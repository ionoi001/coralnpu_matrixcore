/* Minimal bare-metal demo: same as matrix_minimal_demo but with different A/B/C bases. */

#include <stdint.h>

#include "coralnpu_matrix_insn.h"

volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

enum {
  // Keep within default DTCM: [0x10000, 0x18000).
  kABase = 0x13000u,
  kBBase = 0x14000u,
  kCBase = 0x17000u,
};

static void fill_buffers(void) {
  static const uint8_t a[8] = {1, 2, 3, 4, 5, 6, 7, 8};
  static const uint8_t b[8] = {1, 2, 3, 4, 5, 6, 7, 8};
  volatile uint8_t* pa = (volatile uint8_t*)(uintptr_t)kABase;
  volatile uint8_t* pb = (volatile uint8_t*)(uintptr_t)kBBase;
  for (int i = 0; i < 8; i++) {
    pa[i] = a[i];
    pb[i] = b[i];
  }
}

static uint32_t fail_code_for_idx(int idx) {
  static const uint32_t codes[4] = {3u, 5u, 7u, 9u};
  return codes[idx & 3];
}

int main(void) {
  fill_buffers();

  /* Top 20 bits for the chosen bases: 0x13/0x14/0x17. */
  __asm__ volatile(
      "lui a0, 0x13\n"
      "lui a1, 0x14\n"
      "lui a2, 0x17\n"
      :
      :
      : "a0", "a1", "a2", "memory");

  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_set_c(12));
  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_mac(10, 11));

  __asm__ volatile("fence iorw, iorw" ::: "memory");

  volatile const uint32_t* pc = (volatile const uint32_t*)(uintptr_t)kCBase;
  uint32_t c[4] = {pc[0], pc[1], pc[2], pc[3]};
  const uint32_t exp[4] = {50u, 60u, 114u, 140u};
  for (int i = 0; i < 4; i++) {
    if (c[i] != exp[i]) {
      tohost = fail_code_for_idx(i);
      return 0;
    }
  }
  tohost = 1u;
  return 0;
}

