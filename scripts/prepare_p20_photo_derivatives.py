from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path

from PIL import Image, ImageOps


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    source = Path(os.environ.get("P20_SOURCE_DIR", "F:\\npi_g1_source_ro"))
    output = Path(os.environ["P20_DERIVATIVE_DIR"])
    if not source.is_dir():
        raise SystemExit("P20_SOURCE_NOT_FOUND")
    inputs = sorted((path for path in source.iterdir() if path.is_file() and path.suffix.lower() == ".jpg"), key=lambda path: path.name.lower())
    if len(inputs) != 20:
        raise SystemExit("P20_SOURCE_COUNT_NOT_20")
    ranked = sorted(((digest(path), path) for path in inputs), key=lambda item: item[0])
    output.mkdir(parents=True, exist_ok=True)
    manifest: list[dict[str, object]] = []
    for index, (input_sha, path) in enumerate(ranked, start=1):
        anonymous_id = f"photo_{index:03d}"
        target = output / f"{anonymous_id}.jpg"
        try:
            with Image.open(path) as source_image:
                image = ImageOps.exif_transpose(source_image).convert("RGB")
                image.thumbnail((2048, 2048), Image.Resampling.LANCZOS)
                image.save(target, format="JPEG", quality=92, optimize=True, exif=b"")
                width, height = image.size
        except Exception as exc:
            raise SystemExit("P20_IMAGE_DERIVATIVE_FAILED") from exc
        manifest.append({
            "anonymous_id": anonymous_id,
            "input_sha256": input_sha,
            "derivative_sha256": digest(target),
            "width": width,
            "height": height,
        })
    (output / "manifest.json").write_text(json.dumps({"count": len(manifest), "items": manifest}, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"count": len(manifest), "anonymous_ids": [item["anonymous_id"] for item in manifest]}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
