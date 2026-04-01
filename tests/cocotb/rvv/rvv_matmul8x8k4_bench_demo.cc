// RVV end-to-end benchmark for 8x8x4 GEMM tile (matches matrix8x8k4 MAC count).
// Uses mcycle/mcycleh for cycle measurement and writes results to DTCM mailbox.

#include <riscv_vector.h>
#include <stdint.h>

volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

enum : uint32_t {
  kABase = 0x14000u,      // int8 A: 8x4 (32 bytes)
  kBBase = 0x15000u,      // int8 B: 4x8 (32 bytes)
  kCBase = 0x16000u,      // int32 C: 8x8 (256 bytes)
  kResultBase = 0x10100u, // mailbox: iters + cycles + magic
};

static inline uint32_t read_csr(const uint32_t csr) {
  uint32_t v;
  asm volatile("csrr %0, %1" : "=r"(v) : "i"(csr));
  return v;
}

static inline uint64_t read_mcycle64() {
  uint32_t hi0, lo, hi1;
  do {
    hi0 = read_csr(0xB80);  // mcycleh
    lo = read_csr(0xB00);   // mcycle
    hi1 = read_csr(0xB80);  // mcycleh
  } while (hi0 != hi1);
  return (static_cast<uint64_t>(hi1) << 32) | lo;
}

static inline void gemm8x8k4_rvv(const int8_t* a, const int8_t* b, int32_t* c) {
  // We compute C[8x8] = A[8x4] * B[4x8], int8 inputs, int32 accumulate.
  // Layouts are row-major.
  const size_t vl = __riscv_vsetvl_e32m4(8);
  for (int i = 0; i < 8; i++) {
    vint32m4_t acc = __riscv_vmv_v_x_i32m4(0, vl);
    const int8_t* arow = a + i * 4;
    for (int k = 0; k < 4; k++) {
      const int8_t* brow = b + k * 8;
      vint8m1_t w8 = __riscv_vle8_v_i8m1(brow, vl);
      // Widen int8 -> int16
      vint16m2_t w16 = __riscv_vwadd_vx_i16m2(w8, 0, vl);
      int16_t aval = static_cast<int16_t>(arow[k]);
      acc = __riscv_vwmacc_vx_i32m4(acc, aval, w16, vl);
    }
    __riscv_vse32_v_i32m4(c + i * 8, acc, vl);
  }
}

int main() {
  // Initialize inputs in DTCM (same sizes as matrix test).
  volatile int8_t* pa = reinterpret_cast<volatile int8_t*>(kABase);
  volatile int8_t* pb = reinterpret_cast<volatile int8_t*>(kBBase);
  for (int i = 0; i < 32; i++) pa[i] = static_cast<int8_t>(i + 1);
  for (int i = 0; i < 32; i++) pb[i] = static_cast<int8_t>((i % 7) - 3);

  volatile uint32_t* cfg = reinterpret_cast<volatile uint32_t*>(kResultBase);
  uint32_t iters = cfg[0];
  if (iters == 0) iters = 500;

  uint64_t start = read_mcycle64();
  for (uint32_t t = 0; t < iters; t++) {
    gemm8x8k4_rvv(reinterpret_cast<const int8_t*>(kABase),
                  reinterpret_cast<const int8_t*>(kBBase),
                  reinterpret_cast<int32_t*>(kCBase));
  }
  uint64_t end = read_mcycle64();

  asm volatile("fence iorw, iorw" ::: "memory");

  uint64_t cycles = end - start;
  cfg[0] = iters;
  cfg[1] = static_cast<uint32_t>(cycles & 0xffffffffu);
  cfg[2] = static_cast<uint32_t>(cycles >> 32);
  cfg[3] = 0x4D415458u;  // 'MATX'

  tohost = 1u;
  return 0;
}

