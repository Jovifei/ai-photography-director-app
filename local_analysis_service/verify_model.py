from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_quarantine_manifest(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise SystemExit("MODEL_QUARANTINE_MANIFEST_INVALID") from exc
    if not isinstance(value, dict) or not isinstance(value.get("files"), list):
        raise SystemExit("MODEL_QUARANTINE_MANIFEST_INVALID")
    return value


def verify_files(model_dir: Path, manifest: dict) -> list[dict[str, object]]:
    verified: list[dict[str, object]] = []
    for entry in manifest["files"]:
        if not isinstance(entry, dict) or not isinstance(entry.get("name"), str):
            raise SystemExit("MODEL_QUARANTINE_MANIFEST_INVALID")
        relative = Path(entry["name"])
        if relative.is_absolute() or ".." in relative.parts:
            raise SystemExit("MODEL_MANIFEST_PATH_INVALID")
        path = model_dir / relative
        if not path.is_file():
            raise SystemExit("MODEL_FILE_MISSING")
        expected_size = int(entry.get("size_bytes", -1))
        expected_sha = str(entry.get("sha256", "")).lower()
        actual_size = path.stat().st_size
        actual_sha = sha256(path)
        if actual_size != expected_size or actual_sha != expected_sha:
            raise SystemExit("MODEL_FILE_HASH_MISMATCH")
        verified.append({"path": relative.as_posix(), "size": actual_size, "sha256": actual_sha})
    if not verified:
        raise SystemExit("MODEL_FILES_MISSING")
    return verified


def verify_config(model_dir: Path) -> None:
    try:
        config = json.loads((model_dir / "config.json").read_text(encoding="utf-8"))
        processor = json.loads((model_dir / "preprocessor_config.json").read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise SystemExit("MODEL_CONFIG_INVALID") from exc
    if config.get("model_type") != "qwen3_vl" or "Qwen3VLForConditionalGeneration" not in config.get("architectures", []):
        raise SystemExit("MODEL_CONFIG_INVALID")
    if "Qwen3VLProcessor" not in str(processor.get("processor_class", "")):
        raise SystemExit("MODEL_PROCESSOR_INVALID")


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify a quarantined local Qwen model; never downloads.")
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--model-id", required=True)
    parser.add_argument("--model-revision", required=True)
    parser.add_argument("--runtime-id", default="native-windows-transformers-4.57.6-qwen3vl-bf16")
    parser.add_argument("--write-manifest", action="store_true")
    args = parser.parse_args()
    quarantine_path = args.manifest or (args.model_dir / "quarantine_manifest.json")
    quarantine = load_quarantine_manifest(quarantine_path)
    quarantine_model_id = str(quarantine.get("model_id", "")).lower().split("/")[-1]
    requested_model_id = args.model_id.lower().split("/")[-1]
    if quarantine_model_id != requested_model_id or str(quarantine.get("revision", "")) != args.model_revision:
        raise SystemExit("MODEL_ID_OR_REVISION_MISMATCH")
    verify_config(args.model_dir)
    files = verify_files(args.model_dir, quarantine)
    weight_files = [item for item in files if str(item["path"]).endswith((".safetensors", ".bin"))]
    if len(weight_files) != 1:
        raise SystemExit("MODEL_WEIGHT_FILE_COUNT_INVALID")
    output = {
        "model_id": args.model_id,
        "model_revision": args.model_revision,
        "model_artifact_sha256": weight_files[0]["sha256"],
        "runtime_id": args.runtime_id,
        "manifest_type": "per_file_sha256",
        "verified_file_count": len(files),
        "verified_total_bytes": sum(int(item["size"]) for item in files),
        "weight_file": weight_files[0],
    }
    if args.write_manifest:
        (args.model_dir / "photoai-model-manifest.json").write_text(
            json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    print(json.dumps({key: output[key] for key in ("model_id", "model_revision", "model_artifact_sha256", "runtime_id", "manifest_type", "verified_file_count", "verified_total_bytes")}, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
