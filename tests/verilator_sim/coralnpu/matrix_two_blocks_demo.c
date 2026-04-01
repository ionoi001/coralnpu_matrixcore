/* Two independent blocks: SET_C+MAC to C0, then SET_C+MAC to C1. */

#include <stdint.h>

#include "coralnpu_matrix_insn.h"

volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

enum {
  kABase = 0x14000u,
  kBBase = 0x15000u,
  kC0Base = 0x16000u,
  kC1Base = 0x17000u,
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

static uint32_t fail_code(uint32_t which, int idx) {
  /* which: 0 for C0, 1 for C1; idx: 0..3 */
  return (uint32_t)(0x21u + (which * 8u) + (uint32_t)(idx * 2u)); /* all odd */
}

static int check_tile(uint32_t base, uint32_t which) {
  volatile const uint32_t* pc = (volatile const uint32_t*)(uintptr_t)base;
  const uint32_t exp[4] = {50u, 60u, 114u, 140u};
  for (int i = 0; i < 4; i++) {
    if (pc[i] != exp[i]) return i;
  }
  return -1;
}

int main(void) {
  fill_buffers();

  __asm__ volatile(
      "lui a0, 0x14\n"
      "lui a1, 0x15\n"
      :
      :
      : "a0", "a1", "memory");

  /* Block 0: C0 */
  __asm__ volatile("lui a2, 0x16\n" : : : "a2", "memory");
  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_set_c(12));
  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_mac(10, 11));

  /* Block 1: C1 */
  __asm__ volatile("lui a2, 0x17\n" : : : "a2", "memory");
  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_set_c(12));
  CORALNPU_MATRIX_EMIT_WORD(coralnpu_matrix_encode_mac(10, 11));

  __asm__ volatile("fence iorw, iorw" ::: "memory");

  int bad0 = check_tile(kC0Base, 0);
  if (bad0 >= 0) {
    tohost = fail_code(0, bad0);
    return 0;
  }
  int bad1 = check_tile(kC1Base, 1);
  if (bad1 >= 0) {
    tohost = fail_code(1, bad1);
    return 0;
  }

  tohost = 1u;
  return 0;
}

