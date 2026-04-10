#include <stdint.h>
#include <cstddef>

constexpr size_t kLhsRows = 16;
constexpr size_t kRhsCols = 16;
constexpr size_t kInner = 48;

volatile uint32_t tohost __attribute__((section(".htif"), aligned(64))) = 0;

// mcontext0 val used in test for power period extraction
// mcontext0 is io_coralnpu_csr_value_8 in waveform
uint32_t mcontext0_write_value;

struct BenchResult {
  uint32_t iters;
  uint32_t cycles_lo;
  uint32_t cycles_hi;
  uint32_t magic;
};

volatile BenchResult bench_result __attribute__((section(".data")))
__attribute__((aligned(16)));

static inline uint64_t rdcycle64() {
  uint32_t hi0, lo, hi1;
  asm volatile(
      "1:\n"
      "csrr %0, mcycleh\n"
      "csrr %1, mcycle\n"
      "csrr %2, mcycleh\n"
      "bne  %0, %2, 1b\n"
      : "=&r"(hi0), "=&r"(lo), "=&r"(hi1));
  return (uint64_t(hi1) << 32) | lo;
}

int8_t lhs_input[kLhsRows * kInner] __attribute__((section(".extdata")))
__attribute__((aligned(16)));
int8_t rhs_input[kInner * kRhsCols] __attribute__((section(".extdata")))
__attribute__((aligned(16)));
int32_t result_output[kLhsRows * kRhsCols] __attribute__((section(".extdata")))
__attribute__((aligned(16)));

// Assume rhs is KxN row-major (contiguous rows of N), matching matrix bench.
static void MatMul(size_t lhs_rows, size_t inner, size_t rhs_cols,
                   const int8_t* lhs, const int8_t* rhs, int32_t* result) {
  for (size_t r = 0; r < lhs_rows; r++) {
    const int8_t* lhs_data = lhs + (r * inner);
    int32_t* result_row = result + (r * rhs_cols);
    for (size_t c = 0; c < rhs_cols; c++) {
      int32_t acc = 0;
      for (size_t k = 0; k < inner; k++) {
        const int8_t rhs_val = rhs[k * rhs_cols + c];
        acc += (int32_t)lhs_data[k] * (int32_t)rhs_val;
      }
      result_row[c] = acc;
    }
  }
}

int main() {
  bench_result.iters = 1;
  bench_result.cycles_lo = 0;
  bench_result.cycles_hi = 0;
  bench_result.magic = 0;
  tohost = 0;

  mcontext0_write_value = 0x01;
  asm volatile("csrw 0x7C0, %0" : : "r"(mcontext0_write_value));
  uint64_t t0 = rdcycle64();
  MatMul(kLhsRows, kInner, kRhsCols, lhs_input, rhs_input, result_output);
  uint64_t t1 = rdcycle64();
  mcontext0_write_value = 0x00;
  asm volatile("csrw 0x7C0, %0" : : "r"(mcontext0_write_value));

  uint64_t cycles = t1 - t0;
  bench_result.cycles_lo = (uint32_t)(cycles & 0xffffffffu);
  bench_result.cycles_hi = (uint32_t)(cycles >> 32);
  bench_result.magic = 0x4D4C4F50;  // 'MLOP'
  asm volatile("fence iorw, iorw" ::: "memory");
  tohost = 1;
  return 0;
}

