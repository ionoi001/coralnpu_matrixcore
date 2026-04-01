/* 8x8x4 end-to-end benchmark (includes memory read/write):
 * - Reads mcycle/mcycleh around a loop of MAC instructions
 * - Writes results to DTCM so cocotb can compute MAC/cycle
 */

#include <stdint.h>

#include "coralnpu_matrix_insn.h"

volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

enum {
  kABase = 0x14000u,
  kBBase = 0x15000u,
  kCBase = 0x16000u,

  kResultBase = 0x10100u,
};

static inline uint32_t read_csr(const uint32_t csr) {
  uint32_t v;
  __asm__ volatile("csrr %0, %1" : "=r"(v) : "i"(csr));
  return v;
}

static inline uint64_t read_mcycle64(void) {
  uint32_t hi0, lo, hi1;
  do {
    hi0 = read_csr(0xB80); /* mcycleh */
    lo = read_csr(0xB00);  /* mcycle */
    hi1 = read_csr(0xB80);
  } while (hi0 != hi1);
  return ((uint64_t)hi1 << 32) | lo;
}

int main(void) {
  /* A: 8x4 (row-major), fill 1..32 */
  volatile int8_t* pa = (volatile int8_t*)(uintptr_t)kABase;
  for (int i = 0; i < 32; i++) pa[i] = (int8_t)(i + 1);

  /* B: 4x8 (row-major), fill with a simple pattern. */
  volatile int8_t* pb = (volatile int8_t*)(uintptr_t)kBBase;
  for (int i = 0; i < 32; i++) pb[i] = (int8_t)((i % 7) - 3);

  __asm__ volatile(
      "lui a0, 0x14\n"
      "lui a1, 0x15\n"
      "lui a2, 0x16\n"
      :
      :
      : "a0", "a1", "a2", "memory");

  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_set_c(12));

  volatile uint32_t* cfg = (volatile uint32_t*)(uintptr_t)kResultBase;
  uint32_t iters = cfg[0];
  if (iters == 0) iters = 1000;

  uint64_t start = read_mcycle64();
  for (uint32_t i = 0; i < iters; i++) {
    CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_mac(10, 11));
  }
  uint64_t end = read_mcycle64();

  __asm__ volatile("fence iorw, iorw" ::: "memory");

  volatile uint32_t* out = (volatile uint32_t*)(uintptr_t)kResultBase;
  uint64_t cycles = end - start;
  out[0] = iters;
  out[1] = (uint32_t)(cycles & 0xffffffffu);
  out[2] = (uint32_t)(cycles >> 32);
  out[3] = 0x4d415458u; /* 'MATX' */

  tohost = 1u;
  return 0;
}

