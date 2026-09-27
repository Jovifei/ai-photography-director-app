#!/usr/bin/env python3
"""Validate the non-runtime, fail-closed Product READY promotion gate."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker
from jsonschema.exceptions import SchemaError

ROOT = Path(__file__).resolve().parents[1]
T8_BASE = "acf43a8d734675ec23da534472cade129b7b1068"
T9_REL = Path("docs/phase1_5/t9")
POLICY_REL = T9_REL / "ready_promotion_policy.v1.json"
POLICY_SCHEMA_REL = T9_REL / "ready_promotion_policy.v1.schema.json"
CANDIDATE_SCHEMA_REL = T9_REL / "ready_promotion_candidate.v1.schema.json"
DECISION_SCHEMA_REL = T9_REL / "ready_promotion_decision.v1.schema.json"
FIXTURE_DIR_REL = T9_REL / "fixtures"
OPAQUE_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
PATH_RE = re.compile(r"(?:^[A-Za-z]:[\\/]|^\\\\|(?:^|/)(?:Users|home|private|storage|sdcard|secrets?)(?:/|$)|(?:content|file)://|@)", re.IGNORECASE)


class T9ValidationError(ValueError):
    """Expected fail-closed T9 validation error."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise T9ValidationError(message)


def read_json(root: Path, relative: Path | str) -> Any:
    path = root / Path(relative)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise T9ValidationError(f"invalid JSON: {Path(relative).as_posix()}") from exc


def schema_validator(schema: dict[str, Any]) -> Draft202012Validator:
    try:
        Draft202012Validator.check_schema(schema)
    except SchemaError as exc:
        raise T9ValidationError("T9 schema self-check failed") from exc
    return Draft202012Validator(schema, format_checker=FormatChecker())


def schema_errors(validator: Draft202012Validator, instance: Any) -> list[str]:
    return [error.message for error in sorted(validator.iter_errors(instance), key=lambda item: list(item.absolute_path))]


def unsafe(value: Any) -> bool:
    if isinstance(value, str):
        return bool(PATH_RE.search(value)) or ".." in Path(value).parts
    if isinstance(value, dict):
        return any(unsafe(key) or unsafe(child) for key, child in value.items())
    if isinstance(value, list):
        return any(unsafe(child) for child in value)
    return False


def digest(root: Path, relative: str) -> tuple[int, str]:
    data = (root / relative).read_bytes()
    return len(data), hashlib.sha256(data).hexdigest()


def load_policy(root: Path) -> tuple[dict[str, Any], Draft202012Validator, Draft202012Validator, Draft202012Validator]:
    policy_schema = read_json(root, POLICY_SCHEMA_REL)
    candidate_schema = read_json(root, CANDIDATE_SCHEMA_REL)
    decision_schema = read_json(root, DECISION_SCHEMA_REL)
    policy_validator = schema_validator(policy_schema)
    candidate_validator = schema_validator(candidate_schema)
    decision_validator = schema_validator(decision_schema)
    policy = read_json(root, POLICY_REL)
    require(not schema_errors(policy_validator, policy), "T9 policy schema invalid")
    require(not unsafe(policy), "unsafe value in T9 policy")
    for binding in policy["bindings"]:
        size, sha = digest(root, binding["path"])
        require(size == binding["bytes"] and sha == binding["sha256"], f"bound source drift: {binding['id']}")
    require(all(value is False for value in policy["authority"].values()), "T9 authority promoted")
    return policy, candidate_validator, decision_validator, policy_validator


def load_candidate(root: Path, relative: Path, validator: Draft202012Validator) -> dict[str, Any]:
    candidate = read_json(root, relative)
    require(not schema_errors(validator, candidate), f"candidate schema invalid: {relative.as_posix()}")
    require(not unsafe(candidate), f"unsafe promotion candidate: {relative.as_posix()}")
    return candidate


def _source_class(candidate: dict[str, Any]) -> str:
    if candidate["provider_outcome"] != "SUCCESS":
        return "UNAVAILABLE"
    return candidate["evidence_class"]


