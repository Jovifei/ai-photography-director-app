# Qwen Future Download Runbook

Status: `DO_NOT_RUN_UNTIL_OWNER_APPROVES`.

This runbook exists so a future approval is narrow and reproducible. It does not authorize any command today.

1. Independent artifact reviewer verifies `qwen_weight_license_evidence.v1.json` and its external evidence manifest at immutable revision `89644892e4d85e24eaac8bacfd4f463576704203`.
2. Owner separately promotes the exact primary JSON authorization record and names a quarantine directory. Evidence readiness is not legal approval and does not itself permit a download.
3. Re-fetch official metadata at the immutable revision; stop if the publisher, model-card license, file name, size, LFS OID, or exact revision differs.
4. The redirect state is `REDIRECT_DOMAIN_NOT_INDEPENDENTLY_VERIFIED`. Stop before any artifact GET, Range, or HEAD request if a domain is not explicitly approved in that future Owner decision.
5. Download only the named weight into quarantine, never into the repository, Android project, evidence corpus, or a product cache. Calculate local SHA-256 and require exact equality with `7de1838c87a5349b016c26a1c3f7d2bc400a3d485f95ef39a7059ffd734977a0`.
6. Keep the result quarantined. A separate Owner approval is required for runtime installation; a further separate approval is required for inference; a further separate approval is required for app integration or promotion.
7. Rollback/revocation: on hash, source, license, malware, or policy mismatch, revoke the promotion decision, remove the artifact only from the named quarantine under the approved cleanup procedure, preserve metadata-only evidence, and reopen independent review. Do not silently substitute a mirror or a different revision.

No `git lfs pull`, broad snapshot download, `latest` reference, tokenizer/runtime retrieval, model conversion, model execution, or GPU action is allowed by this document.
