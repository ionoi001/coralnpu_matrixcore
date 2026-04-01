/* Intentional failure demo: produces a non-1 tohost code (still odd, so TB halts). */

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

  __asm__ volatile("fence iorw, iorw" ::: "memory");

  /* Deliberately claim failure even if results are correct. */
  tohost = 0x19u; /* odd != 1 */
  return 0;
}

