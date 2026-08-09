from __future__ import annotations

import hashlib
import hmac
import json
import os
import secrets
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

MODEL_ROOT = Path(os.environ.get(
    "PHOTOAI_MODEL_DIR",
    r"E:\AI_Tools\Other\LocalLLM\Qwen3-VL-2B-Instruct\89644892e4d85e24eaac8bacfd4f463576704203",
))
MAX_IMAGE_BYTES = 15 * 1024 * 1024
OPAQUE_ID = lambda value: isinstance(value, str) and bool(value) and len(value) <= 128 and all(
    char.isalnum() or char in "_-" for char in value
)
FIELDS = (
    "scene", "background_story", "lighting", "composition", "subject_intent",
    "emotion", "pose_template", "camera_position", "director_prompt",
)
SUMMARY_FIELDS = (
    "common_scene_direction", "common_lighting_direction", "common_composition_direction",
    "common_subject_direction", "differences", "strongest_references",
    "recommended_primary_reference", "photographer_action_summary",
)


class ModelUnavailable(RuntimeError):
    pass


@dataclass(frozen=True)
class ModelManifest:
    model_id: str
    model_revision: str
    model_artifact_sha256: str
    runtime_id: str

    @classmethod
    def load(cls, model_root: Path) -> "ModelManifest":
        manifest_path = model_root / "photoai-model-manifest.json"
        if not model_root.is_dir() or not manifest_path.is_file():
            raise ModelUnavailable("MODEL_MANIFEST_MISSING")
        try:
            data = json.loads(manifest_path.read_text(encoding="utf-8"))
            values = {key: str(data[key]).strip() for key in (
                "model_id", "model_revision", "model_artifact_sha256", "runtime_id"
            )}
        except (OSError, ValueError, KeyError, TypeError) as exc:
            raise ModelUnavailable("MODEL_MANIFEST_INVALID") from exc
        if len(values["model_artifact_sha256"]) != 64 or any(
            char not in "0123456789abcdefABCDEF" for char in values["model_artifact_sha256"]
        ):
            raise ModelUnavailable("MODEL_ARTIFACT_HASH_INVALID")
        if not (model_root / "config.json").is_file() or not (model_root / "preprocessor_config.json").is_file():
            raise ModelUnavailable("MODEL_CONFIG_MISSING")
        if not list(model_root.glob("*.safetensors")) and not list(model_root.glob("*.bin")):
            raise ModelUnavailable("MODEL_WEIGHTS_MISSING")
        try:
            config = json.loads((model_root / "config.json").read_text(encoding="utf-8"))
        except (OSError, ValueError) as exc:
            raise ModelUnavailable("MODEL_CONFIG_INVALID") from exc
        if config.get("model_type") != "qwen3_vl" or "Qwen3VLForConditionalGeneration" not in config.get("architectures", []):
            raise ModelUnavailable("MODEL_CONFIG_INVALID")
        return cls(**values)


class PairingAuthority:
    def __init__(self, pairing_code: str | None = None, access_token: str | None = None):
        self._pairing_code = pairing_code or secrets.token_urlsafe(18)
        self._access_token = access_token or secrets.token_urlsafe(32)
        self._consumed = False

    @property
    def pairing_code_for_console(self) -> str:
        return self._pairing_code

    def pair(self, supplied_code: str) -> str | None:
        if self._consumed or not hmac.compare_digest(self._pairing_code, supplied_code):
            return None
        self._consumed = True
        return self._access_token

    def is_valid(self, supplied_token: str | None) -> bool:
        return bool(supplied_token) and hmac.compare_digest(self._access_token, supplied_token)


