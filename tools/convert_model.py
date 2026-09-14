"""Installs the bundled NSFW classifier into safecore/src/main/assets/nsfw.tflite.

SPEC.md M1's primary path is `TFLiteConverter.from_saved_model()` against a
Keras model. That path needs TensorFlow, which has no wheel for the Python
version available when this was written (see docs/DECISIONS.md D9). This
script takes SPEC.md's documented fallback instead: fetch a pre-converted
`.tflite` from an existing open-source Android NSFW project, pinned to an
exact commit and verified by checksum, rather than converting anything
ourselves.

Usage:
    python tools/convert_model.py --out safecore/src/main/assets/nsfw.tflite
"""

from __future__ import annotations

import argparse
import hashlib
import shutil
import tempfile
import urllib.request
from pathlib import Path

# nipunru/nsfw-detector-android, MIT licensed. 2-class (nonnude/nude) model
# exported for Firebase AutoML on-device labeling. Pinned to the commit that
# last touched this file so re-running this script is reproducible.
SOURCE_URL = (
    "https://raw.githubusercontent.com/nipunru/nsfw-detector-android/"
    "d67bea108ce995b8090fd71c446627a2b5c7c13e/"
    "nsfwdetector/src/main/assets/automl/NSFW.tflite"
)
EXPECTED_SHA256 = "51cc2d2997bb1e87fa20416d5a528951980b4fc37e4eb30c08d5b1e11b7f2a2d"
MIN_SIZE_BYTES = 3 * 1024 * 1024
MAX_SIZE_BYTES = 30 * 1024 * 1024


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as tmp:
        downloaded = Path(tmp) / "nsfw.tflite"
        urllib.request.urlretrieve(SOURCE_URL, downloaded)

        actual_sha256 = _sha256(downloaded)
        if actual_sha256 != EXPECTED_SHA256:
            raise SystemExit(
                f"checksum mismatch: expected {EXPECTED_SHA256}, got {actual_sha256}. "
                "Upstream file changed — do not proceed without re-verifying provenance."
            )

        size = downloaded.stat().st_size
        if not MIN_SIZE_BYTES <= size <= MAX_SIZE_BYTES:
            raise SystemExit(f"model size {size} bytes is outside the expected 3-30 MB range")

        args.out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(downloaded, args.out)

    print(f"wrote {args.out} ({size} bytes, sha256={actual_sha256})")


if __name__ == "__main__":
    main()
