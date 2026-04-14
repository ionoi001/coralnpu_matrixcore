/* 8x8x4 single-tile correctness (4.2.1): full program path only (no A/B fill).
 * Cocotb writes A @ 0x14000, B @ 0x15000, zeros C @ 0x16000 before execute.
 *
 * IMPORTANT: bind base-register setup + custom instructions into ONE asm block,
 * so the compiler can't reorder/allocate away x10/x11/x12 dependencies.
 */

#include <stdint.h>

#include "coralnpu_matrix_insn.h"

volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

int main(void) {
  __asm__ volatile(
      ".word 0x0000100f\n" /* fence.i */
      "li t0, 1\n"
      "csrw 0x7C0, t0\n"
      "lui a0, 0x14\n"
      "lui a1, 0x15\n"
      "lui a2, 0x16\n"
      ".word %0\n" /* SET_C */
      ".word %1\n" /* MAC */
      ".word 0x0000100f\n" /* fence.i (Decode treats as fencey; wait for LSU/matrix to drain) */
      "li t0, 0\n"
      "csrw 0x7C0, t0\n"
      :
      : "i"((uint32_t)coralnpu_matrix_encode_set_c(12)),
        "i"((uint32_t)coralnpu_matrix_encode_mac(10, 11))
      : "a0", "a1", "a2", "t0", "memory");

  tohost = 1u;
  return 0;
}