class Qwen3VlRuntimeAdapter:
    def __init__(self, model_root: Path, device: str = "cuda:0", max_pixels: int = 1280 * 28 * 28):
        self.model_root = model_root
        self.manifest = ModelManifest.load(model_root)
        self.device = device
        self.max_pixels = max_pixels
        self._processor: Any | None = None
        self._model: Any | None = None
        self._lock = threading.Lock()

    @property
    def ready(self) -> bool:
        return self._processor is not None and self._model is not None

    def model_identity(self) -> dict[str, str]:
        return {
            "model_id": self.manifest.model_id,
            "model_revision": self.manifest.model_revision,
            "model_artifact_sha256": self.manifest.model_artifact_sha256,
            "runtime_id": self.manifest.runtime_id,
        }

    def load_model(self) -> None:
        if self.ready:
            return
        try:
            import torch
            from transformers import AutoProcessor, Qwen3VLForConditionalGeneration
        except ImportError as exc:
            raise ModelUnavailable("MODEL_RUNTIME_DEPENDENCY_MISSING") from exc
        if not torch.cuda.is_available():
            raise ModelUnavailable("CUDA_UNAVAILABLE")
        try:
            processor = AutoProcessor.from_pretrained(
                str(self.model_root), local_files_only=True, trust_remote_code=False,
                min_pixels=65536, max_pixels=self.max_pixels,
            )
            model = Qwen3VLForConditionalGeneration.from_pretrained(
                str(self.model_root), local_files_only=True, trust_remote_code=False,
                dtype=torch.bfloat16,
            )
            model.to(self.device)
            model.eval()
        except torch.cuda.OutOfMemoryError as exc:
            raise ModelUnavailable("CUDA_OOM") from exc
        except Exception as exc:
            raise ModelUnavailable("MODEL_LOAD_FAILED") from exc
        self._processor = processor
        self._model = model

    def unload_model(self) -> None:
        self._processor = None
        self._model = None
        try:
            import torch
            if torch.cuda.is_available():
                torch.cuda.empty_cache()
        except ImportError:
            pass

    def health_check(self) -> dict[str, Any]:
        return {"model_ready": self.ready, **self.model_identity(), "device": self.device}

    def analyze_image(self, reference_id: str, image_bytes: bytes) -> tuple[dict[str, Any], dict[str, Any]]:
        from io import BytesIO
        from PIL import Image

        try:
            image = Image.open(BytesIO(image_bytes))
            image.verify()
            image = Image.open(BytesIO(image_bytes)).convert("RGB")
        except Exception as exc:
            raise ValueError("IMAGE_DECODE_FAILED") from exc
        prompt = (
            "Analyze this photograph for a photographer. Return one compact JSON object only; "
            "do not use Markdown, commentary, or long lists. "
            "Return JSON only with keys "
            "bundle, confidence_summary, uncertainty_flags, warnings. The exact reference_id is "
            f"{reference_id}. bundle must contain "
            "reference_id and exactly the fields scene, background_story, lighting, composition, "
            "subject_intent, emotion, pose_template, camera_position, director_prompt, version. "
            "Keep every bundle string under 160 characters and use one concise sentence per field. "
            "confidence_summary must be an object with exactly three array keys "
            "direct_observation_fields, photographic_interpretation_fields, creative_recommendation_fields; "
            "each array may contain only these field-name tokens: scene, background_story, lighting, composition, "
            "subject_intent, emotion, pose_template, camera_position, director_prompt. "
            "uncertainty_flags must be an object with one entry for every bundle field except reference_id and version; "
            "each entry has level LOW/MEDIUM/HIGH/NOT_ASSESSED and basis "
            "DIRECT_OBSERVATION/PHOTOGRAPHIC_INTERPRETATION/CREATIVE_RECOMMENDATION/INSUFFICIENT_EVIDENCE. "
            "warnings must be an array of at most three short strings. "
            "Separate direct observation, photographic interpretation and creative recommendation; "
            "do not invent identity, location or unsupported facts. Mark uncertainty when evidence is insufficient."
        )
        messages = [{"role": "user", "content": [{"type": "image", "image": image}, {"type": "text", "text": prompt}]}]
        with self._lock:
            self.load_model()
            started = time.perf_counter()
            raw, has_pixels = self._generate(messages, max_new_tokens=768)
            repairs = 0
            try:
                payload = self._validated_photo_payload(raw, reference_id)
            except (ValueError, KeyError, TypeError, json.JSONDecodeError):
                repairs = 1
                raw, _ = self._generate([{"role": "user", "content": [{"type": "text", "text": _repair_prompt(raw, "photo", reference_id)}]}], max_new_tokens=768)
                payload = self._validated_photo_payload(raw, reference_id)
            return payload, _metrics(started, repairs, has_pixels)

    def summarize_ready_bundles(self, ready_items: list[dict[str, Any]]) -> tuple[dict[str, Any], dict[str, Any]]:
        if not ready_items:
            raise ValueError("OUTPUT_EMPTY")
        serialized = json.dumps(ready_items, ensure_ascii=False, sort_keys=True)
        prompt = (
            "Synthesize only these already-validated READY photo bundles for a photographer. Return one compact "
            "JSON object only; do not use Markdown, commentary, or repeat the input bundles. "
            "Return JSON only with keys common_scene_direction, common_lighting_direction, "
            "common_composition_direction, common_subject_direction, differences, strongest_references, "
            "recommended_primary_reference, photographer_action_summary. "
            "Keep each direction and action string under 240 characters, differences to at most 5 short strings, "
            "strongest_references to at most 3 objects with concise reference_id and reason, and use no other keys. "
            "recommended_primary_reference must be one of the supplied reference_id values. "
            "Do not add facts not present in the bundles.\nREADY_BUNDLES=" + serialized
        )
        with self._lock:
            self.load_model()
            started = time.perf_counter()
            raw, has_pixels = self._generate([{"role": "user", "content": [{"type": "text", "text": prompt}]}], max_new_tokens=1024)
            repairs = 0
            try:
                payload = self._validated_summary(raw, {str(item["reference_id"]) for item in ready_items})
            except (ValueError, KeyError, TypeError, json.JSONDecodeError):
                repairs = 1
                raw, _ = self._generate([{"role": "user", "content": [{"type": "text", "text": _repair_prompt(raw, "summary")}]}], max_new_tokens=1024)
                payload = self._validated_summary(raw, {str(item["reference_id"]) for item in ready_items})
            return payload, _metrics(started, repairs, has_pixels)

    def _generate(self, messages: list[dict[str, Any]], max_new_tokens: int) -> tuple[str, bool]:
        if self._processor is None or self._model is None:
            raise ModelUnavailable("MODEL_NOT_LOADED")
        try:
            import torch
            inputs = self._processor.apply_chat_template(
                messages, add_generation_prompt=True, tokenize=True, return_dict=True, return_tensors="pt"
            ).to(self.device)
            has_pixels = "pixel_values" in inputs
            with torch.inference_mode():
                outputs = self._model.generate(**inputs, max_new_tokens=max_new_tokens, do_sample=False)
            input_ids = inputs.get("input_ids")
            generated = outputs[0][input_ids.shape[-1]:] if input_ids is not None else outputs[0]
            return self._processor.decode(generated, skip_special_tokens=True), has_pixels
        except torch.cuda.OutOfMemoryError as exc:
            raise ModelUnavailable("CUDA_OOM") from exc
        except Exception as exc:
            raise RuntimeError("MODEL_INFERENCE_FAILED") from exc

    def _validated_photo_payload(self, raw: str, reference_id: str) -> dict[str, Any]:
        payload = _canonicalize_photo_payload(_parse_json_object(raw), reference_id)
        if not _valid_bundle(payload.get("bundle"), reference_id) or not _valid_confidence(payload.get("confidence_summary")) or not _valid_uncertainty(payload.get("uncertainty_flags")):
            raise ValueError("OUTPUT_SCHEMA_INVALID")
        return payload

    def _validated_summary(self, raw: str, ready_ids: set[str]) -> dict[str, Any]:
        payload = _parse_json_object(raw)
        if not _valid_summary(payload, ready_ids):
            raise ValueError("OUTPUT_SCHEMA_INVALID")
        return payload


