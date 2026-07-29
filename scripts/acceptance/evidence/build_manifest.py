#!/usr/bin/env python3
"""Build SHA256 evidence_manifest.json for an acceptance run directory."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path


MANIFEST_NAME = "evidence_manifest.json"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build(evidence_dir: Path) -> dict:
    files: dict[str, str] = {}
    for path in sorted(evidence_dir.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(evidence_dir).as_posix()
        if rel == MANIFEST_NAME:
            continue
        files[rel] = sha256_file(path)

    return {
        "builtAt": datetime.now(timezone.utc).isoformat(),
        "evidenceDir": evidence_dir.as_posix(),
        "fileCount": len(files),
        "files": files,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence-dir", required=True)
    parser.add_argument(
        "--out",
        help=f"Output path (default: <evidence-dir>/{MANIFEST_NAME})",
    )
    args = parser.parse_args()
    evidence_dir = Path(args.evidence_dir)
    if not evidence_dir.is_dir():
        print(f"NOT A DIR: {evidence_dir}", file=sys.stderr)
        return 2

    payload = build(evidence_dir)
    out = Path(args.out) if args.out else evidence_dir / MANIFEST_NAME
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {out} files={payload['fileCount']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
