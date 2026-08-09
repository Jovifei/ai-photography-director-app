import json
import tempfile
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

from local_analysis_service.service import ModelUnavailable, ModelManifest, PairingAuthority, create_app


def ready_bundle(reference_id: str, marker: str = "scene") -> dict:
    fields = {
        "scene": marker,
        "background_story": "observed background",
        "lighting": "soft light",
        "composition": "centered frame",
        "subject_intent": "portrait subject",
        "emotion": "calm",
        "pose_template": "standing",
        "camera_position": "eye level",
        "director_prompt": "keep the subject clear",
    }
    return {"reference_id": reference_id, **fields, "version": "1.0"}


class FakeSummaryRuntime:
    ready = True

    def __init__(self):
        self.manifest = ModelManifest(
            model_id="Qwen/Qwen3-VL-2B-Instruct",
            model_revision="revision",
            model_artifact_sha256="a" * 64,
            runtime_id="test-runtime",
        )

    def health_check(self):
        return {"model_ready": True, **self.model_identity(), "device": "cuda:0"}

    def model_identity(self):
        return {
            "model_id": self.manifest.model_id,
            "model_revision": self.manifest.model_revision,
            "model_artifact_sha256": self.manifest.model_artifact_sha256,
            "runtime_id": self.manifest.runtime_id,
        }

    def summarize_ready_bundles(self, ready_items):
        ids = [item["reference_id"] for item in ready_items]
        return ({
            "common_scene_direction": "shared",
            "common_lighting_direction": "soft",
            "common_composition_direction": "centered",
            "common_subject_direction": "portrait",
            "differences": [],
            "strongest_references": [{"reference_id": ids[0], "reason": "clear"}],
            "recommended_primary_reference": ids[0],
            "photographer_action_summary": "keep the subject clear",
        }, {"inference_ms": 1, "peak_vram_bytes": 0, "pixel_values_present": False, "json_repair_count": 0})


class ServiceContractTest(unittest.TestCase):
    def test_missing_model_is_fail_closed_and_never_claims_success(self):
        with tempfile.TemporaryDirectory() as temp:
            app = create_app(Path(temp), PairingAuthority("pair-code", "access-token"))
            client = TestClient(app)
            health = client.get("/v1/healthz")
            self.assertFalse(health.json()["model_ready"])
            pair = client.post("/v1/pair", json={"pairing_code": "pair-code"})
            self.assertEqual(pair.status_code, 200)
            response = client.put(
                "/v1/analyze/photo_1",
                headers={"Authorization": "Bearer access-token", "Content-Type": "image/jpeg"},
                content=b"\xff\xd8\xffsynthetic",
            )
            self.assertEqual(response.status_code, 503)
            self.assertEqual(response.json()["error"]["code"], "PROVIDER_NOT_CONFIGURED")
            self.assertNotEqual(response.json()["status"], "SUCCESS")

    def test_pairing_is_single_use(self):
        authority = PairingAuthority("pair-code", "access-token")
        self.assertEqual(authority.pair("pair-code"), "access-token")
        self.assertIsNone(authority.pair("pair-code"))

    def test_model_manifest_rejects_absent_directory(self):
        with self.assertRaises(ModelUnavailable):
            ModelManifest.load(Path("missing-model"))

    def test_summary_accepts_ready_only_and_returns_opaque_counts(self):
        app = create_app(pairing=PairingAuthority("pair-code", "access-token"), runtime_override=FakeSummaryRuntime())
        client = TestClient(app)
        ready_items = [{"reference_id": "photo_001", "bundle": ready_bundle("photo_001")}]
        response = client.post(
            "/v1/summarize",
            headers={"Authorization": "Bearer access-token"},
            json={"project_id": "project_001", "ready_items": ready_items, "failed_count": 1},
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["ready_count"], 1)
        self.assertEqual(response.json()["failed_count"], 1)
        self.assertEqual(response.json()["summary"]["recommended_primary_reference"], "photo_001")

    def test_summary_rejects_failed_text_or_non_ready_reference(self):
        app = create_app(pairing=PairingAuthority("pair-code", "access-token"), runtime_override=FakeSummaryRuntime())
        client = TestClient(app)
        failed = ready_bundle("photo_001")
        failed["failed_detail"] = "FAILED_SENTINEL"
        response = client.post(
            "/v1/summarize",
            headers={"Authorization": "Bearer access-token"},
            json={"project_id": "project_001", "ready_items": [{"reference_id": "photo_001", "bundle": failed}], "failed_count": 1},
        )
        self.assertEqual(response.status_code, 400)
        invalid = client.post(
            "/v1/summarize",
            headers={"Authorization": "Bearer access-token"},
            json={"project_id": "project_001", "ready_items": [{"reference_id": "../failed", "bundle": failed}], "failed_count": 1},
        )
        self.assertEqual(invalid.status_code, 400)


if __name__ == "__main__":
    unittest.main()