def evaluate_promotion(candidate: dict[str, Any], policy: dict[str, Any], decision_validator: Draft202012Validator, *, hypothetical: bool = False) -> dict[str, Any]:
    blockers: list[str] = []
    if candidate["provider_outcome"] != "SUCCESS":
        blockers.append("NOT_APPLICABLE_NON_SUCCESS")
    if candidate["evidence_class"] == "SYNTHETIC_CONTRACT_ONLY" and not hypothetical:
        blockers.append("BLOCKED_SYNTHETIC_EVIDENCE")
    if not (candidate["envelope_contract_valid"] and candidate["bundle_contract_valid"] and candidate["reference_identity_valid"]):
        blockers.append("BLOCKED_CONTRACT_INVALID")
    if not candidate["evaluation_contract_valid"]:
        blockers.append("BLOCKED_CONTRACT_INVALID")
    if not candidate["adjudication_complete"]:
        blockers.append("BLOCKED_ADJUDICATION_REQUIRED")
    if candidate["severe_hallucination_unresolved"]:
        blockers.append("BLOCKED_SEVERE_FINDING")
    if (candidate["threshold_status"] != "APPROVED" or not policy["authority"]["owner_thresholds_approved"]) and not hypothetical:
        blockers.append("BLOCKED_THRESHOLD_NOT_APPROVED")
    if (candidate["provider_qualification_state"] != "PROVIDER_QUALIFIED" or not policy["authority"]["provider_qualification_authorized"]) and not hypothetical:
        blockers.append("BLOCKED_PROVIDER_NOT_QUALIFIED")
    if (candidate["human_review_state"] != "ACCEPTED" or not policy["authority"]["human_review_authorized"]) and not hypothetical:
        blockers.append("BLOCKED_HUMAN_REVIEW_NOT_AUTHORIZED")
    if (candidate["product_promotion_authority"] is not True or not policy["authority"]["product_ready_promotion_authorized"]) and not hypothetical:
        blockers.append("BLOCKED_PRODUCT_PROMOTION_NOT_AUTHORIZED")
    # Preserve precedence from the policy document while retaining every applicable blocker once.
    order = {name: index for index, name in enumerate(policy["blocker_precedence"])}
    def blocker_rank(item: str) -> int:
        key = "NON_SUCCESS" if item == "NOT_APPLICABLE_NON_SUCCESS" else item.removeprefix("BLOCKED_")
        return order.get(key, 999)
    unique_blockers = sorted(set(blockers), key=blocker_rank)
    eligible = not unique_blockers
    decision = "ELIGIBLE_AFTER_ALL_REQUIRED_GATES" if eligible else unique_blockers[0]
    result = {
        "decision_version": "1.0.0",
        "reference_id": candidate["reference_id"],
        "source_class": _source_class(candidate),
        "decision": decision,
        "trusted_ready_guidance_allowed": eligible,
        "blockers": unique_blockers,
        "product_action": "ALLOW_READY_GUIDANCE" if eligible else ("SHOW_DEMO_ONLY" if candidate["provider_result_origin"] == "UNAVAILABLE" and candidate["provider_outcome"] == "FAILED" and candidate["evaluation_state"] == "NOT_APPLICABLE" and False else "SHOW_UNAVAILABLE"),
        "demo_fallback_policy": policy["demo_fallback_policy"],
        "policy_version": policy["policy_version"],
    }
    require(not schema_errors(decision_validator, result), "T9 decision schema invalid")
    if not hypothetical:
        require(result["trusted_ready_guidance_allowed"] is False, "current authority produced trusted READY")
        require(result["decision"] != "ELIGIBLE_AFTER_ALL_REQUIRED_GATES", "current authority produced eligible promotion")
    return result


def run(root: Path) -> dict[str, Any]:
    policy, candidate_validator, decision_validator, _ = load_policy(root)
    fixture_paths = sorted((root / FIXTURE_DIR_REL).glob("*.json"))
    require(len(fixture_paths) >= 10, "T9 fixture matrix is incomplete")
    cases = []
    for path in fixture_paths:
        candidate = load_candidate(root, path.relative_to(root), candidate_validator)
        decision = evaluate_promotion(candidate, policy, decision_validator)
        cases.append({"fixture": path.name, "decision": decision})
        require(decision["trusted_ready_guidance_allowed"] is False, f"fixture promoted READY: {path.name}")
    return {"policy_status": policy["status"], "fixture_class": "CONTRACT_ONLY_SYNTHETIC_NO_REAL_PROMOTION", "cases": cases, "promotion_authorized": False}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    parser.add_argument("--require-real-ready-promotion", action="store_true")
    parser.add_argument("--require-android-ready-write", action="store_true")
    parser.add_argument("--require-provider-qualification", action="store_true")
    args = parser.parse_args(argv)
    if args.require_real_ready_promotion:
        print("BLOCKED_REAL_READY_PROMOTION_AUTHORIZATION_REQUIRED")
        return 2
    if args.require_android_ready_write:
        print("BLOCKED_ANDROID_PRODUCT_CHANGE_NOT_AUTHORIZED")
        return 3
    if args.require_provider_qualification:
        print("BLOCKED_PROVIDER_QUALIFICATION_NOT_AUTHORIZED")
        return 4
    try:
        result = run(args.root.resolve())
    except (T9ValidationError, OSError, KeyError, TypeError, ValueError) as exc:
        print(f"T9_PROMOTION_GATE_FAILED: {exc}")
        return 1
    print("PRODUCT_READY_PROMOTION_GATE_READY")
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
