#!/usr/bin/env python3
from __future__ import annotations

import datetime as dt
import json
import subprocess
from pathlib import Path


def git(root: Path, *args: str, check: bool = True) -> str:
    proc = subprocess.run(["git", *args], cwd=root, text=True, capture_output=True)
    if check and proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or proc.stdout.strip())
    return proc.stdout.strip()


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    binding = json.loads((root / "PROJECT_BINDING.json").read_text(encoding="utf-8"))
    actual_remote = git(root, "remote", "get-url", "origin", check=False)
    branch = git(root, "branch", "--show-current", check=False)
    head = git(root, "rev-parse", "HEAD", check=False)
    tracked_count = len([x for x in git(root, "ls-files", check=False).splitlines() if x.strip()])
    status = git(root, "status", "--short", check=False) or "clean"
    report = f"""# G0 Repository Bootstrap Report

Generated: {dt.datetime.now(dt.timezone.utc).isoformat()}

## Repository

- Project: `{binding['repository_name']}`
- Project ID: `{binding['project_id']}`
- Local path: `{binding['local_path']}`
- Expected remote: `{binding['remote_url']}`
- Actual remote: `{actual_remote}`
- Branch: `{branch}`
- HEAD commit: `{head}`
- Bundle role: `{binding['bundle_role']}`
- Tracked file count: `{tracked_count}`

## Required files

- README.md: `{(root / 'README.md').is_file()}`
- .gitignore: `{(root / '.gitignore').is_file()}`
- AGENTS.md: `{(root / 'AGENTS.md').is_file()}`
- CLAUDE.md: `{(root / 'CLAUDE.md').is_file()}`
- OPENCLAW.md: `{(root / 'OPENCLAW.md').is_file()}`
- PROJECT_BINDING.json: `{(root / 'PROJECT_BINDING.json').is_file()}`
- Shared Contract snapshot: `{(root / 'shared-contract/contract_version.json').is_file()}`

## Working tree

```text
{status}
```

## Gate statement

The repository scaffold was pushed only after the privacy audit. No product feature work is approved by this report. Stop and wait for owner approval before the next Gate.
"""
    out = root / "reports" / "G0_repository_bootstrap_report.md"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(report, encoding="utf-8", newline="\n")
    print(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
