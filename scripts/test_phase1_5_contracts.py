#!/usr/bin/env python3
"""Offline schema and semantic counterexample tests for Phase 1.5 P0."""
from __future__ import annotations

import copy
import json
import re
import sys
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

from phase1_5_contract_semantics import (
    load_error_policy,
    validate_error_policy,
    validate_provider_envelope_semantics,
    validate_reference_bundle_semantics,
)


ROOT = Path(__file__).resolve().parents[1]
PHASE_DIR = ROOT / "docs" / "phase1_5"


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


def schema_errors(instance: object, schema: dict) -> list[object]:
    return sorted(Draft202012Validator(schema, registry=registry, format_checker=format_checker).iter_errors(instance), key=str)


def check(name: str, assertion: bool) -> None:
    if not assertion:
        raise AssertionError(name)
    print(f"PASS {name}")


def expect_schema_rejection(name: str, instance: object, schema: dict) -> None:
    check(f"SCHEMA_REJECTION {name}", bool(schema_errors(instance, schema)))


def expect_semantic_rejection(name: str, errors: list[dict[str, str]]) -> None:
    check(f"SEMANTIC_REJECTION {name}", bool(errors))


def envelope_semantic_errors(envelope: dict, policy: dict) -> list[dict[str, str]]:
    return validate_provider_envelope_semantics(envelope) + validate_error_policy(envelope, policy)


def taxonomy_codes_from_document() -> set[str]:
    document = (PHASE_DIR / "VLM_ERROR_TAXONOMY.md").read_text(encoding="utf-8")
    match = re.search(r"<!-- MACHINE_READABLE_ERROR_CODES_BEGIN -->(.*?)<!-- MACHINE_READABLE_ERROR_CODES_END -->", document, re.S)
    if not match:
        raise AssertionError("taxonomy document has no machine-readable code section")
    return set(re.findall(r"^\s*-\s*`([A-Z_]+)`\s*$", match.group(1), re.M))