def _parse_json_object(text: str) -> dict[str, Any]:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.strip("`").removeprefix("json").strip()
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("PROVIDER_OUTPUT_MALFORMED")
    value = json.loads(cleaned[start:end + 1])
    if not isinstance(value, dict):
        raise ValueError("PROVIDER_OUTPUT_SCHEMA_INVALID")
    return value


def _valid_bundle(bundle: Any, reference_id: str) -> bool:
    return isinstance(bundle, dict) and set(bundle) == {"reference_id", *FIELDS, "version"} and \
        bundle.get("reference_id") == reference_id and all(
            isinstance(bundle.get(field), str) and bool(bundle[field].strip()) and len(bundle[field]) <= 240
            for field in ("reference_id", *FIELDS, "version")
        )


def _canonicalize_photo_payload(payload: dict[str, Any], reference_id: str) -> dict[str, Any]:
    """Bind model output to the requested opaque ID and reduce loose JSON to the wire schema."""
    bundle = payload.get("bundle")
    if isinstance(bundle, dict):
        bundle = dict(bundle)
        bundle["reference_id"] = reference_id
        payload["bundle"] = bundle
    confidence = payload.get("confidence_summary")
    if isinstance(confidence, dict):
        payload["confidence_summary"] = {
            name: [item for item in confidence.get(name, []) if item in FIELDS]
            if isinstance(confidence.get(name), list) else []
            for name in ("direct_observation_fields", "photographic_interpretation_fields", "creative_recommendation_fields")
        }
    uncertainty = payload.get("uncertainty_flags")
    normalized_uncertainty: dict[str, dict[str, str]] = {}
    for field in FIELDS:
        value = uncertainty.get(field) if isinstance(uncertainty, dict) else None
        if isinstance(value, dict):
            level = value.get("level", "NOT_ASSESSED")
            basis = value.get("basis", "INSUFFICIENT_EVIDENCE")
        else:
            level = value if isinstance(value, str) else "NOT_ASSESSED"
            basis = "INSUFFICIENT_EVIDENCE"
        normalized_uncertainty[field] = {
            "level": level if level in {"LOW", "MEDIUM", "HIGH", "NOT_ASSESSED"} else "NOT_ASSESSED",
            "basis": basis if basis in {"DIRECT_OBSERVATION", "PHOTOGRAPHIC_INTERPRETATION", "CREATIVE_RECOMMENDATION", "INSUFFICIENT_EVIDENCE"} else "INSUFFICIENT_EVIDENCE",
        }
    payload["uncertainty_flags"] = normalized_uncertainty
    warnings = payload.get("warnings")
    payload["warnings"] = warnings if isinstance(warnings, list) and all(isinstance(item, str) for item in warnings) else []
    return payload


