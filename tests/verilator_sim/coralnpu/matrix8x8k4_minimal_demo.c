/* 8x8x4 tile demo: SET_C + MAC.
 * A is 8x4, B is 4x8. Choose B so that C[:,0..3] == A and C[:,4..7] == 0.
 */

#include <stdint.h>

#include "coralnpu_matrix_insn.h"

volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

enum {
  kABase = 0x14000u,
  kBBase = 0x15000u,
  kCBase = 0x16000u,
};

static uint32_t fail_code_for_idx(int idx) {
  /* odd codes */
  return (uint32_t)(3u + (uint32_t)(idx * 2u));
}

int main(void) {
  /* A: 8x4 (row-major), fill with 1..32 */
  volatile int8_t* pa = (volatile int8_t*)(uintptr_t)kABase;
  for (int i = 0; i < 32; i++) pa[i] = (int8_t)(i + 1);

  /* B: 4x8 (row-major). First 4 columns form I4, last 4 columns all zero. */
  volatile int8_t* pb = (volatile int8_t*)(uintptr_t)kBBase;
  for (int i = 0; i < 32; i++) pb[i] = 0;
  for (int k = 0; k < 4; k++) {
    pb[k * 8 + k] = 1;
  }

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

  /* Verify C is 8x8 int32, row-major. */
  volatile const uint32_t* pc = (volatile const uint32_t*)(uintptr_t)kCBase;
  for (int i = 0; i < 8; i++) {
    for (int j = 0; j < 8; j++) {
      uint32_t got = pc[i * 8 + j];
      uint32_t exp;
      if (j < 4) {
        exp = (uint32_t)(pa[i * 4 + j]);
      } else {
        exp = 0;
      }
      if (got != exp) {
        tohost = fail_code_for_idx(i * 8 + j);
        return 0;
      }
    }
  }

  tohost = 1u;
  return 0;
}

