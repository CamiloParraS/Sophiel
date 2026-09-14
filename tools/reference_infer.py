"""Runs the bundled fixtures through nsfw.tflite and writes expected_logits.json.

This is the Python reference for ParityTest.kt's M1 parity gate: the score
computed here for each fixture must match the on-device Interpreter's score
for the same fixture within 1e-2 absolute (SPEC.md M1.V3).

Preprocessing here mirrors Preprocessor.kt exactly: NHWC float32 RGB in
[0,1] (see docs/DECISIONS.md D12). Score is hentai + porn + sexy, matching
NsfwClassifier.unsafeScore. Fixtures are pre-sized to 224x224 so resize
interpolation never enters the comparison.

Usage:
    python tools/reference_infer.py \
        --model safecore/src/main/assets/nsfw.tflite \
        --fixtures safecore/src/androidTest/assets/fixtures
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from ai_edge_litert.interpreter import Interpreter
from PIL import Image

INPUT_SIZE = 224
HENTAI, PORN, SEXY = 1, 3, 4


def score(interpreter: Interpreter, image_path: Path) -> float:
    image = Image.open(image_path).convert("RGB")
    if image.size != (INPUT_SIZE, INPUT_SIZE):
        raise ValueError(f"{image_path} is {image.size}, expected {INPUT_SIZE}x{INPUT_SIZE}")

    pixels = np.asarray(image, dtype=np.float32) / 255.0  # HWC, RGB, [0,1]
    input_tensor = pixels[np.newaxis, ...]  # NHWC

    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]

    interpreter.set_tensor(input_details["index"], input_tensor)
    interpreter.invoke()
    probs = interpreter.get_tensor(output_details["index"])[0]  # drawings, hentai, neutral, porn, sexy

    return float(probs[HENTAI] + probs[PORN] + probs[SEXY])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--fixtures", required=True, type=Path)
    args = parser.parse_args()

    interpreter = Interpreter(model_path=str(args.model))
    interpreter.allocate_tensors()

    fixtures = sorted(args.fixtures.glob("*.png"))
    if not fixtures:
        raise SystemExit(f"no .png fixtures found in {args.fixtures}")

    expected = {f.name: score(interpreter, f) for f in fixtures}

    out_path = args.fixtures / "expected_logits.json"
    out_path.write_text(json.dumps(expected, indent=2, sort_keys=True) + "\n")
    print(f"wrote {out_path} ({len(expected)} fixtures)")


if __name__ == "__main__":
    main()
