#!/usr/bin/env python3
"""Offline semantic checks for the Phase 1.5 P0 contracts.

This module intentionally uses only the Python standard library. It never
downloads packages, contacts a provider, reads media, or loads a model.
"""
from __future__ import annotations

import argparse
import json
import re
from datetime import datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_POLICY_PATH = ROOT / "docs" / "phase1_5" / "error_policy.v1.json"
OPAQUE_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
WINDOWS_PATH = re.compile(r"(?i)(?:[a-z]:\\|\\\\)")
UNIX_PATH = re.compile(r"(?i)(?<![A-Za-z0-9_])/(?:users|home|private|data|storage|sdcard|var|tmp|etc)/")
PICKER_URI = re.compile(r"(?i)\b(?:content|file|android\.resource)://")
TOKEN_MARKER = re.compile(r"(?i)\b(?:bearer\s+\S+|sk-[A-Za-z0-9_-]+|(?:api[_ -]?key|authorization)\s*[:=])")
SERIAL_MARKER = re.compile(r"(?i)\b(?:device_)?serial(?:number)?\s*[:=]")
STACK_MARKER = re.compile(r"(?im)(?:traceback \(most recent call last\)|^\s*at\s+\S+\(|^\s*file \".+\", line \d+|\b(?:java|kotlin)\.[A-Za-z0-9_.]+(?:exception|error)\b)")
TEXT_FIELDS = (
    "scene", "background_story", "lighting", "composition", "subject_intent",
    "emotion", "pose_template", "camera_position", "director_prompt",
)
POLICY_FIELDS = (
    "retryable", "preserve_reference", "allow_safe_event_log",
    "allow_diagnostic_upload", "product_action", "cleanup_behavior",
    "demo_fallback_policy",
)


def _error(path: str, code: str, message: str) -> dict[str, str]:
    return {"path": path, "code": code, "message": message}


def _opaque_errors(value: Any, path: str) -> list[dict[str, str]]:
    if not isinstance(value, str) or not OPAQUE_ID.fullmatch(value):
        return [_error(path, "OPAQUE_ID_INVALID", "must use the v1 opaque-token syntax")]
    return []


def _plain_text_errors(value: Any, path: str) -> list[dict[str, str]]:
    if not isinstance(value, str) or not value or not value.strip():
        return [_error(path, "TEXT_BLANK", "must contain non-whitespace text")]
    if value != value.strip():
        return [_error(path, "TEXT_EDGE_WHITESPACE", "must not contain leading or trailing whitespace")]
    if "\r" in value or "\n" in value:
        return [_error(path, "TEXT_MULTILINE", "must be a single line")]
    return []


def _parse_aware_timestamp(value: Any, path: str) -> tuple[datetime | None, list[dict[str, str]]]:
    if not isinstance(value, str):
        return None, [_error(path, "TIMESTAMP_INVALID", "must be a timezone-aware date-time string")]
    try:
        parsed = datetime.fromisoformat(value[:-1] + "+00:00" if value.endswith("Z") else value)
    except ValueError:
        return None, [_error(path, "TIMESTAMP_INVALID", "must be a parseable timezone-aware date-time string")]
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        return None, [_error(path, "TIMESTAMP_NAIVE", "must include timezone information")]
    return parsed, []


def _message_errors(value: Any) -> list[dict[str, str]]:
    errors = _plain_text_errors(value, "error.user_message")
    if errors or not isinstance(value, str):
        return errors
    checks = (
        (STACK_MARKER, "ERROR_MESSAGE_STACK"),
        (WINDOWS_PATH, "ERROR_MESSAGE_LOCAL_PATH"),
        (UNIX_PATH, "ERROR_MESSAGE_LOCAL_PATH"),
        (PICKER_URI, "ERROR_MESSAGE_PICKER_URI"),
        (TOKEN_MARKER, "ERROR_MESSAGE_SECRET"),
        (SERIAL_MARKER, "ERROR_MESSAGE_DEVICE_SERIAL"),
    )
    return [_error("error.user_message", code, "contains prohibited diagnostic content") for pattern, code in checks if pattern.search(value)]


def validate_reference_bundle_semantics(bundle: Any) -> list[dict[str, str]]:
    """Return structured cross-field/normalization errors after schema validation."""
    if not isinstance(bundle, dict):
        return [_error("$", "BUNDLE_NOT_OBJECT", "must be an object")]
    errors = _opaque_errors(bundle.get("reference_id"), "reference_id")
    for field in TEXT_FIELDS:
        errors.extend(_plain_text_errors(bundle.get(field), field))
    return errors


