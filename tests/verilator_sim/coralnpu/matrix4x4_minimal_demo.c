/* 4x4x4 tile demo: SET_C + MAC, with B=I so C == A. */

#include <stdint.h>

#include "coralnpu_matrix_insn.h"

volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

enum {
  kABase = 0x14000u,
  kBBase = 0x15000u,
  kCBase = 0x16000u,
};

static void fill_buffers(void) {
  /* A is 4x4 (row-major): 1..16 */
  volatile int8_t* pa = (volatile int8_t*)(uintptr_t)kABase;
  for (int i = 0; i < 16; i++) pa[i] = (int8_t)(i + 1);

  /* B is 4x4 identity (row-major): diag=1 */
  volatile int8_t* pb = (volatile int8_t*)(uintptr_t)kBBase;
  for (int i = 0; i < 16; i++) pb[i] = 0;
  pb[0] = 1;
  pb[5] = 1;
  pb[10] = 1;
  pb[15] = 1;
}

static uint32_t fail_code_for_idx(int idx) {
  static const uint32_t codes[16] = {
      3u, 5u, 7u, 9u, 11u, 13u, 15u, 17u,
      19u, 21u, 23u, 25u, 27u, 29u, 31u, 33u,
  };
  return codes[idx & 15];
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

  volatile const uint32_t* pc = (volatile const uint32_t*)(uintptr_t)kCBase;
  for (int i = 0; i < 16; i++) {
    uint32_t got = pc[i];
    uint32_t exp = (uint32_t)(i + 1);
    if (got != exp) {
      tohost = fail_code_for_idx(i);
      return 0;
    }
  }
  tohost = 1u;
  return 0;
}

