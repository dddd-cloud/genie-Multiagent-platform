#!/usr/bin/env python3
"""Recompute SHA256 hashes and verify evidence_manifest.json."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path


MANIFEST_NAME = "evidence_manifest.json"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify(evidence_dir: Path, manifest_path: Path) -> list[str]:
    errors: list[str] = []
    try:
        payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"cannot read manifest: {exc}"]

    files = payload.get("files")
    if not isinstance(files, dict):
        return ["manifest.files must be an object"]

    expected = {str(k).replace("\\", "/"): str(v).lower() for k, v in files.items()}

    actual: dict[str, str] = {}
    for path in sorted(evidence_dir.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(evidence_dir).as_posix()
        if rel == MANIFEST_NAME or path.resolve() == manifest_path.resolve():
            continue
        actual[rel] = sha256_file(path)

    missing = sorted(set(expected) - set(actual))
    unexpected = sorted(set(actual) - set(expected))
    for rel in missing:
        errors.append(f"missing file: {rel}")
    for rel in unexpected:
        errors.append(f"unexpected file not in manifest: {rel}")

    for rel in sorted(set(expected) & set(actual)):
        if expected[rel] != actual[rel]:
            errors.append(
                f"hash mismatch: {rel} expected={expected[rel]} actual={actual[rel]}"
            )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence-dir", required=True)
    parser.add_argument(
        "--manifest",
        help=f"Manifest path (default: <evidence-dir>/{MANIFEST_NAME})",
    )
    args = parser.parse_args()
    evidence_dir = Path(args.evidence_dir)
    manifest = Path(args.manifest) if args.manifest else evidence_dir / MANIFEST_NAME

    if not evidence_dir.is_dir():
        print(f"NOT A DIR: {evidence_dir}", file=sys.stderr)
        return 2
    if not manifest.is_file():
        print(f"MANIFEST MISSING: {manifest}", file=sys.stderr)
        return 2

    errors = verify(evidence_dir, manifest)
    if errors:
        for err in errors:
            print(f"ERROR: {err}", file=sys.stderr)
        print("VERIFY FAIL")
        return 1

    print("VERIFY PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
