#!/usr/bin/env python3
"""Offline Phase 1.5 P0 schema and boundary checks; no image, model, or network access."""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[1]
REF_DIR = ROOT / "docs" / "reference"
FIELDS = {"scene", "background_story", "lighting", "composition", "subject_intent", "emotion", "pose_template", "camera_position", "director_prompt"}
ERROR_CODES = {
    "USER_CANCELLED", "REFERENCE_URI_UNAVAILABLE", "REFERENCE_PERMISSION_EXPIRED", "IMAGE_DECODE_FAILED", "IMAGE_UNSUPPORTED", "IMAGE_TOO_LARGE", "IMAGE_PRIVACY_BLOCKED", "PROVIDER_NOT_CONFIGURED", "PROVIDER_UNAVAILABLE", "PROVIDER_TIMEOUT", "PROVIDER_OOM", "PROVIDER_OUTPUT_EMPTY", "PROVIDER_OUTPUT_MALFORMED", "PROVIDER_OUTPUT_SCHEMA_INVALID", "PROVIDER_SAFETY_REJECTED", "PROVIDER_LICENSE_BLOCKED", "PIPELINE_BUNDLE_INCOMPATIBLE", "UNKNOWN_FAILURE",
}
RETRYABLE = {"USER_CANCELLED": False, "REFERENCE_URI_UNAVAILABLE": False, "REFERENCE_PERMISSION_EXPIRED": False, "IMAGE_DECODE_FAILED": False, "IMAGE_UNSUPPORTED": False, "IMAGE_TOO_LARGE": False, "IMAGE_PRIVACY_BLOCKED": False, "PROVIDER_NOT_CONFIGURED": False, "PROVIDER_UNAVAILABLE": True, "PROVIDER_TIMEOUT": True, "PROVIDER_OOM": True, "PROVIDER_OUTPUT_EMPTY": True, "PROVIDER_OUTPUT_MALFORMED": True, "PROVIDER_OUTPUT_SCHEMA_INVALID": True, "PROVIDER_SAFETY_REJECTED": False, "PROVIDER_LICENSE_BLOCKED": False, "PIPELINE_BUNDLE_INCOMPATIBLE": False, "UNKNOWN_FAILURE": False}


def load(relative: str) -> dict:
    with (ROOT / relative).open(encoding="utf-8") as handle:
        return json.load(handle)


reference_schema = load("docs/reference/reference_bundle.v1.schema.json")
alias_schema = load("docs/reference/reference_bundle.schema.json")
envelope_schema = load("docs/reference/provider_analysis_envelope.v1.schema.json")
corpus_schema = load("docs/phase1_5/schemas/vlm_evaluation_corpus_manifest.v1.schema.json")
registry = Registry().with_resources(
    (schema["$id"], Resource.from_contents(schema))
    for schema in (reference_schema, alias_schema, envelope_schema, corpus_schema)
)
format_checker = FormatChecker()


def valid(instance: object, schema: dict, name: str) -> None:
    errors = sorted(Draft202012Validator(schema, registry=registry, format_checker=format_checker).iter_errors(instance), key=str)
    if errors:
        raise AssertionError(f"{name} unexpectedly invalid: {errors[0].message}")


def invalid(instance: object, schema: dict, name: str) -> None:
    errors = list(Draft202012Validator(schema, registry=registry, format_checker=format_checker).iter_errors(instance))
    if not errors:
        raise AssertionError(f"{name} unexpectedly valid")


def check(name: str, assertion: bool) -> None:
    if not assertion:
        raise AssertionError(name)
    print(f"PASS {name}")


def main() -> int:
    bundle = load("docs/reference/fixtures/reference_bundle_demo.json")
    success = load("docs/reference/fixtures/provider_analysis_envelope_demo.json")
    failure = load("docs/reference/fixtures/provider_analysis_error_demo.json")
    corpus = load("docs/phase1_5/fixtures/vlm_evaluation_corpus_manifest.v1.json")

    valid(bundle, reference_schema, "existing demo bundle")
    valid(bundle, alias_schema, "compatibility alias")
    valid(success, envelope_schema, "success fixture")
    valid(failure, envelope_schema, "error fixture")
    valid(corpus, corpus_schema, "planned non-private corpus manifest")
    print("PASS valid fixtures and existing Demo compatibility")

    absent = copy.deepcopy(bundle); del absent["scene"]
    invalid(absent, reference_schema, "missing required field")
    long_text = copy.deepcopy(bundle); long_text["director_prompt"] = "x" * 721
    invalid(long_text, reference_schema, "maximum text length")
    unknown_version = copy.deepcopy(bundle); unknown_version["version"] = "2.0"
    invalid(unknown_version, reference_schema, "unknown bundle version")
    malformed = copy.deepcopy(bundle); malformed["scene"] = "line one\nline two"
    invalid(malformed, reference_schema, "malformed bundle")
    print("PASS ReferenceBundle required, bounded, single-line, and fail-closed version checks")

    for forbidden in ("image_uri", "raw_image_path", "api_key"):
        candidate = copy.deepcopy(success); candidate[forbidden] = "forbidden"
        invalid(candidate, envelope_schema, f"forbidden envelope field {forbidden}")
    invalid({**success, "status": "RUNNING"}, envelope_schema, "non-terminal provider status")
    print("PASS envelope excludes URI, raw path, credentials, and non-contract statuses")

    error_enum = set(envelope_schema["properties"]["error"]["oneOf"][1]["properties"]["code"]["enum"])
    check("error taxonomy is complete", error_enum == ERROR_CODES == set(RETRYABLE))
    check("privacy error is non-retryable", failure["error"]["code"] == "IMAGE_PRIVACY_BLOCKED" and not failure["retryable"] and not RETRYABLE["IMAGE_PRIVACY_BLOCKED"])
    check("bundle has no provider metadata", not ({"provider_id", "model_id", "runtime_id", "provenance"} & set(reference_schema["properties"])))
    provider_in_bundle = copy.deepcopy(bundle); provider_in_bundle["provider_id"] = "not-allowed"
    invalid(provider_in_bundle, reference_schema, "provider metadata in bundle")

    demo_source = (ROOT / "android/app/src/main/java/com/jovi/photoai/data/demo/DemoReferenceAnalyzer.kt").read_text(encoding="utf-8")
    check("Demo source is explicit", 'const val SOURCE_LABEL = "Demo Analysis"' in demo_source)
    check("Pipeline source is explicit", success["provenance"]["result_origin"] == "PIPELINE")
    check("uncertainty covers every analysis field", set(success["uncertainty_flags"]) == FIELDS and set(failure["uncertainty_flags"]) == FIELDS)
    check("success fixture separates bundle and envelope", success["bundle"] is not None and success["error"] is None and success["model_id"] not in success["bundle"].values())
    check("error fixture never substitutes Demo output", failure["bundle"] is None and failure["status"] == "FAILED")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # concise CI-friendly failure without a network/model side effect
        print(f"FAIL {exc}", file=sys.stderr)
        raise SystemExit(1)