def validate_time_order(envelope: Any) -> list[dict[str, str]]:
    """Validate audit ordering independently from monotonic provider latency."""
    if not isinstance(envelope, dict):
        return [_error("$", "ENVELOPE_NOT_OBJECT", "must be an object")]
    started, start_errors = _parse_aware_timestamp(envelope.get("started_at_utc"), "started_at_utc")
    completed, complete_errors = _parse_aware_timestamp(envelope.get("completed_at_utc"), "completed_at_utc")
    errors = start_errors + complete_errors
    if started is not None and completed is not None and completed < started:
        errors.append(_error("completed_at_utc", "TIME_ORDER_INVALID", "must not be earlier than started_at_utc"))
    latency = envelope.get("latency_ms")
    if isinstance(latency, bool) or not isinstance(latency, int) or latency < 0:
        errors.append(_error("latency_ms", "LATENCY_INVALID", "must be a non-negative monotonic-clock duration"))
    return errors


def validate_provider_envelope_semantics(envelope: Any) -> list[dict[str, str]]:
    """Return non-schema semantic errors for ProviderAnalysisEnvelope v1."""
    if not isinstance(envelope, dict):
        return [_error("$", "ENVELOPE_NOT_OBJECT", "must be an object")]
    errors = _opaque_errors(envelope.get("request_id"), "request_id")
    errors.extend(_opaque_errors(envelope.get("reference_id"), "reference_id"))
    errors.extend(validate_time_order(envelope))

    status = envelope.get("status")
    bundle = envelope.get("bundle")
    error = envelope.get("error")
    if status == "SUCCESS":
        for field in ("model_id", "model_revision", "runtime_id"):
            errors.extend(_plain_text_errors(envelope.get(field), field))
        artifact = envelope.get("model_artifact_sha256")
        if not isinstance(artifact, str) or not re.fullmatch(r"[A-Fa-f0-9]{64}", artifact):
            errors.append(_error("model_artifact_sha256", "SUCCESS_MODEL_HASH_INVALID", "SUCCESS requires a 64-hex artifact hash"))
        if envelope.get("output_schema_version") != "1.0":
            errors.append(_error("output_schema_version", "SUCCESS_OUTPUT_VERSION_INVALID", "SUCCESS requires output schema version 1.0"))
        if envelope.get("retryable") is not False:
            errors.append(_error("retryable", "SUCCESS_RETRYABLE_INVALID", "SUCCESS must be non-retryable"))
        if error is not None:
            errors.append(_error("error", "SUCCESS_ERROR_PRESENT", "SUCCESS must not include an error object"))
        if not isinstance(bundle, dict):
            errors.append(_error("bundle", "SUCCESS_BUNDLE_MISSING", "SUCCESS requires a bundle"))
        else:
            for item in validate_reference_bundle_semantics(bundle):
                errors.append({**item, "path": f"bundle.{item['path']}"})
            if bundle.get("reference_id") != envelope.get("reference_id"):
                errors.append(_error("bundle.reference_id", "REFERENCE_ID_MISMATCH", "must equal envelope.reference_id"))
        if isinstance(envelope.get("provenance"), dict) and envelope["provenance"].get("result_origin") == "UNAVAILABLE":
            errors.append(_error("provenance.result_origin", "SUCCESS_UNAVAILABLE_PROVENANCE", "SUCCESS cannot have UNAVAILABLE provenance"))
    elif status == "FAILED":
        if bundle is not None:
            errors.append(_error("bundle", "FAILED_BUNDLE_PRESENT", "FAILED must not include a bundle"))
        if envelope.get("output_schema_version") is not None:
            errors.append(_error("output_schema_version", "FAILED_OUTPUT_VERSION_PRESENT", "FAILED must not include an output schema version"))
        if not isinstance(error, dict):
            errors.append(_error("error", "FAILED_ERROR_MISSING", "FAILED requires an error object"))
        elif error.get("code") == "USER_CANCELLED":
            errors.append(_error("error.code", "FAILED_CANCEL_CODE", "USER_CANCELLED is reserved for CANCELLED"))
    elif status == "CANCELLED":
        if bundle is not None:
            errors.append(_error("bundle", "CANCELLED_BUNDLE_PRESENT", "CANCELLED must not include a bundle"))
        if envelope.get("output_schema_version") is not None:
            errors.append(_error("output_schema_version", "CANCELLED_OUTPUT_VERSION_PRESENT", "CANCELLED must not include an output schema version"))
        if envelope.get("retryable") is not False:
            errors.append(_error("retryable", "CANCELLED_RETRYABLE_INVALID", "CANCELLED must not retry"))
        if not isinstance(error, dict):
            errors.append(_error("error", "CANCELLED_ERROR_MISSING", "CANCELLED requires USER_CANCELLED"))
        elif error.get("code") != "USER_CANCELLED":
            errors.append(_error("error.code", "CANCELLED_CODE_INVALID", "CANCELLED requires USER_CANCELLED"))
    else:
        errors.append(_error("status", "STATUS_INVALID", "must be a terminal v1 status"))

    if isinstance(error, dict):
        errors.extend(_message_errors(error.get("user_message")))
        if error.get("allow_diagnostic_upload") is not False:
            errors.append(_error("error.allow_diagnostic_upload", "DIAGNOSTIC_UPLOAD_FORBIDDEN", "must always be false"))
    return errors


