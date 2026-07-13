#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import json
import subprocess
import sys
from pathlib import Path


def run(cmd: list[str], cwd: Path | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, cwd=cwd, text=True, capture_output=True, check=check)


def main() -> int:
    parser = argparse.ArgumentParser(description="Shallow-clone approved reference repositories into a gitignored docs directory.")
    parser.add_argument("--profile", choices=["core", "all"], default="core")
    parser.add_argument("--refresh", action="store_true", help="Fetch a new shallow origin HEAD when the local reference clone is clean.")
    parser.add_argument("--only", action="append", default=[], help="Fetch only a named repository id; may be repeated.")
    parser.add_argument("--list", action="store_true", help="List matching repositories without cloning.")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    config_path = root / "config" / "reference_repositories.json"
    data = json.loads(config_path.read_text(encoding="utf-8"))
    dest_root = root / "docs" / "references" / "repos"
    dest_root.mkdir(parents=True, exist_ok=True)

    selected = []
    for repo in data["repositories"]:
        if args.profile == "core" and repo["profile"] != "core":
            continue
        if args.only and repo["id"] not in set(args.only):
            continue
        selected.append(repo)
    if args.list:
        for repo in selected:
            print(f"{repo['id']}	{repo['profile']}	{repo['reuse_mode']}	{repo['url']}")
        return 0

    lock: list[dict] = []
    failures: list[str] = []
    for repo in selected:
        target = dest_root / repo["folder"]
        try:
            if not target.exists():
                result = run(["git", "clone", "--depth", "1", "--filter=blob:none", repo["url"], str(target)], check=False)
                if result.returncode != 0:
                    raise RuntimeError(result.stderr.strip() or result.stdout.strip())
            elif not (target / ".git").exists():
                raise RuntimeError("target exists but is not a Git repository")
            elif args.refresh:
                dirty = run(["git", "status", "--porcelain"], cwd=target).stdout.strip()
                if dirty:
                    raise RuntimeError("local reference clone has changes; refusing to refresh")
                run(["git", "fetch", "--depth", "1", "origin"], cwd=target)
                head_ref = run(["git", "symbolic-ref", "refs/remotes/origin/HEAD"], cwd=target, check=False).stdout.strip()
                if head_ref:
                    branch = head_ref.rsplit("/", 1)[-1]
                    run(["git", "checkout", "-B", branch, f"origin/{branch}"], cwd=target)

            commit = run(["git", "rev-parse", "HEAD"], cwd=target).stdout.strip()
            remote = run(["git", "remote", "get-url", "origin"], cwd=target).stdout.strip()
            if remote.rstrip("/") != repo["url"].rstrip("/"):
                raise RuntimeError(f"origin mismatch: expected {repo['url']}, found {remote}")
            lock.append({
                "id": repo["id"],
                "remote": remote,
                "commit": commit,
                "folder": str(target.relative_to(root)).replace("\\", "/"),
                "reuse_mode": repo["reuse_mode"],
                "license_snapshot": repo["license_snapshot"],
                "fetched_at": dt.datetime.now(dt.timezone.utc).isoformat()
            })
            print(f"OK {repo['id']} {commit[:12]}")
        except Exception as exc:
            failures.append(f"{repo['id']}: {exc}")
            print(f"ERROR {repo['id']}: {exc}", file=sys.stderr)

    lock_path = root / "docs" / "references" / "REFERENCE_LOCK.json"
    lock_path.write_text(json.dumps({"generated": True, "repositories": lock}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {lock_path}")
    if failures:
        print("Some repositories failed; inspect before continuing.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
