import cocotb

from cocotb.triggers import ClockCycles, RisingEdge
from bazel_tools.tools.python.runfiles import runfiles
from coralnpu_test_utils.core_mini_axi_interface import CoreMiniAxiInterface

_MODULE_LOADED = True  # helps confirm import in logs if needed


async def mk_core(dut):
    core = CoreMiniAxiInterface(dut)
    cocotb.start_soon(core.clock.start())
    await core.init()
    await core.reset()
    await ClockCycles(dut.io_aclk, 5)
    return core


async def run_elf_and_wait_tohost(core: CoreMiniAxiInterface, elf_path: str, timeout_cycles: int):
    with open(elf_path, "rb") as f:
        tohost = core.lookup_symbol(f, "tohost")
        assert tohost is not None
        entry = await core.load_elf(f)
        await core.write_word(tohost, 0)
        await ClockCycles(core.dut.io_aclk, 2)
        await core.execute_from(entry)

        for _ in range(timeout_cycles):
            await RisingEdge(core.dut.io_aclk)
            if int(core.dut.io_fault.value) == 1:
                break
            rv = await core.read_word(tohost)
            val = int(rv[0])
            if val != 0:
                return val
    return None


@cocotb.test()
async def rvv_8x8x4_mac_per_cycle_bench(dut):
    """RVV end-to-end MAC/cycle bench for 8x8x4 tile (MACs per iter = 256)."""
    core = await mk_core(dut)
    r = runfiles.Create()
    elf_path = r.Rlocation("coralnpu_hw/tests/cocotb/rvv/rvv_matmul8x8k4_bench_demo.elf")

    kResultBase = 0x10100
    sweep = [500, 5000, 50000]

    for iters_req in sweep:
        await core.reset()
        await ClockCycles(dut.io_aclk, 5)

        await core.write_word(kResultBase + 0, iters_req)
        await core.write_word(kResultBase + 4, 0)
        await core.write_word(kResultBase + 8, 0)
        await core.write_word(kResultBase + 12, 0)

        tohost_val = await run_elf_and_wait_tohost(core, elf_path, timeout_cycles=50_000_000)
        assert int(core.dut.io_fault.value) == 0
        assert tohost_val == 1

        iters = int((await core.read_word(kResultBase + 0)).view("uint32")[0])
        cycles_lo = int((await core.read_word(kResultBase + 4)).view("uint32")[0])
        cycles_hi = int((await core.read_word(kResultBase + 8)).view("uint32")[0])
        magic = int((await core.read_word(kResultBase + 12)).view("uint32")[0])
        assert magic == 0x4D415458  # 'MATX'

        cycles = (cycles_hi << 32) | cycles_lo
        assert cycles > 0

        macs = iters * 256
        mac_per_cycle = macs / cycles
        cocotb.log.info(
            f"[RVV BENCH] iters={iters} cycles={cycles} MACs={macs} MAC/cycle={mac_per_cycle:.6f}"
        )