def main() -> int:
    for schema, name in (
        (reference_schema, "canonical ReferenceBundle schema"),
        (alias_schema, "compatibility alias schema"),
        (envelope_schema, "Provider Envelope schema"),
        (corpus_schema, "corpus Manifest schema"),
    ):
        Draft202012Validator.check_schema(schema)
        check(f"SCHEMA_SELF_CHECK {name}", True)
    ids = [schema["$id"] for schema in (reference_schema, alias_schema, envelope_schema, corpus_schema)]
    check("SCHEMA_SELF_CHECK unique ids", len(ids) == len(set(ids)))

    bundle = load("docs/reference/fixtures/reference_bundle_demo.json")
    success = load("docs/reference/fixtures/provider_analysis_envelope_demo.json")
    failure = load("docs/reference/fixtures/provider_analysis_error_demo.json")
    cancelled = load("docs/reference/fixtures/provider_analysis_cancelled_demo.json")
    corpus = load("docs/phase1_5/fixtures/vlm_evaluation_corpus_manifest.v1.json")
    policy = load_error_policy()

    for instance, schema, name in (
        (bundle, reference_schema, "Demo Bundle"), (bundle, alias_schema, "compatibility alias"),
        (success, envelope_schema, "Pipeline SUCCESS Envelope fixture"), (failure, envelope_schema, "FAILED Envelope fixture"),
        (cancelled, envelope_schema, "CANCELLED Envelope fixture"), (corpus, corpus_schema, "corpus Manifest fixture"),
    ):
        check(f"SCHEMA_ACCEPTANCE {name}", not schema_errors(instance, schema))
    check("SEMANTIC_ACCEPTANCE Demo Bundle", not validate_reference_bundle_semantics(bundle))
    for envelope, name in ((success, "SUCCESS"), (failure, "FAILED"), (cancelled, "CANCELLED")):
        check(f"SEMANTIC_ACCEPTANCE {name} Envelope", not envelope_semantic_errors(envelope, policy))

    for value, name in (("content:opaque-user-media", "content URI identifier"), ("urn:photo:private", "URN identifier"), ("/private/photo", "Unix path identifier"), (r"C:\private\photo", "Windows path identifier"), ("user@example.com", "email identifier"), ("   ", "blank identifier")):
        candidate = copy.deepcopy(bundle); candidate["reference_id"] = value
        expect_schema_rejection(name, candidate, reference_schema)
    for field, value, name in (("scene", "   ", "whitespace-only field"), ("lighting", "\t\t", "tab-only field"), ("emotion", "", "empty field"), ("composition", "line one\nline two", "newline field")):
        candidate = copy.deepcopy(bundle); candidate[field] = value
        expect_schema_rejection(name, candidate, reference_schema)
    edge_space = copy.deepcopy(bundle); edge_space["scene"] = " valid scene "
    check("SCHEMA_ACCEPTANCE leading/trailing whitespace is semantic", not schema_errors(edge_space, reference_schema))
    expect_semantic_rejection("leading/trailing whitespace", validate_reference_bundle_semantics(edge_space))
    for value, name in (("English scene", "English"), ("中文，标点。", "Chinese punctuation"), ("夜景 ✨", "emoji")):
        candidate = copy.deepcopy(bundle); candidate["scene"] = value
        check(f"SCHEMA_ACCEPTANCE plain text {name}", not schema_errors(candidate, reference_schema))
        check(f"SEMANTIC_ACCEPTANCE plain text {name}", not validate_reference_bundle_semantics(candidate))
    unknown_field = copy.deepcopy(bundle); unknown_field["provider_id"] = "forbidden"
    expect_schema_rejection("unknown ReferenceBundle field", unknown_field, reference_schema)
    unknown_version = copy.deepcopy(bundle); unknown_version["version"] = "2.0"
    expect_schema_rejection("unknown ReferenceBundle version", unknown_version, reference_schema)

    earlier = copy.deepcopy(success); earlier["completed_at_utc"] = "2026-07-25T23:59:59Z"
    check("SCHEMA_ACCEPTANCE completed-before-started is semantic", not schema_errors(earlier, envelope_schema))
    expect_semantic_rejection("completed before started", envelope_semantic_errors(earlier, policy))
    naive = copy.deepcopy(success); naive["started_at_utc"] = "2026-07-26T00:00:00"
    check("SCHEMA_ACCEPTANCE naive timestamp is semantic", not schema_errors(naive, envelope_schema))
    expect_semantic_rejection("naive timestamp defense", envelope_semantic_errors(naive, policy))
    negative_latency = copy.deepcopy(success); negative_latency["latency_ms"] = -1
    expect_schema_rejection("negative latency", negative_latency, envelope_schema)
    expect_semantic_rejection("negative latency defense", envelope_semantic_errors(negative_latency, policy))

    for field, value, name in (("retryable", True, "SUCCESS retryable true"), ("model_id", None, "SUCCESS null model id"), ("model_id", "", "SUCCESS empty model id"), ("model_revision", None, "SUCCESS null model revision"), ("runtime_id", None, "SUCCESS null runtime id"), ("model_artifact_sha256", None, "SUCCESS null model hash"), ("model_artifact_sha256", "not-a-sha", "SUCCESS invalid model hash"), ("error", copy.deepcopy(failure["error"]), "SUCCESS error object"), ("bundle", None, "SUCCESS null bundle")):
        candidate = copy.deepcopy(success); candidate[field] = value
        expect_schema_rejection(name, candidate, envelope_schema)

    failed_bundle = copy.deepcopy(failure); failed_bundle["bundle"] = copy.deepcopy(bundle)
    expect_schema_rejection("FAILED bundle present", failed_bundle, envelope_schema)
    for field, value, name in (("retryable", True, "FAILED retryable policy mismatch"), ("product_action", "OFFER_RETRY", "FAILED product action policy mismatch"), ("preserve_reference", True, "FAILED preserve-reference policy mismatch")):
        candidate = copy.deepcopy(failure)
        if field == "retryable": candidate[field] = value
        else: candidate["error"][field] = value
        check(f"SCHEMA_ACCEPTANCE {name}", not schema_errors(candidate, envelope_schema))
        expect_semantic_rejection(name, envelope_semantic_errors(candidate, policy))

    cancelled_timeout = copy.deepcopy(cancelled); cancelled_timeout["error"]["code"] = "PROVIDER_TIMEOUT"
    expect_schema_rejection("CANCELLED provider timeout", cancelled_timeout, envelope_schema)
    cancelled_retry = copy.deepcopy(cancelled); cancelled_retry["retryable"] = True
    expect_schema_rejection("CANCELLED retryable true", cancelled_retry, envelope_schema)
    cancelled_bundle = copy.deepcopy(cancelled); cancelled_bundle["bundle"] = copy.deepcopy(bundle)
    expect_schema_rejection("CANCELLED bundle present", cancelled_bundle, envelope_schema)
    cancelled_error = copy.deepcopy(cancelled); cancelled_error["error"] = None
    expect_schema_rejection("CANCELLED error missing", cancelled_error, envelope_schema)

    invalid_code = copy.deepcopy(failure); invalid_code["error"]["code"] = "NOT_A_V1_ERROR"
    expect_schema_rejection("invalid error code", invalid_code, envelope_schema)
    for message, name in (("cannot open content://media/external/images/1", "Photo Picker URI in error message"), (r"failed at C:\private\photo.jpg", "local path in error message"), ("Bearer secret-token", "Bearer token in error message"), ("Traceback (most recent call last):", "stack trace in error message")):
        candidate = copy.deepcopy(failure); candidate["error"]["user_message"] = message
        check(f"SCHEMA_ACCEPTANCE {name}", not schema_errors(candidate, envelope_schema))
        expect_semantic_rejection(name, envelope_semantic_errors(candidate, policy))
    diagnostic_upload = copy.deepcopy(failure); diagnostic_upload["error"]["allow_diagnostic_upload"] = True
    expect_schema_rejection("diagnostic upload enabled", diagnostic_upload, envelope_schema)

    schema_codes = set(envelope_schema["$defs"]["errorObject"]["properties"]["code"]["enum"])
    policy_codes = [entry["code"] for entry in policy["policies"]]
    check("POLICY unique codes", len(policy_codes) == len(set(policy_codes)))
    check("POLICY matches schema taxonomy", set(policy_codes) == schema_codes)
    check("POLICY matches taxonomy document", taxonomy_codes_from_document() == schema_codes)
    check("POLICY forbids provider Demo substitution", all(entry["demo_fallback_policy"] == "USER_EXPLICIT_OUT_OF_ENVELOPE_ONLY" for entry in policy["policies"]))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        raise SystemExit(1)
