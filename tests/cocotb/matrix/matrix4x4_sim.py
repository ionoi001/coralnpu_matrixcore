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


@cocotb.test()
async def matrix4x4_minimal_demo_smoke(dut):
    core = await mk_core(dut)
    await assert_pass(core, "coralnpu_hw/tests/verilator_sim/matrix4x4_minimal_demo.elf")


@cocotb.test()
async def matrix4x4_mac_per_cycle_bench(dut):
  """End-to-end MAC/cycle benchmark using mcycle from target software."""
  core = await mk_core(dut)

  r = runfiles.Create()
  elf_path = r.Rlocation("coralnpu_hw/tests/verilator_sim/matrix4x4_bench_demo.elf")

  kResultBase = 0x10100
  sweep = [2000, 20000, 200000]

  for iters_req in sweep:
    # Fresh run each sweep point.
    await core.reset()
    await ClockCycles(dut.io_aclk, 5)

    with open(elf_path, "rb") as f:
      tohost = core.lookup_symbol(f, "tohost")
      assert tohost is not None
      entry = await core.load_elf(f)

      # Program reads iters from mailbox before timing.
      await core.write_word(kResultBase + 0, iters_req)
      await core.write_word(kResultBase + 4, 0)
      await core.write_word(kResultBase + 8, 0)
      await core.write_word(kResultBase + 12, 0)

      await core.write_word(tohost, 0)
      await ClockCycles(dut.io_aclk, 2)
      await core.execute_from(entry)

      # Wait for non-zero tohost.
      tohost_val = None
      for _ in range(20_000_000):
        await RisingEdge(dut.io_aclk)
        if int(core.dut.io_fault.value) == 1:
          break
        rv = await core.read_word(tohost)
        val = int(rv[0])
        if val != 0:
          tohost_val = val
          break

    assert int(core.dut.io_fault.value) == 0
    assert tohost_val == 1

    iters = int((await core.read_word(kResultBase + 0)).view("uint32")[0])
    cycles_lo = int((await core.read_word(kResultBase + 4)).view("uint32")[0])
    cycles_hi = int((await core.read_word(kResultBase + 8)).view("uint32")[0])
    magic = int((await core.read_word(kResultBase + 12)).view("uint32")[0])
    assert magic == 0x4D415458  # 'MATX'

    cycles = (cycles_hi << 32) | cycles_lo
    assert cycles > 0

    macs = iters * 64  # 4x4x4
    mac_per_cycle = macs / cycles
    cocotb.log.info(f"[BENCH] iters={iters} cycles={cycles} MACs={macs} MAC/cycle={mac_per_cycle:.6f}")

