from bazel_tools.tools.python.runfiles import runfiles
print("DEBUG: npusim_run_mobilenet.py is really being executed", flush=True)
from coralnpu_v2_sim_utils import CoralNPUV2Simulator

import argparse
import os
from typing import List, Optional

import numpy as np

INPUT_H = 224
INPUT_W = 224
INPUT_C = 3
OUTPUT_DIM = 5


def load_input_from_npy(npy_path: str) -> np.ndarray:
    arr = np.load(npy_path)
    arr = np.asarray(arr, dtype=np.int8).reshape(-1)
    expected_size = INPUT_H * INPUT_W * INPUT_C
    if arr.size != expected_size:
        raise RuntimeError(f"Input size mismatch: got {arr.size}, expected {expected_size}")
    return arr


def get_default_labels() -> List[str]:
    return [f"class_{i}" for i in range(OUTPUT_DIM)]


def parse_labels(label_str: Optional[str]) -> List[str]:
    if not label_str:
        return get_default_labels()

    labels = [x.strip() for x in label_str.split(",") if x.strip()]
    if len(labels) != OUTPUT_DIM:
        raise ValueError(
            f"Expected exactly {OUTPUT_DIM} labels, but got {len(labels)}: {labels}"
        )
    return labels


def run_full_mobilenet_with_npy(
    npy_path: str,
    verbose_symbols: bool = False,
) -> dict:
    print("Running full mobilenet with npy input...")

    npu_sim = CoralNPUV2Simulator(highmem_ld=True, exit_on_ebreak=True)
    r = runfiles.Create()

    elf_file = r.Rlocation(
        "coralnpu_hw/tests/npusim_examples/run_full_mobilenet_v1_binary.elf"
    )
    if elf_file is None or not os.path.exists(elf_file):
        raise FileNotFoundError(
            "Could not locate run_full_mobilenet_v1_binary.elf via Bazel runfiles."
        )

    entry_point, symbol_map = npu_sim.get_elf_entry_and_symbol(
        elf_file,
        ["inference_status", "inference_input", "inference_output"],
    )

    if verbose_symbols:
        print(f"ELF file: {elf_file}")
        print(f"Entry point: 0x{entry_point:08x}")
        for k, v in symbol_map.items():
            print(f"Symbol {k}: 0x{v:08x}")

    npu_sim.load_program(elf_file, entry_point)

    if symbol_map.get("inference_input") is None:
        raise RuntimeError("Symbol 'inference_input' not found in ELF.")

    input_data = load_input_from_npy(npy_path)
    npu_sim.write_memory(symbol_map["inference_input"], input_data)

    print("Running simulation...", flush=True)
    npu_sim.run()
    npu_sim.wait()

    cycles = npu_sim.get_cycle_count()
    print(f"cycles taken by the simulation {cycles}")

    output_data = None
    if symbol_map.get("inference_output") is not None:
        output_raw = npu_sim.read_memory(symbol_map["inference_output"], OUTPUT_DIM)
        output_data = np.array(output_raw, dtype=np.int8)

        max_idx = int(np.argmax(output_data))
        print(
            f"Output info: Top index {max_idx} with value {int(output_data[max_idx])} "
            f"from {output_data.tolist()}"
        )

    inference_status = None
    if symbol_map.get("inference_status") is not None:
        inference_status = int(
            np.array(
                npu_sim.read_memory(symbol_map["inference_status"], 1),
                dtype=np.int8,
            )[0]
        )
        print(f"inference_status {inference_status}")

    return {
        "elf_file": elf_file,
        "entry_point": entry_point,
        "symbol_map": symbol_map,
        "cycles": cycles,
        "output_data": output_data,
        "inference_status": inference_status,
    }


def main():
    parser = argparse.ArgumentParser(
        description="Run CoralNPU npusim MobileNet tutorial with npy input."
    )
    parser.add_argument("--input_npy", required=True, help="Path to preprocessed int8 numpy input.")
    parser.add_argument(
        "--labels",
        default=None,
        help="Comma-separated labels for 5 outputs.",
    )
    parser.add_argument(
        "--verbose_symbols",
        action="store_true",
        help="Print ELF entry point and symbol addresses.",
    )

    args = parser.parse_args()

    if not os.path.exists(args.input_npy):
        raise FileNotFoundError(f"Input npy not found: {args.input_npy}")

    labels = parse_labels(args.labels)

    result = run_full_mobilenet_with_npy(
        npy_path=args.input_npy,
        verbose_symbols=args.verbose_symbols,
    )

    output_data = result["output_data"]
    status = result["inference_status"]

    if output_data is None:
        raise RuntimeError("Failed to read inference_output from simulator.")

    if status != 0:
        raise RuntimeError(f"Inference failed, inference_status = {status}")

    top_idx = int(np.argmax(output_data))
    print(f"Top-1 label: {labels[top_idx]}, scores={output_data.tolist()}")


if __name__ == "__main__":
    main()