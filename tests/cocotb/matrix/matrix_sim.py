import cocotb

from cocotb.triggers import ClockCycles, RisingEdge
from bazel_tools.tools.python.runfiles import runfiles
from coralnpu_test_utils.core_mini_axi_interface import CoreMiniAxiInterface


async def _mk_core(dut):
    core = CoreMiniAxiInterface(dut)
    # Start the clock first to avoid init/reset races.
    cocotb.start_soon(core.clock.start())
    await core.init()
    await core.reset()
    await ClockCycles(dut.io_aclk, 5)
    return core


async def _run_elf_and_wait_tohost(
    core: CoreMiniAxiInterface, elf_rloc: str, timeout_cycles: int = 2_000_000
):
    r = runfiles.Create()
    elf_path = r.Rlocation(elf_rloc)
    with open(elf_path, "rb") as f:
        tohost = core.lookup_symbol(f, "tohost")
        assert tohost is not None
        entry = await core.load_elf(f)

        # Force a clean, stable start value to avoid reading residual/unstable
        # tohost data and treating a later read of 0 as "completion".
        await core.write_word(tohost, 0)
        await ClockCycles(core.dut.io_aclk, 2)

        await core.execute_from(entry)

        # Poll tohost; only non-zero means the program reported completion/result.
        for _ in range(timeout_cycles):
            await RisingEdge(core.dut.io_aclk)
            if int(core.dut.io_fault.value) == 1:
                break
            if int(core.dut.io_halted.value) == 1:
                break
            rv = await core.read_word(tohost)
            val = int(rv[0])
            if val != 0:
                return val

    # If we got here, timeout or fault/halt without a tohost change.
    return None


async def _assert_pass(core: CoreMiniAxiInterface, elf_rloc: str):
    tohost_val = await _run_elf_and_wait_tohost(core, elf_rloc)
    assert int(core.dut.io_fault.value) == 0
    assert tohost_val is not None
    assert tohost_val == 1, f"expected tohost=1, got 0x{tohost_val:08x}"


@cocotb.test()
async def matrix_minimal_demo_smoke(dut):
    """Smoke test: load matrix_minimal_demo.elf and expect tohost=1."""
    core = await _mk_core(dut)
    await _assert_pass(core, "coralnpu_hw/tests/verilator_sim/matrix_minimal_demo.elf")


@cocotb.test()
async def matrix_mac_acc_smoke(dut):
    core = await _mk_core(dut)
    await _assert_pass(core, "coralnpu_hw/tests/verilator_sim/matrix_mac_acc_demo.elf")


@cocotb.test()
async def matrix_alt_addr_smoke(dut):
    core = await _mk_core(dut)
    await _assert_pass(core, "coralnpu_hw/tests/verilator_sim/matrix_alt_addr_demo.elf")


@cocotb.test()
async def matrix_two_blocks_smoke(dut):
    core = await _mk_core(dut)
    await _assert_pass(core, "coralnpu_hw/tests/verilator_sim/matrix_two_blocks_demo.elf")


@cocotb.test()
async def matrix_intentional_fail_is_caught(dut):
    """Proves the testbench can detect a non-1 tohost code (negative test)."""
    core = await _mk_core(dut)
    tohost_val = await _run_elf_and_wait_tohost(core, "coralnpu_hw/tests/verilator_sim/matrix_intentional_fail_demo.elf")
    assert int(core.dut.io_fault.value) == 0
    assert tohost_val is not None
    assert tohost_val != 1


@cocotb.test()
async def matrix4x4_minimal_demo_smoke(dut):
    core = await _mk_core(dut)
    await _assert_pass(core, "coralnpu_hw/tests/verilator_sim/matrix4x4_minimal_demo.elf")