def _valid_confidence(value: Any) -> bool:
    if not isinstance(value, dict) or set(value) != {
        "direct_observation_fields", "photographic_interpretation_fields", "creative_recommendation_fields"
    }:
        return False
    return all(isinstance(value[name], list) and all(item in FIELDS for item in value[name]) for name in value)


def _valid_uncertainty(value: Any) -> bool:
    if not isinstance(value, dict) or set(value) != set(FIELDS):
        return False
    return all(
        isinstance(item, dict) and set(item) == {"level", "basis"} and
        item["level"] in {"LOW", "MEDIUM", "HIGH", "NOT_ASSESSED"} and
        item["basis"] in {"DIRECT_OBSERVATION", "PHOTOGRAPHIC_INTERPRETATION", "CREATIVE_RECOMMENDATION", "INSUFFICIENT_EVIDENCE"}
        for item in value.values()
    )


def _valid_summary(value: Any, ready_ids: set[str]) -> bool:
    if not isinstance(value, dict) or set(value) != set(SUMMARY_FIELDS):
        return False
    text_fields = (
        "common_scene_direction", "common_lighting_direction", "common_composition_direction",
        "common_subject_direction", "photographer_action_summary",
    )
    if any(not isinstance(value[field], str) or not value[field].strip() or len(value[field]) > 480 for field in text_fields):
        return False
    if not isinstance(value["differences"], list) or len(value["differences"]) > 20 or any(
        not isinstance(item, str) or not item.strip() or len(item) > 480 for item in value["differences"]
    ):
        return False
    strongest = value["strongest_references"]
    if not isinstance(strongest, list) or len(strongest) > 3 or any(
        not isinstance(item, dict) or set(item) != {"reference_id", "reason"} or
        item["reference_id"] not in ready_ids or not isinstance(item["reason"], str) or not item["reason"].strip()
        for item in strongest
    ):
        return False
    return value["recommended_primary_reference"] in ready_ids


def _repair_prompt(raw: str, kind: str, reference_id: str | None = None) -> str:
    expected = "photo Bundle envelope" if kind == "photo" else "project summary"
    identity = f" The bundle reference_id must be exactly {reference_id}." if reference_id else ""
    return (
        f"Repair the following model output into one compact valid {expected}. Return JSON only, no Markdown, "
        f"without inventing facts. If the text is truncated, close the object and use concise values already present.{identity} "
        "For a photo envelope, keep every bundle string under 160 characters; confidence_summary arrays contain "
        "only field-name tokens; uncertainty_flags contain one level/basis object for each bundle field; "
        "warnings must be at most three short strings. For a project summary, keep direction/action strings under "
        "240 characters, differences to at most 5 short strings, strongest_references to at most 3 objects, and "
        "recommended_primary_reference must be one supplied ID.\nRAW_OUTPUT=" + raw[:12000]
    )


