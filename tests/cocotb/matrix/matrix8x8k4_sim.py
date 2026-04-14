import cocotb
import numpy as np

import json
import time

from cocotb.triggers import ClockCycles, RisingEdge
from bazel_tools.tools.python.runfiles import runfiles
from coralnpu_test_utils.core_mini_axi_interface import CoreMiniAxiInterface


TEST_VECTOR_VERSION = "matrix8x8k4_single_tile_correctness_v2_posB"


def _agent_log(hypothesis_id: str, message: str, data: dict):
    # #region agent log
    payload = {
        "sessionId": "5fe90f",
        "runId": "pre-fix",
        "hypothesisId": hypothesis_id,
        "location": "tests/cocotb/matrix/matrix8x8k4_sim.py",
        "message": message,
        "data": data,
        "timestamp": int(time.time() * 1000),
    }
    with open("debug-5fe90f.log", "a", encoding="utf-8") as f:
        f.write(json.dumps(payload, ensure_ascii=True) + "\n")
    # #endregion agent log


async def mk_core(dut):
    core = CoreMiniAxiInterface(dut)
    cocotb.start_soon(core.clock.start())
    await core.init()
    await core.reset()
    await ClockCycles(dut.io_aclk, 5)
    return core


@cocotb.test()
async def matrix8x8k4_single_tile_correctness(dut):
    """4.2.1: full program path; cocotb writes A/B, ELF runs SET_C+MAC+FENCE.I+tohost."""
    # IMPORTANT: avoid overlapping with the ELF's `.htif` section (tohost is often at 0x10000).
    k_a = 0x14000
    k_b = 0x15000
    k_c = 0x16000

    # Rule-based patterns (easy to eyeball): A row-major 1..32; B row-major 1..32 (all positive).
    lhs = np.arange(1, 33, dtype=np.int8).reshape(8, 4)
    rhs = np.arange(1, 33, dtype=np.int8).reshape(4, 8)
    ref = np.matmul(lhs.astype(np.int32), rhs.astype(np.int32))

    core = await mk_core(dut)
    r = runfiles.Create()
    elf_path = r.Rlocation("coralnpu_hw/tests/verilator_sim/matrix8x8k4_tile_correctness_demo.elf")
    _agent_log(
        "H0",
        "test start",
        {
            "test_vector_version": TEST_VECTOR_VERSION,
            "k_a": k_a,
            "k_b": k_b,
            "k_c": k_c,
            "elf": "matrix8x8k4_tile_correctness_demo.elf",
            "lhs_first8": [int(x) for x in lhs.reshape(-1)[:8]],
            "rhs_first8": [int(x) for x in rhs.reshape(-1)[:8]],
            "ref00": int(ref[0, 0]),
            "ref01": int(ref[0, 1]),
        },
    )
    cocotb.log.info(
        f"[{TEST_VECTOR_VERSION}] lhs_first8={lhs.reshape(-1)[:8].tolist()} rhs_first8={rhs.reshape(-1)[:8].tolist()} ref00={int(ref[0,0])}"
    )

    with open(elf_path, "rb") as f:
        entry = await core.load_elf(f)
        tohost = core.lookup_symbol(f, "tohost")
        assert tohost is not None
        _agent_log("H1", "elf loaded", {"entry": int(entry), "tohost": int(tohost)})
        assert not (k_a <= int(tohost) < (k_a + 32)), "A base overlaps tohost"
        assert not (k_b <= int(tohost) < (k_b + 32)), "B base overlaps tohost"
        assert not (k_c <= int(tohost) < (k_c + (8 * 8 * 4))), "C base overlaps tohost"

        await core.write(k_a, lhs.reshape(-1).view(np.uint8))
        await core.write(k_b, rhs.reshape(-1).view(np.uint8))
        await core.write(k_c, np.zeros(8 * 8, dtype=np.int32))

        # Sanity: make sure our A/B writes are visible through the same memory path.
        a_rb = (await core.read(k_a, 32)).view(np.int8).reshape(8, 4)
        b_rb = (await core.read(k_b, 32)).view(np.int8).reshape(4, 8)
        _agent_log(
            "H2",
            "A/B readback",
            {
                "a_ok": bool((a_rb == lhs).all()),
                "b_ok": bool((b_rb == rhs).all()),
                "a_first8": [int(x) for x in a_rb.reshape(-1)[:8]],
                "b_first8": [int(x) for x in b_rb.reshape(-1)[:8]],
            },
        )
        assert (a_rb == lhs).all(), "A write/readback mismatch"
        assert (b_rb == rhs).all(), "B write/readback mismatch"

        await core.write_word(tohost, 0)
        await ClockCycles(dut.io_aclk, 2)
        await core.execute_from(entry)

        for _ in range(5_000_000):
            await RisingEdge(dut.io_aclk)
            assert int(core.dut.io_fault.value) == 0
            rv = await core.read_word(tohost)
            if int(rv[0]) != 0:
                break
        else:
            assert False, "timeout waiting for tohost"

        tohost_val = int((await core.read_word(tohost))[0])
        _agent_log(
            "H3",
            "tohost observed",
            {
                "tohost": tohost_val,
                "io_fault": int(core.dut.io_fault.value),
                "io_halted": int(getattr(core.dut, "io_halted").value) if hasattr(core.dut, "io_halted") else None,
            },
        )
        cocotb.log.info(f"[{TEST_VECTOR_VERSION}] tohost={tohost_val} io_fault={int(core.dut.io_fault.value)}")
        assert tohost_val == 1

        # Some configurations can retire tohost before the matrix writeback is fully visible.
        # Wait a bit (and allow the fabric to drain) before sampling C.
        await ClockCycles(dut.io_aclk, 2000)

        # Probe whether C ever changes from zero after completion.
        c_samples = []
        for _ in range(8):
            w0 = int((await core.read_word(k_c))[0])
            c_samples.append(w0)
            await ClockCycles(dut.io_aclk, 250)
        _agent_log("H4", "C samples after tohost", {"c0_samples": c_samples})
        cocotb.log.info(f"[{TEST_VECTOR_VERSION}] C[0] samples after tohost: {c_samples}")

        # Debug: did the DUT issue any AXI master traffic (matrix backend DMA / writeback)?
        cocotb.log.info(
            f"[{TEST_VECTOR_VERSION}] AXI master counts: "
            f"AR={core._agent_dbg_master_ar_count} AW={core._agent_dbg_master_aw_count} W={core._agent_dbg_master_w_count} "
            f"first_aw={getattr(core, '_agent_dbg_master_aw_addrs', [])[:4]} first_ar={getattr(core, '_agent_dbg_master_ar_addrs', [])[:4]}"
        )

        out = (await core.read(k_c, 8 * 8 * 4)).view("int32").reshape(8, 8)
        assert (out == ref).all(), "single-tile C mismatch vs NumPy reference"
