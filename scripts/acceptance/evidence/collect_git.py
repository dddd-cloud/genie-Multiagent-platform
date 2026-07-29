#!/usr/bin/env python3
"""Collect git provenance for MVP acceptance evidence."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def _run(cmd: list[str]) -> tuple[int, str]:
    try:
        proc = subprocess.run(
            cmd,
            check=False,
            capture_output=True,
            text=True,
            timeout=60,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return 1, str(exc)
    out = (proc.stdout or "").strip()
    err = (proc.stderr or "").strip()
    return proc.returncode, out if out else err


def collect() -> dict[str, Any]:
    code_head, head = _run(["git", "rev-parse", "HEAD"])
    code_branch, branch = _run(["git", "rev-parse", "--abbrev-ref", "HEAD"])
    code_ts, commit_ts = _run(["git", "show", "-s", "--format=%cI", "HEAD"])
    code_status, status = _run(["git", "status", "--porcelain=v1"])
    code_dirty, dirty_out = _run(["git", "status", "--porcelain=v1"])
    dirty = bool(dirty_out.strip()) if code_dirty == 0 else True

    return {
        "collectedAt": datetime.now(timezone.utc).isoformat(),
        "head": head if code_head == 0 else None,
        "branch": branch if code_branch == 0 else None,
        "commitTimestamp": commit_ts if code_ts == 0 else None,
        "worktreeClean": (not dirty) if code_status == 0 else False,
        "worktreeStatus": status if code_status == 0 else None,
        "errors": {
            "head": None if code_head == 0 else head,
            "branch": None if code_branch == 0 else branch,
            "commitTimestamp": None if code_ts == 0 else commit_ts,
            "worktreeStatus": None if code_status == 0 else status,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, help="Output path for git_provenance.json")
    args = parser.parse_args()
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = collect()
    out.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {out}")
    return 0 if payload.get("head") else 1


if __name__ == "__main__":
    sys.exit(main())
