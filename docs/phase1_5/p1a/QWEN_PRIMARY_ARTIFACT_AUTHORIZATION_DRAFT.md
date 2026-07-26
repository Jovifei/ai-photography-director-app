# Qwen Primary Artifact Authorization Draft

Status: `READY_FOR_OWNER_DOWNLOAD_DECISION`.

This is an authorization *draft*, not a download authorization. It freezes the only proposed primary artifact:

| Field | Frozen value |
| --- | --- |
| Model | `Qwen/Qwen3-VL-2B-Instruct` |
| Immutable revision | `89644892e4d85e24eaac8bacfd4f463576704203` |
| Weight file | `model.safetensors` |
| Expected bytes | 4,255,140,312 |
| Expected LFS SHA-256 | `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0` |
| State | `READY_FOR_OWNER_DOWNLOAD_DECISION` |
| Download / runtime / inference | false / false / false |

Only `huggingface.co` and the observed redirect host `us.aws.cdn.hf.co` are proposed for a future Owner-approved retrieval. Any other domain, redirect, filename, byte count, revision, license, or hash is a stop condition. A future run must use an isolated quarantine location, verify the final local SHA-256, and separately obtain runtime/privacy approval. It may not use private images or promote anything to Android.

The structured, testable record is `primary_artifact_authorization_draft.v1.json`.
