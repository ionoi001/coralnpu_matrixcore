/* Minimal bare-metal demo: SET_C + MAC; data matches SCoreMatrixHarnessTest Case1 math. */

#include <stdint.h>

#include "coralnpu_matrix_insn.h"

/* HTIF exit for Verilator TB (legacy path: direct write halts sim when value LSB is 1). */
volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

/* DTCM starts at 0x10000; .htif/tohost is placed first ¡ª keep A/B/C past linker data. */
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

  /* x10/x11/x12 = A/B/C bases: lui imm is top 20 bits (0x14<<12 etc.). */
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

  /* Expected int32 tile from Case1 (2x2, K=4 MAC). */
  volatile const uint32_t* pc = (volatile const uint32_t*)(uintptr_t)kCBase;
  uint32_t c0 = pc[0];
  uint32_t c1 = pc[1];
  uint32_t c2 = pc[2];
  uint32_t c3 = pc[3];

  if (c0 == 50u && c1 == 60u && c2 == 114u && c3 == 140u) {
    tohost = 1u;
  } else {
    tohost = 0xbadu;
  }
  return 0;
}
