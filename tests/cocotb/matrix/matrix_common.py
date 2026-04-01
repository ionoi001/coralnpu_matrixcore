import cocotb

from cocotb.triggers import ClockCycles, RisingEdge
from bazel_tools.tools.python.runfiles import runfiles
from coralnpu_test_utils.core_mini_axi_interface import CoreMiniAxiInterface


async def mk_core(dut):
    core = CoreMiniAxiInterface(dut)
    cocotb.start_soon(core.clock.start())
    await core.init()
    await core.reset()
    await ClockCycles(dut.io_aclk, 5)
    return core


async def run_elf_and_wait_tohost(core: CoreMiniAxiInterface, elf_rloc: str, timeout_cycles: int = 2_000_000):
    r = runfiles.Create()
    elf_path = r.Rlocation(elf_rloc)
    with open(elf_path, "rb") as f:
        tohost = core.lookup_symbol(f, "tohost")
        assert tohost is not None
        entry = await core.load_elf(f)

        # Force a clean, stable start value.
        await core.write_word(tohost, 0)
        await ClockCycles(core.dut.io_aclk, 2)

        await core.execute_from(entry)

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
    return None


async def assert_pass(core: CoreMiniAxiInterface, elf_rloc: str):
    tohost_val = await run_elf_and_wait_tohost(core, elf_rloc)
    assert int(core.dut.io_fault.value) == 0
    assert tohost_val is not None
    assert tohost_val == 1, f"expected tohost=1, got 0x{tohost_val:08x}"

