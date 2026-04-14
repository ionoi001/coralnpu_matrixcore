// RVV ALU peak ¡ª same bench_result layout as rvv_matmul_32x16_16x32.cc (iters=1).
// Kernel is in-register vector work only: no vle/vse in the timed inner loop.
// mcycle in bench_result counts ONLY that inner for-loop (see rdcycle64 pair).
//
// bench_result / peak_sink live in .extdata (EXTMEM @ 0x20000000) so cocotb
// CoreMiniAxiInterface.read() uses the Python memory mirror; DTCM-only .data is
// not reliably readable via the AXI slave path (SLVERR) while polling magic.

#include <riscv_vector.h>
#include <stdint.h>

uint32_t mcontext0_write_value;

struct BenchResult {
  uint32_t iters;
  uint32_t cycles_lo;
  uint32_t cycles_hi;
  uint32_t magic;
};

volatile BenchResult bench_result __attribute__((section(".extdata")))
__attribute__((aligned(16)));

volatile int32_t peak_sink __attribute__((section(".extdata")));

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

// ~100 timed vector ops: 13 * 8 vadd.vv (small for fast cocotb; still reports ops/cycle).
static constexpr uint32_t kPeakLoopIters = 13u;
static constexpr uint32_t kVaddPerIter = 8u;

// Timed region = inner `for` only (teardown after t1 is not counted).
__attribute__((noinline)) static uint64_t PeakVaddKernelInnerCycles(void) {
  const size_t vl = __riscv_vsetvlmax_e32m1();
  const vint32m1_t c = __riscv_vmv_v_x_i32m1(0x100, vl);

  vint32m1_t x0 = __riscv_vmv_v_x_i32m1(1, vl);
  vint32m1_t x1 = __riscv_vmv_v_x_i32m1(3, vl);
  vint32m1_t x2 = __riscv_vmv_v_x_i32m1(5, vl);
  vint32m1_t x3 = __riscv_vmv_v_x_i32m1(7, vl);
  vint32m1_t x4 = __riscv_vmv_v_x_i32m1(11, vl);
  vint32m1_t x5 = __riscv_vmv_v_x_i32m1(13, vl);
  vint32m1_t x6 = __riscv_vmv_v_x_i32m1(17, vl);
  vint32m1_t x7 = __riscv_vmv_v_x_i32m1(19, vl);

  const uint64_t t0 = rdcycle64();
  for (uint32_t n = 0; n < kPeakLoopIters; ++n) {
    x0 = __riscv_vadd_vv_i32m1(x0, c, vl);
    x1 = __riscv_vadd_vv_i32m1(x1, c, vl);
    x2 = __riscv_vadd_vv_i32m1(x2, c, vl);
    x3 = __riscv_vadd_vv_i32m1(x3, c, vl);
    x4 = __riscv_vadd_vv_i32m1(x4, c, vl);
    x5 = __riscv_vadd_vv_i32m1(x5, c, vl);
    x6 = __riscv_vadd_vv_i32m1(x6, c, vl);
    x7 = __riscv_vadd_vv_i32m1(x7, c, vl);
  }
  const uint64_t t1 = rdcycle64();

  vint32m1_t acc = __riscv_vadd_vv_i32m1(x0, x1, vl);
  acc = __riscv_vadd_vv_i32m1(acc, x2, vl);
  acc = __riscv_vadd_vv_i32m1(acc, x3, vl);
  acc = __riscv_vadd_vv_i32m1(acc, x4, vl);
  acc = __riscv_vadd_vv_i32m1(acc, x5, vl);
  acc = __riscv_vadd_vv_i32m1(acc, x6, vl);
  acc = __riscv_vadd_vv_i32m1(acc, x7, vl);

  const int32_t s = __riscv_vmv_x_s_i32m1_i32(acc);
  peak_sink = s;

  return t1 - t0;
}

extern "C" int main(void) {
  bench_result.iters = 1;
  bench_result.cycles_lo = 0;
  bench_result.cycles_hi = 0;
  bench_result.magic = 0;

  mcontext0_write_value = 0x01;
  asm volatile("csrw 0x7C0, %0" : : "r"(mcontext0_write_value));

  const uint64_t cycles = PeakVaddKernelInnerCycles();

  mcontext0_write_value = 0x00;
  asm volatile("csrw 0x7C0, %0" : : "r"(mcontext0_write_value));

  const uint64_t c = cycles;
  bench_result.cycles_lo = (uint32_t)(c & 0xffffffffu);
  bench_result.cycles_hi = (uint32_t)(c >> 32);
  bench_result.magic = 0x56414444u;  // 'VADD'

  return 0;
}
