/* Minimal bare-metal demo: SET_C + MAC + MAC_ACC (accumulate semantics). */

#include <stdint.h>

#include "coralnpu_matrix_insn.h"

volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

enum {
  kABase = 0x14000u,
  kBBase = 0x15000u,
  kCBase = 0x16000u,
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
  /* Odd values to match legacy tohost halt rule. */
  static const uint32_t codes[4] = {3u, 5u, 7u, 9u};
  return codes[idx & 3];
}

int main(void) {
  fill_buffers();

  __asm__ volatile(
      "lui a0, 0x14\n"
      "lui a1, 0x15\n"
      "lui a2, 0x16\n"
      :
      :
      : "a0", "a1", "a2", "memory");

  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_set_c(12));
  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_mac(10, 11));
  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_mac_acc(10, 11));

  __asm__ volatile("fence iorw, iorw" ::: "memory");

  volatile const uint32_t* pc = (volatile const uint32_t*)(uintptr_t)kCBase;
  uint32_t c[4] = {pc[0], pc[1], pc[2], pc[3]};
  const uint32_t exp[4] = {100u, 120u, 228u, 280u};
  for (int i = 0; i < 4; i++) {
    if (c[i] != exp[i]) {
      tohost = fail_code_for_idx(i);
      return 0;
    }
  }
  tohost = 1u;
  return 0;
}

