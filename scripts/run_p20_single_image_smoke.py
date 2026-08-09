from __future__ import annotations

import hashlib
import json
import os
import sys
import time
from io import BytesIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from PIL import Image, ImageDraw

from local_analysis_service.service import Qwen3VlRuntimeAdapter


def main() -> int:
    evidence_dir = Path(os.environ.get("P20_EVIDENCE_DIR", ""))
    if not evidence_dir:
        print(json.dumps({"status": "BLOCKED", "code": "P20_EVIDENCE_DIR_REQUIRED"}))
        return 2
    evidence_dir.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (512, 384), (32, 58, 88))
    draw = ImageDraw.Draw(image)
    draw.rectangle((92, 64, 420, 320), fill=(210, 138, 83))
    draw.ellipse((190, 92, 320, 222), fill=(240, 196, 160))
    draw.line((256, 222, 256, 300), fill=(40, 40, 40), width=12)
    image_path = evidence_dir / "synthetic-smoke.jpg"
    image.save(image_path, format="JPEG", quality=90, exif=b"")
    image_bytes = image_path.read_bytes()
    runtime = Qwen3VlRuntimeAdapter(Path(os.environ.get("PHOTOAI_MODEL_DIR", "")))
    result: dict[str, object] = {"status": "BLOCKED", "gate": "A"}
    started = time.perf_counter()
    try:
        import psutil
        import torch

        free_before, total_vram = torch.cuda.mem_get_info() if torch.cuda.is_available() else (0, 0)
        ram_before = psutil.virtual_memory().available
        runtime.load_model()
        free_after_load, _ = torch.cuda.mem_get_info()
        payload, metrics = runtime.analyze_image("smoke_001", image_bytes)
        canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")
        free_after_inference, _ = torch.cuda.mem_get_info()
        result = {
            "status": "PASS" if metrics.get("pixel_values_present") and payload.get("bundle", {}).get("reference_id") == "smoke_001" else "FAIL",
            "gate": "A",
            "code": "P20_SINGLE_IMAGE_VLM_SMOKE_PASS" if metrics.get("pixel_values_present") else "P20_SINGLE_IMAGE_VLM_SMOKE_FAIL",
            "model": runtime.model_identity(),
            "device": runtime.device,
            "cuda_available": bool(torch.cuda.is_available()),
            "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else None,
            "total_vram_bytes": int(total_vram),
            "free_vram_before_bytes": int(free_before),
            "free_vram_after_load_bytes": int(free_after_load),
            "free_vram_after_inference_bytes": int(free_after_inference),
            "ram_available_before_bytes": int(ram_before),
            "ram_available_after_bytes": int(psutil.virtual_memory().available),
            "metrics": metrics,
            "output_digest": hashlib.sha256(canonical).hexdigest(),
            "elapsed_ms": int((time.perf_counter() - started) * 1000),
            "offline": os.environ.get("HF_HUB_OFFLINE") == "1" and os.environ.get("TRANSFORMERS_OFFLINE") == "1",
        }
    except Exception as exc:
        result.update({"code": type(exc).__name__, "error_class": type(exc).__name__})
    finally:
        runtime.unload_model()
        image_path.unlink(missing_ok=True)
    (evidence_dir / "gate-a-sanitized.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0 if result.get("status") == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
