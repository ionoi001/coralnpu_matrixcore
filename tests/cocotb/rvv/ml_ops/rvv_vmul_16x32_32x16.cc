#include <riscv_vector.h>
#include <stdint.h>

// VMul: elementwise multiply of LHS row and RHS column vectors (length K).
//
// To keep simulation fast, we do NOT store the full 16x16x32 int16 tensor.
// Instead we compute a single checksum by xoring lane0 of each (row,col) product
// vector, then write that checksum to EXTMEM.
//
// LHS: [kLhsRows, kInner] row-major (int8)
// RHS: [kInner, kRhsCols] but stored column-major in memory (like matmul tests),
//      i.e. rhs_input is rhs_data.transpose().flatten() from cocotb.
// OUT: vmul_checksum (uint32)
constexpr size_t kLhsRows = 16;
constexpr size_t kRhsCols = 16;
constexpr size_t kInner = 32;

uint32_t mcontext0_write_value;

struct BenchResult {
  uint32_t iters;
  uint32_t cycles_lo;
  uint32_t cycles_hi;
  uint32_t magic;
};

// Place mailboxes & large buffers in EXTMEM so cocotb can read them via AXI
// mirror and we don't overflow small DTCM configs.
volatile BenchResult bench_result __attribute__((section(".extdata")))
    __attribute__((aligned(16)));

volatile uint32_t vmul_checksum __attribute__((section(".extdata")))
    __attribute__((aligned(16)));

static inline uint64_t rdcycle64() {
  uint32_t hi0, lo, hi1;
  asm volatile(
      "1:\n"
      "csrr %0, mcycleh\n"
      "csrr %1, mcycle\n"
      "csrr %2, mcycleh\n"
      "bne  %0, %2, 1b\n"
      : "=&r"(hi0), "=&r"(lo), "=&r"(hi1));
  return (uint64_t(hi1) << 32) | lo;
}

// 64B alignment helps unit-stride bursts; sizes are multiples of 32/64.
int8_t lhs_input[kLhsRows * kInner] __attribute__((section(".extdata")))
    __attribute__((aligned(64)));
int8_t rhs_input[kInner * kRhsCols] __attribute__((section(".extdata")))
    __attribute__((aligned(64)));

// RHS is column-major: rhs points to [rhs_cols][inner] contiguous columns.
static uint32_t VMulChecksum(size_t lhs_rows, size_t inner, size_t rhs_cols,
                             const int8_t* lhs, const int8_t* rhs) {
  const size_t vlenb = __riscv_vlenb();
  uint32_t chk = 0;

  // Fast path: inner==32 and vlenb>=16 => can use e8m2 (vl=32) and vwmul -> e16m4.
  if (inner == 32 && vlenb >= 16) {
    const size_t vl = 32;
    for (size_t r = 0; r < lhs_rows; ++r) {
      const int8_t* lhs_row = lhs + r * inner;
      const vint8m2_t vlhs = __riscv_vle8_v_i8m2(lhs_row, vl);

      for (size_t c = 0; c < rhs_cols; ++c) {
        const int8_t* rhs_col = rhs + c * inner;
        const vint8m2_t vrhs = __riscv_vle8_v_i8m2(rhs_col, vl);
        const vint16m4_t vmul16 = __riscv_vwmul_vv_i16m4(vlhs, vrhs, vl);
        // Fold just lane0 into checksum to avoid huge memory traffic.
        const int16_t lane0 = __riscv_vmv_x_s_i16m4_i16(vmul16);
        chk ^= (uint32_t)(uint16_t)lane0;
      }
    }
    return chk;
  }

  // Generic strip-mined path (supports any inner).
  for (size_t r = 0; r < lhs_rows; ++r) {
    const int8_t* lhs_row = lhs + r * inner;
    for (size_t c = 0; c < rhs_cols; ++c) {
      const int8_t* rhs_col = rhs + c * inner;

      size_t k = 0;
      while (k < inner) {
        // Use as many e8 elements as possible (vl in elements).
        size_t vl = __riscv_vsetvl_e8m1(inner - k);
        vint8m1_t vlhs = __riscv_vle8_v_i8m1(lhs_row + k, vl);
        vint8m1_t vrhs = __riscv_vle8_v_i8m1(rhs_col + k, vl);
        vint16m2_t vmul16 = __riscv_vwmul_vv_i16m2(vlhs, vrhs, vl);
        // For k==0, fold lane0 once; other strips don't contribute to lane0.
        if (k == 0) {
          const int16_t lane0 = __riscv_vmv_x_s_i16m2_i16(vmul16);
          chk ^= (uint32_t)(uint16_t)lane0;
        }
        k += vl;
      }
    }
  }
  return chk;
}

int main() {
  bench_result.iters = 1;
  bench_result.cycles_lo = 0;
  bench_result.cycles_hi = 0;
  bench_result.magic = 0;
  vmul_checksum = 0;

  mcontext0_write_value = 0x01;
  asm volatile("csrw 0x7C0, %0" : : "r"(mcontext0_write_value));
  uint64_t t0 = rdcycle64();
  const uint32_t chk = VMulChecksum(kLhsRows, kInner, kRhsCols, lhs_input, rhs_input);
  uint64_t t1 = rdcycle64();
  mcontext0_write_value = 0x00;
  asm volatile("csrw 0x7C0, %0" : : "r"(mcontext0_write_value));

  vmul_checksum = chk;
  const uint64_t cycles = t1 - t0;
  bench_result.cycles_lo = (uint32_t)(cycles & 0xffffffffu);
  bench_result.cycles_hi = (uint32_t)(cycles >> 32);
  bench_result.magic = 0x564D554Cu;  // 'VMUL'
  return 0;
}

