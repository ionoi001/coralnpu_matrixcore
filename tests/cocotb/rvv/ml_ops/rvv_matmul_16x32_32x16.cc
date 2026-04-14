#include <riscv_vector.h>
#include <stdint.h>

// Matmul: LHS 16x32 * RHS 32x16 -> result 16x16 (int32 accumulators).
constexpr size_t kLhsRows = 16;
constexpr size_t kRhsCols = 16;
constexpr size_t kInner = 32;

// mcontext0 val used in test for power period extraction
// mcontext0 is io_coralnpu_csr_value_8 in waveform
uint32_t mcontext0_write_value;

struct BenchResult {
  uint32_t iters;
  uint32_t cycles_lo;
  uint32_t cycles_hi;
  uint32_t magic;
};

volatile BenchResult bench_result __attribute__((section(".data")))
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

// 64B alignment helps unit-stride bursts; sizes are multiples of 32/64 where possible.
int8_t lhs_input[kLhsRows * kInner] __attribute__((section(".data")))
__attribute__((aligned(64)));
int8_t rhs_input[kInner * kRhsCols] __attribute__((section(".data")))
__attribute__((aligned(64)));
int32_t result_output[kLhsRows * kRhsCols] __attribute__((section(".data")))
__attribute__((aligned(64)));

// One LHS row ¡Á four RHS columns (in regs) -> four i32 dots, row-major stores.
static inline void dot4_row_store_vl32(vint8m2_t vlhs, vint8m2_t vrhs0,
                                       vint8m2_t vrhs1, vint8m2_t vrhs2,
                                       vint8m2_t vrhs3, int32_t* out,
                                       size_t vl) {
  vint16m4_t m0 = __riscv_vwmul_vv_i16m4(vlhs, vrhs0, vl);
  vint16m4_t m1 = __riscv_vwmul_vv_i16m4(vlhs, vrhs1, vl);
  vint16m4_t m2 = __riscv_vwmul_vv_i16m4(vlhs, vrhs2, vl);
  vint16m4_t m3 = __riscv_vwmul_vv_i16m4(vlhs, vrhs3, vl);
  vint32m1_t z0 = __riscv_vmv_v_x_i32m1(0, 1);
  vint32m1_t z1 = __riscv_vmv_v_x_i32m1(0, 1);
  vint32m1_t z2 = __riscv_vmv_v_x_i32m1(0, 1);
  vint32m1_t z3 = __riscv_vmv_v_x_i32m1(0, 1);
  vint32m1_t a0 = __riscv_vwredsum_vs_i16m4_i32m1(m0, z0, vl);
  vint32m1_t a1 = __riscv_vwredsum_vs_i16m4_i32m1(m1, z1, vl);
  vint32m1_t a2 = __riscv_vwredsum_vs_i16m4_i32m1(m2, z2, vl);
  vint32m1_t a3 = __riscv_vwredsum_vs_i16m4_i32m1(m3, z3, vl);
  __riscv_vse32_v_i32m1(out + 0, a0, 1);
  __riscv_vse32_v_i32m1(out + 1, a1, 1);
  __riscv_vse32_v_i32m1(out + 2, a2, 1);
  __riscv_vse32_v_i32m1(out + 3, a3, 1);
}

