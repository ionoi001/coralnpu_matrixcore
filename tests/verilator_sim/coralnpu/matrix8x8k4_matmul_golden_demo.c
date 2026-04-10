/* 8x8x4 single-tile matmul: same A/B fill as matrix8x8k4_bench_demo (CPU stores to DTCM);
 * zeros C, SET_C + one MAC, bench_result + tohost. Cocotb matches A/B pattern in NumPy ref.
 */

#include <stdint.h>

#include "coralnpu_matrix_insn.h"

volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

enum {
  kABase = 0x14000u,
  kBBase = 0x15000u,
  kCBase = 0x16000u,
};

typedef struct {
  uint32_t iters;
  uint32_t cycles_lo;
  uint32_t cycles_hi;
  uint32_t magic;
} BenchResult;

volatile BenchResult bench_result __attribute__((section(".data"))) __attribute__((aligned(16)));

static inline uint64_t read_mcycle64(void) {
  uint32_t hi0, lo, hi1;
  do {
    __asm__ volatile("csrr %0, mcycleh" : "=r"(hi0));
    __asm__ volatile("csrr %0, mcycle" : "=r"(lo));
    __asm__ volatile("csrr %0, mcycleh" : "=r"(hi1));
  } while (hi0 != hi1);
  return ((uint64_t)hi1 << 32) | lo;
}

int main(void) {
  volatile int32_t* pc = (volatile int32_t*)(uintptr_t)kCBase;
  for (int i = 0; i < 8 * 8; i++) {
    pc[i] = 0;
  }

  volatile int8_t* pa = (volatile int8_t*)(uintptr_t)kABase;
  for (int i = 0; i < 32; i++) pa[i] = (int8_t)(i + 1);

  volatile int8_t* pb = (volatile int8_t*)(uintptr_t)kBBase;
  for (int i = 0; i < 32; i++) pb[i] = (int8_t)((i % 7) - 3);

  /* Scalar stores must retire to DTCM before the matrix engine DMA reads A/B.
   * A plain `fence` is not treated as fency in Decode; FENCE.I is, and dispatch
   * waits until lsu.io.active is clear (see SCoreMatrixHarnessTest FENCE.I cases). */
  __asm__ volatile(".word 0x0000100f"); /* fence.i */

  __asm__ volatile(
      "lui a0, 0x14\n"
      "lui a1, 0x15\n"
      "lui a2, 0x16\n"
      :
      :
      : "a0", "a1", "a2", "memory");

  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_set_c(12));

  bench_result.iters = 1;
  bench_result.cycles_lo = 0;
  bench_result.cycles_hi = 0;
  bench_result.magic = 0;
  tohost = 0;

  uint64_t t0 = read_mcycle64();
  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_mac(10, 11));
  __asm__ volatile("fence iorw, iorw" ::: "memory");
  uint64_t t1 = read_mcycle64();

  uint64_t cycles = t1 - t0;
  bench_result.cycles_lo = (uint32_t)(cycles & 0xffffffffu);
  bench_result.cycles_hi = (uint32_t)(cycles >> 32);
  bench_result.magic = 0x4d4c4f50u; /* 'MLOP' */

  __asm__ volatile("fence iorw, iorw" ::: "memory");
  tohost = 1u;
  return 0;
}
