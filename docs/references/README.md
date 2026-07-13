# Local Reference Repositories

Run:

```powershell
python scripts/fetch_reference_repos.py --profile core
```

Repositories are shallow-cloned into `docs/references/repos/`, which is intentionally ignored by Git. The script writes `REFERENCE_LOCK.json` containing exact commits. Do not copy code from `CONCEPT_ONLY` or `RESEARCH_ONLY` repositories. Production reuse requires a recorded license and ADR.
