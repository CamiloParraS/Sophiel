"""Installs the bundled NSFW classifier into safecore/src/main/assets/nsfw.tflite.

Source is GantMan/nsfw_model (MIT) release 1.2.0, MobileNetV2 140 @ 224x224,
5 classes (drawings, hentai, neutral, porn, sexy). The release zip already
ships a converted `saved_model.tflite`, so no TensorFlow install or conversion
step is needed (see docs/DECISIONS.md D12). The file is pinned by SHA-256.

Usage:
    python tools/convert_model.py --out safecore/src/main/assets/nsfw.tflite
"""

from __future__ import annotations

import argparse
import hashlib
import tempfile
import urllib.request
import zipfile
from pathlib import Path

SOURCE_URL = "https://github.com/GantMan/nsfw_model/releases/download/1.2.0/mobilenet_v2_140_224.1.zip"
MEMBER = "mobilenet_v2_140_224/saved_model.tflite"
EXPECTED_SHA256 = "380f98f7685f9d8a386f8cc595b6dfcb972989aae3d1b8b270d3a4a5b96fab40"
MIN_SIZE_BYTES = 3 * 1024 * 1024
MAX_SIZE_BYTES = 30 * 1024 * 1024


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    with tempfile.TemporaryDirectory() as tmp:
        archive = Path(tmp) / "model.zip"
        urllib.request.urlretrieve(SOURCE_URL, archive)
        with zipfile.ZipFile(archive) as z:
            data = z.read(MEMBER)

    actual_sha256 = hashlib.sha256(data).hexdigest()
    if actual_sha256 != EXPECTED_SHA256:
        raise SystemExit(
            f"checksum mismatch: expected {EXPECTED_SHA256}, got {actual_sha256}. "
            "Upstream file changed — do not proceed without re-verifying provenance."
        )
    if not MIN_SIZE_BYTES <= len(data) <= MAX_SIZE_BYTES:
        raise SystemExit(f"model size {len(data)} bytes is outside the expected 3-30 MB range")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(data)
    print(f"wrote {args.out} ({len(data)} bytes, sha256={actual_sha256})")


if __name__ == "__main__":
    main()
