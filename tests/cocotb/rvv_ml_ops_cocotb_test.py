import cocotb
import numpy as np
import argparse

from coralnpu_test_utils.sim_test_fixture import Fixture
from bazel_tools.tools.python.runfiles import runfiles
from cocotb.triggers import RisingEdge, ReadOnly
from cocotb.utils import get_sim_time

import os
from pathlib import Path


def _find_dump_vcd(hdl_toplevel: str) -> Path | None:
    # The verilator_cocotb_model rule emits <hdl_toplevel>_build as its outdir.
    candidates = [
        Path("dump.vcd"),
        Path(f"{hdl_toplevel}_build") / "dump.vcd",
    ]
    for p in candidates:
        if p.exists():
            return p
    return None


def _trim_vcd_keep_t0_and_window(
    in_path: Path, out_path: Path, start_ps: int, end_ps: int
) -> None:
    """Trim a VCD by time window while preserving header and t=0 dump.

    We keep:
    - full header
    - everything at time 0 (initial values)
    - value changes for timestamps within [start_tick, end_tick]

    NOTE: VCD timestamps are in units of the file's $timescale. We convert from
    ps to VCD ticks by reading $timescale.
    """
    # Parse timescale (default 1ns if not found, but VCDs should have it).
    timescale_ps = None
    with in_path.open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if line.startswith("$timescale"):
                # Typical: "$timescale 1ns $end" or multiline with value on next line.
                parts = line.strip().split()
                if len(parts) >= 2 and parts[1] != "$end":
                    ts = parts[1]
                else:
                    ts = next(f).strip()
                # ts like "1ns" "10ps" "1ps"
                num = ""
                unit = ""
                for ch in ts:
                    if ch.isdigit():
                        num += ch
                    else:
                        unit += ch
                n = int(num) if num else 1
                unit = unit.strip()
                unit_to_ps = {
                    "s": 1_000_000_000_000,
                    "ms": 1_000_000_000,
                    "us": 1_000_000,
                    "ns": 1_000,
                    "ps": 1,
                    "fs": 0,  # unsupported for our conversion
                }
                if unit not in unit_to_ps or unit_to_ps[unit] == 0:
                    raise RuntimeError(f"Unsupported VCD timescale unit: {ts}")
                timescale_ps = n * unit_to_ps[unit]
                break
    if timescale_ps is None:
        timescale_ps = 1_000

    start_tick = max(0, start_ps // timescale_ps)
    end_tick = max(start_tick, end_ps // timescale_ps)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    wrote_t0 = False
    cur_t = None

    with in_path.open("r", encoding="utf-8", errors="ignore") as fin, out_path.open(
        "w", encoding="utf-8"
    ) as fout:
        in_header = True
        keep_block = False

        for line in fin:
            if in_header:
                fout.write(line)
                if line.startswith("#"):
                    in_header = False
                    try:
                        cur_t = int(line[1:].strip())
                    except ValueError:
                        cur_t = None
                    # Keep time 0 block; also keep if within window.
                    keep_block = (cur_t == 0) or (
                        cur_t is not None and start_tick <= cur_t <= end_tick
                    )
                    wrote_t0 = wrote_t0 or (cur_t == 0)
                continue

            if line.startswith("#"):
                try:
                    cur_t = int(line[1:].strip())
                except ValueError:
                    cur_t = None
                keep_block = (cur_t == 0) or (
                    cur_t is not None and start_tick <= cur_t <= end_tick
                )
                if keep_block:
                    fout.write(line)
                    wrote_t0 = wrote_t0 or (cur_t == 0)
                continue

            if keep_block:
                fout.write(line)

    if not wrote_t0:
        cocotb.log.warning(
            f"[VCD TRIM] {in_path} did not contain a t=0 dump block; "
            "some signals may appear as X until they toggle."
        )


async def _capture_toggle_window_ps(sig, timeout_cycles: int = 2_000_000):
    """Wait for sig 0->1 then 1->0, return (start_ps, end_ps) or None."""
    # Wait for rising edge
    for _ in range(timeout_cycles):
        await RisingEdge(sig._path.root.io_aclk)  # type: ignore[attr-defined]
        await ReadOnly()
        if int(sig.value) == 1:
            start_ps = int(get_sim_time(units="ps"))
            break
    else:
        return None

    # Wait for falling edge
    for _ in range(timeout_cycles):
        await RisingEdge(sig._path.root.io_aclk)  # type: ignore[attr-defined]
        await ReadOnly()
        if int(sig.value) == 0:
            end_ps = int(get_sim_time(units="ps"))
            return (start_ps, end_ps)
    return None


@cocotb.test()
async def core_mini_rvv_matmul_test(dut):
    """Testbench to test matmul with rvv intrinsics.

    This test performs matmul in M1 16x24 M2 24x16 matrices.
    Compares results with native numpy matmul.
    """

    LHS_ROWS = 16
    RHS_COLS = 16
    INNER = 48

    fixture = await Fixture.Create(dut)
    r = runfiles.Create()
    elf_files = ['rvv_matmul.elf', 'rvv_matmul_assembly.elf']
    for elf_file in elf_files:

        await fixture.load_elf_and_lookup_symbols(
            r.Rlocation('coralnpu_hw/tests/cocotb/rvv/ml_ops/' + elf_file),
            ['lhs_input', 'rhs_input', 'result_output', 'bench_result'])
        np_type = np.int8
        min_value = np.iinfo(np_type).min
        max_value = np.iinfo(np_type).max + 1  # One above.
        lhs_data = np.random.randint(min_value,
                                     max_value, [LHS_ROWS, INNER],
                                     dtype=np_type)
        rhs_data = np.random.randint(min_value,
                                     max_value, [INNER, RHS_COLS],
                                     dtype=np_type)
        result_data = np.matmul(lhs_data.astype(np.int32),
                                rhs_data.astype(np.int32))

        await fixture.write('lhs_input', lhs_data.flatten())
        await fixture.write('rhs_input', rhs_data.transpose().flatten())
        # Capture a trim window for waveforms:
        # - Prefer a SW-defined window marker if present.
        # - Otherwise fall back to (start=execute, end=halt).
        marker = getattr(dut, "io_coralnpu_csr_value_8", None)
        window_task = None
        if marker is not None:
            window_task = cocotb.start_soon(_capture_toggle_window_ps(marker))

        start_ps_fallback = int(get_sim_time(units="ps"))
        await fixture.run_to_halt(timeout_cycles=1000000)
        end_ps_fallback = int(get_sim_time(units="ps"))

        window_ps = None
        if window_task is not None:
            try:
                window_ps = window_task.result()
            except Exception:
                window_ps = None
        if window_ps is None:
            window_ps = (start_ps_fallback, end_ps_fallback)

        # Add +/- margin (in cycles) around the window to retain context.
        margin_cycles = int(os.environ.get("CORALNPU_VCD_MARGIN_CYCLES", "2000"))
        clk_ps = int(fixture.core_mini_axi.clock_ns * 1000)
        start_ps = max(0, window_ps[0] - margin_cycles * clk_ps)
        end_ps = window_ps[1] + margin_cycles * clk_ps

        dump = _find_dump_vcd("RvvCoreMiniAxi")
        if dump is not None:
            out = dump.with_name(f"{dump.stem}_{elf_file}_window.vcd")
            cocotb.log.info(
                f"[VCD TRIM] Trimming {dump} to {out} "
                f"(start_ps={start_ps}, end_ps={end_ps}, margin_cycles={margin_cycles})"
            )
            _trim_vcd_keep_t0_and_window(dump, out, start_ps=start_ps, end_ps=end_ps)
        else:
            cocotb.log.warning(
                "[VCD TRIM] dump.vcd not found; set waves=True and ensure "
                "verilator model trace is enabled."
            )

        output_matmul_result = (await fixture.read(
            'result_output', LHS_ROWS * RHS_COLS *
            4)).view(dtype=np.int32).reshape([LHS_ROWS, RHS_COLS])

        assert ((result_data == output_matmul_result).all())

        bench = (await fixture.read('bench_result', 16)).view(dtype=np.uint32)
        iters = int(bench[0])
        cycles = (int(bench[2]) << 32) | int(bench[1])
        magic = int(bench[3])
        assert magic == 0x4D4C4F50  # 'MLOP'
        assert iters == 1
        assert cycles > 0

        macs = LHS_ROWS * RHS_COLS * INNER
        mac_per_cycle = macs / cycles
        cocotb.log.info(
            f"[RVV MLOP PERF] elf={elf_file} cycles={cycles} "
            f"MACs={macs} MAC/cycle={mac_per_cycle:.6f}"
        )
