// Local copy for cocotb matrix ml_ops binaries.
//
// Keep this in sync with:
// - tests/verilator_sim/coralnpu/coralnpu_matrix_insn.h
//
/* Copyright 2026 Google LLC
 *
 * CoralNPU matrix custom instructions ¡ª software encoding only (no compiler builtin).
 *
 * Encoding summary (opcode custom-2 = 0x5B):
 *   SET_C   : rd=0, funct3=000, rs1=C base reg, rs2 ignored
 *   MAC     : rd=0, funct3=001, rs1=A base, rs2=B base
 *   MAC_ACC : rd=0, funct3=010, rs1=A base, rs2=B base
 */

#ifndef TESTS_COCOTB_MATRIX_ML_OPS_CORALNPU_MATRIX_INSN_H_
#define TESTS_COCOTB_MATRIX_ML_OPS_CORALNPU_MATRIX_INSN_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/** `rs` is 0..31 (x0..x31). */
static inline uint32_t coralnpu_matrix_encode_set_c(unsigned rs1) {
  return (uint32_t)((rs1 & 31u) << 15) | (0u << 7) | 0x5bu;
}

static inline uint32_t coralnpu_matrix_encode_mac(unsigned rs1, unsigned rs2) {
  return (uint32_t)(((rs2 & 31u) << 20) | ((rs1 & 31u) << 15) | (1u << 12) |
                    (0u << 7) | 0x5bu);
}

static inline uint32_t coralnpu_matrix_encode_mac_acc(unsigned rs1, unsigned rs2) {
  return (uint32_t)(((rs2 & 31u) << 20) | ((rs1 & 31u) << 15) | (2u << 12) |
                    (0u << 7) | 0x5bu);
}

/** Emit one 32-bit instruction word (assembler never needs to know the mnemonic). */
#define CORALNPU_MATRIX_EMIT_WORD(WORD) \
  __asm__ volatile(".word %0" : : "i"((uint32_t)(WORD)))

#ifdef __cplusplus
}
#endif

#endif  // TESTS_COCOTB_MATRIX_ML_OPS_CORALNPU_MATRIX_INSN_H_