def _metrics(started: float, json_repairs: int, has_pixels: bool) -> dict[str, Any]:
    peak_vram = 0
    try:
        import torch
        if torch.cuda.is_available():
            peak_vram = int(torch.cuda.max_memory_allocated())
            torch.cuda.reset_peak_memory_stats()
    except ImportError:
        pass
    return {
        "inference_ms": int((time.perf_counter() - started) * 1000),
        "peak_vram_bytes": peak_vram,
        "pixel_values_present": has_pixels,
        "json_repair_count": json_repairs,
    }


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def _opaque_id() -> str:
    return secrets.token_urlsafe(18).replace("-", "_")


def _base_envelope(reference_id: str, status: str, **values: Any) -> dict[str, Any]:
    now = _utc_now()
    return {
        "envelope_version": "1.0", "request_id": _opaque_id(), "reference_id": reference_id,
        "provider_id": values.get("provider_id", "local-qwen3-vl"), "provider_type": "LOCAL_SERVICE",
        "model_id": values.get("model_id"), "model_revision": values.get("model_revision"),
        "model_artifact_sha256": values.get("model_artifact_sha256"), "runtime_id": values.get("runtime_id"),
        "started_at_utc": values.get("started_at_utc", now), "completed_at_utc": values.get("completed_at_utc", now),
        "latency_ms": int(values.get("latency_ms", 0)), "status": status,
        "output_schema_version": values.get("output_schema_version"), "bundle": values.get("bundle"),
        "confidence_summary": values.get("confidence_summary", {"direct_observation_fields": [], "photographic_interpretation_fields": [], "creative_recommendation_fields": []}),
        "uncertainty_flags": values.get("uncertainty_flags", {field: {"level": "NOT_ASSESSED", "basis": "INSUFFICIENT_EVIDENCE"} for field in FIELDS}),
        "warnings": values.get("warnings", []),
        "provenance": values.get("provenance", {
            "result_origin": "UNAVAILABLE", "producer_release": None, "input_media_retained": False,
            "raw_image_available": False, "direct_observation_fields": [],
            "photographic_interpretation_fields": [], "creative_recommendation_fields": [],
        }),
        "safety_flags": values.get("safety_flags", ["NO_SAFETY_FLAGS"]),
        "retryable": bool(values.get("retryable", False)), "error": values.get("error"),
    }


def _failure(reference_id: str, code: str, message: str, retryable: bool, action: str) -> dict[str, Any]:
    return _base_envelope(
        reference_id, "FAILED", retryable=retryable,
        safety_flags=["UNSUPPORTED_INPUT"] if code.startswith("IMAGE_") else ["NO_SAFETY_FLAGS"],
        error={"code": code, "user_message": message, "preserve_reference": True,
               "allow_safe_event_log": True, "allow_diagnostic_upload": False, "product_action": action},
    )


def _error_message(code: str) -> tuple[str, bool, str]:
    if code in {"MODEL_NOT_FOUND", "MODEL_CONFIG_MISSING", "MODEL_CONFIG_INVALID", "MODEL_MANIFEST_MISSING", "MODEL_MANIFEST_INVALID", "MODEL_WEIGHTS_MISSING"}:
        return "本机分析模型尚未就绪。", False, "SHOW_UNAVAILABLE"
    if code in {"CUDA_UNAVAILABLE", "CUDA_OOM", "MODEL_LOAD_FAILED", "MODEL_RUNTIME_DEPENDENCY_MISSING"}:
        return "本机分析运行时不可用。", code == "CUDA_OOM", "SHOW_UNAVAILABLE"
    if code in {"IMAGE_DECODE_FAILED", "IMAGE_UNSUPPORTED", "IMAGE_TOO_LARGE"}:
        return "照片无法安全解码。", False, "RESELECT"
    if code in {"OUTPUT_EMPTY", "OUTPUT_JSON_INVALID", "OUTPUT_SCHEMA_INVALID", "PROVIDER_OUTPUT_SCHEMA_INVALID"}:
        return "本机分析结果无法安全使用。", False, "SHOW_UNAVAILABLE"
    return "本机分析暂时失败，可稍后重试。", True, "OFFER_RETRY"