def _policy_index(policy: Any) -> tuple[dict[str, dict[str, Any]], list[dict[str, str]]]:
    if not isinstance(policy, dict) or policy.get("version") != "1.0" or not isinstance(policy.get("policies"), list):
        return {}, [_error("policy", "POLICY_DOCUMENT_INVALID", "must be a v1 policy document")]
    index: dict[str, dict[str, Any]] = {}
    errors: list[dict[str, str]] = []
    for position, entry in enumerate(policy["policies"]):
        path = f"policy.policies[{position}]"
        if not isinstance(entry, dict) or not isinstance(entry.get("code"), str):
            errors.append(_error(path, "POLICY_ENTRY_INVALID", "must contain a code"))
            continue
        code = entry["code"]
        if code in index:
            errors.append(_error(path, "POLICY_CODE_DUPLICATE", "must be unique"))
            continue
        missing = [field for field in POLICY_FIELDS if field not in entry]
        if missing:
            errors.append(_error(path, "POLICY_ENTRY_INCOMPLETE", f"missing {', '.join(missing)}"))
        index[code] = entry
    return index, errors


def validate_error_policy(envelope: Any, policy: Any) -> list[dict[str, str]]:
    """Validate an error envelope against the sole machine-readable policy mapping."""
    index, errors = _policy_index(policy)
    if not isinstance(envelope, dict) or not isinstance(envelope.get("error"), dict):
        return errors
    error = envelope["error"]
    code = error.get("code")
    expected = index.get(code)
    if expected is None:
        return errors + [_error("error.code", "POLICY_CODE_UNKNOWN", "is absent from error_policy.v1.json")]
    for field in ("retryable", "preserve_reference", "allow_safe_event_log", "allow_diagnostic_upload", "product_action"):
        actual = envelope.get(field) if field == "retryable" else error.get(field)
        if actual != expected.get(field):
            errors.append(_error(f"error.{field}" if field != "retryable" else field, "POLICY_MISMATCH", f"must equal policy for {code}"))
    if expected.get("demo_fallback_policy") != "USER_EXPLICIT_OUT_OF_ENVELOPE_ONLY":
        errors.append(_error("policy.demo_fallback_policy", "DEMO_FALLBACK_POLICY_INVALID", "must keep fallback outside provider envelopes"))
    if error.get("product_action") == "EXPLICIT_DEMO_FALLBACK":
        errors.append(_error("error.product_action", "DEMO_FALLBACK_NOT_PROVIDER_ACTION", "must require a separate explicit user action outside the provider envelope"))
    return errors


def load_error_policy(path: Path = DEFAULT_POLICY_PATH) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def _load_json(path: Path) -> Any:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Offline Phase 1.5 P0 semantic validator")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--bundle", type=Path)
    group.add_argument("--envelope", type=Path)
    parser.add_argument("--error-policy", type=Path, default=DEFAULT_POLICY_PATH)
    args = parser.parse_args(argv)
    policy = load_error_policy(args.error_policy)
    if args.bundle:
        errors = validate_reference_bundle_semantics(_load_json(args.bundle))
    else:
        envelope = _load_json(args.envelope)
        errors = validate_provider_envelope_semantics(envelope) + validate_error_policy(envelope, policy)
    print(json.dumps({"valid": not errors, "errors": errors}, ensure_ascii=False, sort_keys=True))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
