// RVV vmul peak — same bench_result layout as rvv_matmul_32x16_16x32.cc (iters=1).
// Inner loop is only vmul.vv (e32m1); mcycle in bench_result is inner-loop only.
// bench_result / peak_sink in .extdata — see rvv_alu_peak_vadd_bench.cc comment.

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

volatile uint32_t peak_sink __attribute__((section(".extdata")));

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

// ~100 timed vector ops: 13 * 8 vmul.vv (small for fast cocotb; still reports ops/cycle).
static constexpr uint32_t kPeakLoopIters = 13u;
static constexpr uint32_t kVmulPerIter = 8u;

__attribute__((noinline)) static uint64_t PeakVmulKernelInnerCycles(void) {
  // GCC 15 coralnpu toolchain exposes vsetvlmax as e32*, not u32*; vl is identical.
  const size_t vl = __riscv_vsetvlmax_e32m1();
  const vuint32m1_t b = __riscv_vmv_v_x_u32m1(3u, vl);

  vuint32m1_t a0 = __riscv_vmv_v_x_u32m1(0x11111111u, vl);
  vuint32m1_t a1 = __riscv_vmv_v_x_u32m1(0x22222222u, vl);
  vuint32m1_t a2 = __riscv_vmv_v_x_u32m1(0x33333333u, vl);
  vuint32m1_t a3 = __riscv_vmv_v_x_u32m1(0x44444444u, vl);
  vuint32m1_t a4 = __riscv_vmv_v_x_u32m1(0x55555555u, vl);
  vuint32m1_t a5 = __riscv_vmv_v_x_u32m1(0x66666666u, vl);
  vuint32m1_t a6 = __riscv_vmv_v_x_u32m1(0x77777777u, vl);
  vuint32m1_t a7 = __riscv_vmv_v_x_u32m1(0x88888888u, vl);

  const uint64_t t0 = rdcycle64();
  for (uint32_t n = 0; n < kPeakLoopIters; ++n) {
    a0 = __riscv_vmul_vv_u32m1(a0, b, vl);
    a1 = __riscv_vmul_vv_u32m1(a1, b, vl);
    a2 = __riscv_vmul_vv_u32m1(a2, b, vl);
    a3 = __riscv_vmul_vv_u32m1(a3, b, vl);
    a4 = __riscv_vmul_vv_u32m1(a4, b, vl);
    a5 = __riscv_vmul_vv_u32m1(a5, b, vl);
    a6 = __riscv_vmul_vv_u32m1(a6, b, vl);
    a7 = __riscv_vmul_vv_u32m1(a7, b, vl);
  }
  const uint64_t t1 = rdcycle64();

  vuint32m1_t acc = __riscv_vadd_vv_u32m1(a0, a1, vl);
  acc = __riscv_vadd_vv_u32m1(acc, a2, vl);
  acc = __riscv_vadd_vv_u32m1(acc, a3, vl);
  acc = __riscv_vadd_vv_u32m1(acc, a4, vl);
  acc = __riscv_vadd_vv_u32m1(acc, a5, vl);
  acc = __riscv_vadd_vv_u32m1(acc, a6, vl);
  acc = __riscv_vadd_vv_u32m1(acc, a7, vl);

  // Single scalar sink (like vadd peak) — no vector store; minimizes post-loop LSU.
  // vreinterpret_v_i32m1_u32m1 is vint->vuint; we need vuint->vint for vmv.x.s.
  const int32_t s = __riscv_vmv_x_s_i32m1_i32(
      __riscv_vreinterpret_v_u32m1_i32m1(acc));
  peak_sink = static_cast<uint32_t>(s);

  return t1 - t0;
}

extern "C" int main(void) {
  bench_result.iters = 1;
  bench_result.cycles_lo = 0;
  bench_result.cycles_hi = 0;
  bench_result.magic = 0;

  mcontext0_write_value = 0x01;
  asm volatile("csrw 0x7C0, %0" : : "r"(mcontext0_write_value));

  const uint64_t cycles = PeakVmulKernelInnerCycles();

  mcontext0_write_value = 0x00;
  asm volatile("csrw 0x7C0, %0" : : "r"(mcontext0_write_value));

  const uint64_t c = cycles;
  bench_result.cycles_lo = (uint32_t)(c & 0xffffffffu);
  bench_result.cycles_hi = (uint32_t)(c >> 32);
  bench_result.magic = 0x564D554Cu;  // 'VMUL'

  return 0;
}
