# Qwen Future Download Runbook

Status: `DO_NOT_RUN_UNTIL_OWNER_APPROVES`.

This runbook exists so a future approval is narrow and reproducible. It does not authorize any command today.

1. Owner approves the exact primary JSON authorization record and named quarantine directory.
2. Re-fetch official metadata at revision `89644892e4d85e24eaac8bacfd4f463576704203`; stop if the official license, file name, size, or LFS OID differs.
3. Permit only `huggingface.co` and `us.aws.cdn.hf.co`; stop on any mirror or unknown redirect.
4. Download only the named weight into quarantine, never into the repository, Android project, or evidence corpus.
5. Calculate local SHA-256 and require exact equality with `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`.
6. Keep the result quarantined. A separate Owner approval is required for runtime installation, inference, retention, promotion, or deletion.

No `git lfs pull`, broad snapshot download, `latest` reference, tokenizer/runtime retrieval, model conversion, model execution, or GPU action is allowed by this document.