// inner==32, vlenb>=16 (e8 m2, vl=32). RHS column-major.
//
// - Loop interchange: outer column blocks; each RHS column loaded once/matmul.
// - 2x4 micro-blocks: with 4 RHS columns in registers, process *two* LHS rows
//   per inner step (8 outputs). Amortizes row-loop control vs 1x4; same RHS
//   reuse as 1x4.
//
// Deferred reduction / fewer vwredsum: with K=32 in one m2 strip, each output
// still needs one horizontal reduction; deferring only helps when K is strip-
// mined (future). Packed i32 stores (vl=4): toolchain-dependent vcreate; keep
// vl=1 stores until we standardize on intrinsics that pack four m1 dots.
//
// vsetvli churn: cluster e8 mul -> e16 mul -> e32 red per output; larger
// blocks amortize; asm or explicit vsetvl is the next lever.
static void MatMul_inner32_rhs_reuse(size_t lhs_rows, size_t rhs_cols,
                                     const int8_t* lhs, const int8_t* rhs,
                                     int32_t* result) {
  const size_t vl = 32;
  const size_t inner = 32;

  size_t c = 0;
  for (; c + 4 <= rhs_cols; c += 4) {
    const int8_t* r0 = rhs + (c + 0) * inner;
    const int8_t* r1 = rhs + (c + 1) * inner;
    const int8_t* r2 = rhs + (c + 2) * inner;
    const int8_t* r3 = rhs + (c + 3) * inner;

    vint8m2_t vrhs0 = __riscv_vle8_v_i8m2(r0, vl);
    vint8m2_t vrhs1 = __riscv_vle8_v_i8m2(r1, vl);
    vint8m2_t vrhs2 = __riscv_vle8_v_i8m2(r2, vl);
    vint8m2_t vrhs3 = __riscv_vle8_v_i8m2(r3, vl);

    size_t r = 0;
    for (; r + 1 < lhs_rows; r += 2) {
      const int8_t* lhs_a = lhs + r * inner;
      const int8_t* lhs_b = lhs + (r + 1) * inner;
      int32_t* out_a = result + r * rhs_cols + c;
      int32_t* out_b = result + (r + 1) * rhs_cols + c;

      vint8m2_t vlhs_a = __riscv_vle8_v_i8m2(lhs_a, vl);
      vint8m2_t vlhs_b = __riscv_vle8_v_i8m2(lhs_b, vl);

      dot4_row_store_vl32(vlhs_a, vrhs0, vrhs1, vrhs2, vrhs3, out_a, vl);
      dot4_row_store_vl32(vlhs_b, vrhs0, vrhs1, vrhs2, vrhs3, out_b, vl);
    }
    for (; r < lhs_rows; ++r) {
      const int8_t* lhs_data = lhs + r * inner;
      int32_t* out = result + r * rhs_cols + c;

      vint8m2_t vlhs = __riscv_vle8_v_i8m2(lhs_data, vl);
      dot4_row_store_vl32(vlhs, vrhs0, vrhs1, vrhs2, vrhs3, out, vl);
    }
  }

  for (; c + 2 <= rhs_cols; c += 2) {
    const int8_t* r0 = rhs + (c + 0) * inner;
    const int8_t* r1 = rhs + (c + 1) * inner;

    vint8m2_t vrhs0 = __riscv_vle8_v_i8m2(r0, vl);
    vint8m2_t vrhs1 = __riscv_vle8_v_i8m2(r1, vl);

    for (size_t r = 0; r < lhs_rows; ++r) {
      const int8_t* lhs_data = lhs + r * inner;
      int32_t* out = result + r * rhs_cols + c;

      vint8m2_t vlhs = __riscv_vle8_v_i8m2(lhs_data, vl);

      vint16m4_t vmul0 = __riscv_vwmul_vv_i16m4(vlhs, vrhs0, vl);
      vint16m4_t vmul1 = __riscv_vwmul_vv_i16m4(vlhs, vrhs1, vl);

      vint32m1_t z0 = __riscv_vmv_v_x_i32m1(0, 1);
      vint32m1_t z1 = __riscv_vmv_v_x_i32m1(0, 1);

      vint32m1_t vacc0 = __riscv_vwredsum_vs_i16m4_i32m1(vmul0, z0, vl);
      vint32m1_t vacc1 = __riscv_vwredsum_vs_i16m4_i32m1(vmul1, z1, vl);

      __riscv_vse32_v_i32m1(out + 0, vacc0, 1);
      __riscv_vse32_v_i32m1(out + 1, vacc1, 1);
    }
  }

  for (; c < rhs_cols; ++c) {
    const int8_t* r0 = rhs + c * inner;
    vint8m2_t vrhs0 = __riscv_vle8_v_i8m2(r0, vl);

    for (size_t r = 0; r < lhs_rows; ++r) {
      const int8_t* lhs_data = lhs + r * inner;
      int32_t* out = result + r * rhs_cols + c;

      vint8m2_t vlhs = __riscv_vle8_v_i8m2(lhs_data, vl);
      vint16m4_t vmul0 = __riscv_vwmul_vv_i16m4(vlhs, vrhs0, vl);

      vint32m1_t z0 = __riscv_vmv_v_x_i32m1(0, 1);
      vint32m1_t vacc0 = __riscv_vwredsum_vs_i16m4_i32m1(vmul0, z0, vl);

      __riscv_vse32_v_i32m1(out, vacc0, 1);
    }
  }
}

