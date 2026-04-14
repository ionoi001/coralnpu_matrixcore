"""RVV matmul cocotb: small case 8x4 x 4x8 only."""

import cocotb
import numpy as np

from coralnpu_test_utils.sim_test_fixture import Fixture
from bazel_tools.tools.python.runfiles import runfiles


async def _rvv_matmul_mlops_case(
    dut,
    lhs_rows: int,
    inner: int,
    rhs_cols: int,
    elf_files: list[str],
    case_tag: str,
):
    """RVV int8 matmul check vs numpy; RHS is column-major in memory."""
    fixture = await Fixture.Create(dut)
    r = runfiles.Create()
    for elf_file in elf_files:

        await fixture.load_elf_and_lookup_symbols(
            r.Rlocation('coralnpu_hw/tests/cocotb/rvv/ml_ops/' + elf_file),
            ['lhs_input', 'rhs_input', 'result_output', 'bench_result'])
        np_type = np.int8
        min_value = np.iinfo(np_type).min
        max_value = np.iinfo(np_type).max + 1  # One above.
        lhs_data = np.random.randint(min_value,
                                     max_value, [lhs_rows, inner],
                                     dtype=np_type)
        rhs_data = np.random.randint(min_value,
                                     max_value, [inner, rhs_cols],
                                     dtype=np_type)
        result_data = np.matmul(lhs_data.astype(np.int32),
                                rhs_data.astype(np.int32))

        await fixture.write('lhs_input', lhs_data.flatten())
        await fixture.write('rhs_input', rhs_data.transpose().flatten())

        await fixture.run_to_halt(timeout_cycles=1000000)

        output_matmul_result = (await fixture.read(
            'result_output', lhs_rows * rhs_cols *
            4)).view(dtype=np.int32).reshape([lhs_rows, rhs_cols])

        assert ((result_data == output_matmul_result).all())

        bench = (await fixture.read('bench_result', 16)).view(dtype=np.uint32)
        iters = int(bench[0])
        cycles = (int(bench[2]) << 32) | int(bench[1])
        magic = int(bench[3])
        assert magic == 0x4D4C4F50  # 'MLOP'
        assert iters == 1
        assert cycles > 0

        macs = lhs_rows * rhs_cols * inner
        mac_per_cycle = macs / cycles
        cocotb.log.info(
            f"[RVV MLOP PERF] case={case_tag} elf={elf_file} cycles={cycles} "
            f"MACs={macs} MAC/cycle={mac_per_cycle:.6f}"
        )


@cocotb.test()
async def core_mini_rvv_matmul_8x4_4x8_test(dut):
    """Smaller matmul: LHS 8x4 times RHS 4x8 -> 8x8 int32 (RHS column-major in ELF)."""

    await _rvv_matmul_mlops_case(
        dut,
        lhs_rows=8,
        inner=4,
        rhs_cols=8,
        elf_files=['rvv_matmul_8x4_4x8.elf'],
        case_tag='8x4x8',
    )
