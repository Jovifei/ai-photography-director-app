# Qwen Candidate Desk Review

Status: `READY_FOR_OWNER_DOWNLOAD_DECISION`; no artifact download is authorized.

The primary candidate is `Qwen/Qwen3-VL-2B-Instruct` at immutable revision `89644892e4d85e24eaac8bacfd4f463576704203`. Official publisher metadata identifies Qwen, the model card declares `apache-2.0`, and the official Qwen3-VL repository declares Apache-2.0 for code. The exact repository metadata reports 12 files totaling 4,266,648,961 bytes and one 4,255,140,312-byte `model.safetensors` LFS object with OID SHA-256 `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`.

The backup is `Qwen/Qwen3-VL-4B-Instruct` at `ebb281ec70b05090aa6165b016eac8ec08e71b17`. Its two LFS weight objects total more than 8.87 GB before runtime allocations, so it is a metadata-only backup with `HIGH_RISK_AND_UNMEASURED` 12 GB fitness.

No model file, LFS object, tokenizer runtime artifact, framework package, Docker image, GPU workload, or inference call was downloaded or executed. The exact inventory is machine-readable in `qwen_candidate_inventory.v1.json`.

Official evidence: [Qwen3-VL repository](https://github.com/QwenLM/Qwen3-VL), [Qwen3-VL code license](https://github.com/QwenLM/Qwen3-VL/blob/main/LICENSE), [2B immutable tree](https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct/tree/89644892e4d85e24eaac8bacfd4f463576704203), and [4B immutable tree](https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct/tree/ebb281ec70b05090aa6165b016eac8ec08e71b17).