static void MatMul_16x32x16_fast(const int8_t* lhs, const int8_t* rhs,
                                 int32_t* result) {
  MatMul_inner32_rhs_reuse(kLhsRows, kRhsCols, lhs, rhs, result);
}

// Generic fallback (other dimensions / stripmine).
static void MatMul_generic(size_t lhs_rows, size_t inner, size_t rhs_cols,
                           const int8_t* lhs, const int8_t* rhs, int32_t* result) {
  const size_t vlenb = __riscv_vlenb();

  if (inner == 32 && vlenb >= 16) {
    MatMul_inner32_rhs_reuse(lhs_rows, rhs_cols, lhs, rhs, result);
    return;
  }

  for (size_t r = 0; r < lhs_rows; r++) {
    const int8_t* lhs_data = lhs + (r * inner);
    int32_t* result_row = result + (r * rhs_cols);

    for (size_t c = 0; c < rhs_cols; c++) {
      const int8_t* rhs_data = rhs + (c * inner);
      vint32m1_t vacc = __riscv_vmv_v_x_i32m1(0, 1);
      size_t k = 0;
      size_t vl = vlenb;
      while (k < inner) {
        if (inner - k < vl) {
          vl = inner - k;
        }
        vint8m1_t vlhs_strip = __riscv_vle8_v_i8m1(lhs_data + k, vl);
        vint8m1_t vrhs_strip = __riscv_vle8_v_i8m1(rhs_data + k, vl);
        vint16m2_t vmul_16 = __riscv_vwmul_vv_i16m2(vlhs_strip, vrhs_strip, vl);
        vacc = __riscv_vwredsum_vs_i16m2_i32m1(vmul_16, vacc, vlenb);
        k += vl;
      }
      __riscv_vse32_v_i32m1(result_row + c, vacc, 1);
    }
  }
}

void MatMul(size_t lhs_rows, size_t inner, size_t rhs_cols, const int8_t* lhs,
            const int8_t* rhs, int32_t* result) {
  if (lhs_rows == kLhsRows && inner == kInner && rhs_cols == kRhsCols &&
      __riscv_vlenb() >= 16) {
    MatMul_16x32x16_fast(lhs, rhs, result);
    return;
  }
  MatMul_generic(lhs_rows, inner, rhs_cols, lhs, rhs, result);
}

int main() {
  bench_result.iters = 1;
  bench_result.cycles_lo = 0;
  bench_result.cycles_hi = 0;
  bench_result.magic = 0;

  mcontext0_write_value = 0x01;
  asm volatile("csrw 0x7C0, %0" : : "r"(mcontext0_write_value));
  uint64_t t0 = rdcycle64();
  MatMul(kLhsRows, kInner, kRhsCols, lhs_input, rhs_input, result_output);
  uint64_t t1 = rdcycle64();
  mcontext0_write_value = 0x00;
  asm volatile("csrw 0x7C0, %0" : : "r"(mcontext0_write_value));

  uint64_t cycles = t1 - t0;
  bench_result.cycles_lo = (uint32_t)(cycles & 0xffffffffu);
  bench_result.cycles_hi = (uint32_t)(cycles >> 32);
  bench_result.magic = 0x4D4C4F50;  // 'MLOP'
  return 0;
}
