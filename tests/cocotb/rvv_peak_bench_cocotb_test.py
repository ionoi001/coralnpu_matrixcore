"""RVV peak cocotb: dense in-register kernels; completion via bench_result.magic.

CRT success path uses mpause (.insn 0x08000073) then a tight loop; this Verilator
environment may not drive io_halted=1 there, so we use run_until_bench_magic()
instead of run_to_halt(). Magic is written last in main before return.
"""

import cocotb
import numpy as np

from coralnpu_test_utils.sim_test_fixture import Fixture
from bazel_tools.tools.python.runfiles import runfiles

# Timed vector op count in each peak .cc: kPeakLoopIters * 8 (must match sources).
_TIMED_VECTOR_OPS = 13 * 8

_MAGIC_VADD = 0x56414444  # 'VADD'
_MAGIC_VMUL = 0x564D554C  # 'VMUL'


async def _rvv_peak_mlops_case(
    dut,
    elf_file: str,
    expected_magic: int,
    case_tag: str,
):
    """Same flow as _rvv_matmul_mlops_case in rvv_ml_ops_32x16_cocotb_test (minus lhs/rhs)."""
    fixture = await Fixture.Create(dut)
    r = runfiles.Create()

    await fixture.load_elf_and_lookup_symbols(
        r.Rlocation('coralnpu_hw/tests/cocotb/rvv/ml_ops/' + elf_file),
        ['bench_result', 'peak_sink'],
    )

    await fixture.run_until_bench_magic(
        expected_magic, timeout_cycles=5_000_000)

    bench = (await fixture.read('bench_result', 16)).view(dtype=np.uint32)
    iters = int(bench[0])
    cycles = (int(bench[2]) << 32) | int(bench[1])
    magic = int(bench[3])

    assert magic == expected_magic, (
        f'{case_tag}: magic {hex(magic)} != {hex(expected_magic)}'
    )
    assert iters == 1
    assert cycles > 0

    ops_per_cycle = _TIMED_VECTOR_OPS / cycles
    cocotb.log.info(
        f"[RVV PEAK PERF] case={case_tag} elf={elf_file} cycles={cycles} "
        f"timed_vector_ops={_TIMED_VECTOR_OPS} vector_ops/cycle={ops_per_cycle:.6f}"
    )


@cocotb.test()
async def core_mini_rvv_alu_peak_vadd_bench_test(dut):
    """Dense vadd.vv kernel only (inner-loop mcycle in bench_result)."""
    await _rvv_peak_mlops_case(
        dut,
        elf_file='rvv_alu_peak_vadd_bench.elf',
        expected_magic=_MAGIC_VADD,
        case_tag='alu_vadd',
    )


@cocotb.test()
async def core_mini_rvv_mac_peak_vmul_bench_test(dut):
    """Dense vmul.vv kernel only (inner-loop mcycle in bench_result)."""
    await _rvv_peak_mlops_case(
        dut,
        elf_file='rvv_mac_peak_vmul_bench.elf',
        expected_magic=_MAGIC_VMUL,
        case_tag='mul_vmul',
    )