def _summary_request(data: Any) -> tuple[str, list[dict[str, Any]], int]:
    if not isinstance(data, dict):
        raise ValueError("SUMMARY_INPUT_INVALID")
    project_id = str(data.get("project_id", ""))
    ready_items = data.get("ready_items")
    failed_count = data.get("failed_count", 0)
    if not OPAQUE_ID(project_id) or not isinstance(ready_items, list) or not 1 <= len(ready_items) <= 20:
        raise ValueError("SUMMARY_INPUT_INVALID")
    if not isinstance(failed_count, int) or failed_count < 0:
        raise ValueError("SUMMARY_INPUT_INVALID")
    normalized: list[dict[str, Any]] = []
    for item in ready_items:
        if not isinstance(item, dict) or not OPAQUE_ID(str(item.get("reference_id", ""))) or not _valid_bundle(item.get("bundle"), str(item["reference_id"])):
            raise ValueError("SUMMARY_INPUT_INVALID")
        normalized.append({"reference_id": str(item["reference_id"]), "bundle": item["bundle"]})
    return project_id, normalized, failed_count


def create_app(
    model_root: Path = MODEL_ROOT,
    pairing: PairingAuthority | None = None,
    runtime_override: Qwen3VlRuntimeAdapter | None = None,
) -> FastAPI:
    app = FastAPI(title="PhotoAI Local Analysis Service", docs_url=None, redoc_url=None)
    authority = pairing or PairingAuthority(os.environ.get("PHOTOAI_PAIRING_CODE"))
    runtime: Qwen3VlRuntimeAdapter | None = runtime_override
    runtime_error: str | None = None
    if runtime is None:
        try:
            runtime = Qwen3VlRuntimeAdapter(model_root)
        except ModelUnavailable as exc:
            runtime_error = str(exc)

    @app.get("/v1/healthz")
    async def healthz() -> dict[str, Any]:
        details = runtime.health_check() if runtime is not None else {}
        return {"service_version": "1.0", "provider_type": "LOCAL_SERVICE", "model_ready": runtime is not None and runtime.ready,
                "retention": "NONE", "runtime_error": runtime_error, **details}

    @app.post("/v1/pair")
    async def pair(request: Request) -> dict[str, Any]:
        try:
            data = await request.json()
            code = str(data.get("pairing_code", ""))
        except Exception as exc:
            raise HTTPException(status_code=400, detail="PAIRING_CODE_INVALID") from exc
        token = authority.pair(code)
        if token is None:
            raise HTTPException(status_code=403, detail="PAIRING_REJECTED")
        return {"access_token": token, "provider_type": "LOCAL_SERVICE", "retention": "NONE"}

    @app.put("/v1/analyze/{reference_id}")
    async def analyze(reference_id: str, request: Request) -> JSONResponse:
        token = request.headers.get("authorization", "").removeprefix("Bearer ").strip()
        if not OPAQUE_ID(reference_id) or not authority.is_valid(token):
            raise HTTPException(status_code=403, detail="ANALYSIS_AUTH_REJECTED")
        content_length = request.headers.get("content-length")
        if content_length and (not content_length.isdigit() or int(content_length) > MAX_IMAGE_BYTES):
            return JSONResponse(_failure(reference_id, "IMAGE_TOO_LARGE", "照片尺寸超过本机分析上限。", False, "RESELECT"), status_code=413)
        image_bytes = await request.body()
        if not image_bytes or len(image_bytes) > MAX_IMAGE_BYTES or not image_bytes.startswith(b"\xff\xd8\xff"):
            return JSONResponse(_failure(reference_id, "IMAGE_UNSUPPORTED", "仅支持已净化的 JPEG。", False, "RESELECT"), status_code=415)
        if runtime is None or runtime_error is not None:
            return JSONResponse(_failure(reference_id, "PROVIDER_NOT_CONFIGURED", "本机分析模型尚未就绪。", False, "SHOW_UNAVAILABLE"), status_code=503)
        started_at = _utc_now()
        try:
            payload, metrics = runtime.analyze_image(reference_id, image_bytes)
            manifest = runtime.manifest
            envelope = _base_envelope(
                reference_id, "SUCCESS", model_id=manifest.model_id, model_revision=manifest.model_revision,
                model_artifact_sha256=manifest.model_artifact_sha256, runtime_id=manifest.runtime_id,
                started_at_utc=started_at, completed_at_utc=_utc_now(), latency_ms=metrics["inference_ms"], output_schema_version="1.0",
                bundle=payload["bundle"], confidence_summary=payload["confidence_summary"],
                uncertainty_flags=payload["uncertainty_flags"], warnings=payload.get("warnings", []),
                provenance={"result_origin": "LOCAL_VLM", "producer_release": manifest.model_revision,
                            "input_media_retained": False, "raw_image_available": False,
                            "direct_observation_fields": payload["confidence_summary"]["direct_observation_fields"],
                            "photographic_interpretation_fields": payload["confidence_summary"]["photographic_interpretation_fields"],
                            "creative_recommendation_fields": payload["confidence_summary"]["creative_recommendation_fields"]},
            )
            envelope["metrics"] = metrics
            return JSONResponse(envelope)
        except ModelUnavailable as exc:
            code = str(exc)
            message, retryable, action = _error_message(code)
            return JSONResponse(_failure(reference_id, code, message, retryable, action), status_code=503)
        except ValueError as exc:
            code = str(exc)
            message, retryable, action = _error_message(code)
            return JSONResponse(_failure(reference_id, code, message, retryable, action), status_code=502)
        except Exception:
            return JSONResponse(_failure(reference_id, "MODEL_INFERENCE_FAILED", "本机分析暂时失败，可稍后重试。", True, "OFFER_RETRY"), status_code=502)

    @app.post("/v1/summarize")
    async def summarize(request: Request) -> JSONResponse:
        token = request.headers.get("authorization", "").removeprefix("Bearer ").strip()
        if not authority.is_valid(token):
            raise HTTPException(status_code=403, detail="SUMMARY_AUTH_REJECTED")
        try:
            project_id, ready_items, failed_count = _summary_request(await request.json())
        except (ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
            return JSONResponse({"status": "FAILED", "error": {"code": str(exc) or "SUMMARY_INPUT_INVALID"}}, status_code=400)
        if runtime is None or runtime_error is not None:
            return JSONResponse({"status": "FAILED", "project_id": project_id, "error": {"code": "PROVIDER_NOT_CONFIGURED"}}, status_code=503)
        try:
            payload, metrics = runtime.summarize_ready_bundles(ready_items)
            manifest = runtime.manifest
            return JSONResponse({
                "status": "SUCCESS", "project_id": project_id, "ready_count": len(ready_items), "failed_count": failed_count,
                "model_id": manifest.model_id, "model_revision": manifest.model_revision,
                "model_artifact_sha256": manifest.model_artifact_sha256, "runtime_id": manifest.runtime_id,
                "completed_at_utc": _utc_now(), "summary_version": "1.0", "summary": payload,
                "provenance": {"result_origin": "LOCAL_VLM", "input_media_retained": False, "raw_image_available": False},
                "metrics": metrics,
            })
        except ModelUnavailable as exc:
            code = str(exc)
            message, retryable, action = _error_message(code)
            return JSONResponse({"status": "FAILED", "project_id": project_id, "error": {"code": code, "user_message": message, "retryable": retryable, "product_action": action}}, status_code=503)
        except (ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
            return JSONResponse({"status": "FAILED", "project_id": project_id, "error": {"code": str(exc) or "OUTPUT_SCHEMA_INVALID", "retryable": False}}, status_code=502)
        except Exception:
            return JSONResponse({"status": "FAILED", "project_id": project_id, "error": {"code": "MODEL_INFERENCE_FAILED", "retryable": True}}, status_code=502)

    app.state.pairing_authority = authority
    app.state.runtime_error = runtime_error
    app.state.runtime = runtime
    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn
    certfile = os.environ.get("PHOTOAI_TLS_CERTFILE")
    keyfile = os.environ.get("PHOTOAI_TLS_KEYFILE")
    if not certfile or not keyfile:
        raise SystemExit("TLS_CERTIFICATE_REQUIRED")
    if app.state.runtime is None:
        raise SystemExit(app.state.runtime_error or "MODEL_NOT_FOUND")
    app.state.runtime.load_model()
    print("PAIRING_CODE=" + app.state.pairing_authority.pairing_code_for_console, flush=True)
    uvicorn.run(
        "local_analysis_service.service:app",
        host=os.environ.get("PHOTOAI_BIND_HOST", "127.0.0.1"),
        port=int(os.environ.get("PHOTOAI_PORT", "8443")),
        ssl_certfile=certfile,
        ssl_keyfile=keyfile,
        access_log=False,
    )
