#!/usr/bin/env python3
"""Scan evidence / reports for leaked secrets. Writes secret_scan.json."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

# Patterns intentionally avoid matching our own documentation of field names alone
# by requiring assignment / header-like shapes where practical.
PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    (
        "session_cookie",
        re.compile(
            r"(?i)(?:set-cookie|cookie)\s*[:=]\s*[^\s;]*(?:session|JSESSIONID|GENIE[_-]?SESSION)[^\s;]*\s*[:=]\s*[^\s;]+"
        ),
    ),
    (
        "session_cookie",
        re.compile(r"(?i)\b(?:JSESSIONID|SESSION)=([A-Za-z0-9_\-\.]{8,})"),
    ),
    (
        "csrf_token",
        re.compile(
            r"(?i)(?:x-xsrf-token|xsrf-token|csrf[_-]?token)\s*[:=]\s*([A-Za-z0-9_\-]{8,})"
        ),
    ),
    (
        "password",
        re.compile(
            r"(?i)(?:password|passwd|pwd)\s*[:=]\s*([^\s\"']{4,})"
        ),
    ),
    (
        "api_key",
        re.compile(
            r"(?i)(?:api[_-]?key|apikey)\s*[:=]\s*([A-Za-z0-9_\-]{8,})"
        ),
    ),
    (
        "bearer_token",
        re.compile(r"(?i)\bBearer\s+([A-Za-z0-9_\-\.=]{8,})"),
    ),
    (
        "internal_token",
        re.compile(
            r"(?i)GENIE_INTERNAL_AGENT_TOKEN\s*[:=]\s*([^\s\"']{4,})"
        ),
    ),
    (
        "internal_token",
        re.compile(
            r"(?i)X-Genie-Internal-Token\s*[:=]\s*([A-Za-z0-9_\-]{8,})"
        ),
    ),
]

SKIP_NAMES = frozenset(
    {
        "secret_scan.json",
        "evidence_manifest.json",
        ".gitkeep",
    }
)

TEXT_SUFFIXES = frozenset(
    {
        ".json",
        ".txt",
        ".log",
        ".md",
        ".xml",
        ".html",
        ".csv",
        ".yml",
        ".yaml",
        ".out",
        ".err",
        ".ts",
        ".js",
        ".sh",
    }
)


def _should_scan(path: Path) -> bool:
    if path.name in SKIP_NAMES:
        return False
    if path.suffix.lower() in TEXT_SUFFIXES:
        return True
    # allow extensionless small text dumps
    return path.suffix == "" and path.stat().st_size < 2_000_000


def scan_tree(root: Path) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    if not root.exists():
        return findings

    for path in sorted(root.rglob("*")):
        if not path.is_file() or not _should_scan(path):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        rel = str(path.relative_to(root)).replace("\\", "/")
        for kind, pattern in PATTERNS:
            for match in pattern.finditer(text):
                line_no = text.count("\n", 0, match.start()) + 1
                findings.append(
                    {
                        "type": kind,
                        "path": rel,
                        "line": line_no,
                        "snippet": _redact(match.group(0)),
                    }
                )
    return findings


def _redact(value: str) -> str:
    if len(value) <= 12:
        return "***"
    return value[:4] + "…" + "***"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--evidence-dir",
        required=True,
        help="evidence/mvp/{runId} directory to scan",
    )
    parser.add_argument(
        "--out",
        help="Output path (default: <evidence-dir>/secret_scan.json)",
    )
    parser.add_argument(
        "--extra",
        action="append",
        default=[],
        help="Extra roots to scan (repeatable)",
    )
    args = parser.parse_args()
    evidence_dir = Path(args.evidence_dir)
    out = Path(args.out) if args.out else evidence_dir / "secret_scan.json"

    findings: list[dict[str, Any]] = []
    findings.extend(scan_tree(evidence_dir))
    for extra in args.extra:
        findings.extend(scan_tree(Path(extra)))

    # de-dupe
    seen: set[tuple[Any, ...]] = set()
    unique: list[dict[str, Any]] = []
    for item in findings:
        key = (item["type"], item["path"], item["line"], item["snippet"])
        if key in seen:
            continue
        seen.add(key)
        unique.append(item)

    payload = {
        "scannedAt": datetime.now(timezone.utc).isoformat(),
        "evidenceDir": str(evidence_dir).replace("\\", "/"),
        "findingCount": len(unique),
        "result": "PASS" if len(unique) == 0 else "FAIL",
        "findings": unique,
    }
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {out} result={payload['result']} findings={payload['findingCount']}")
    return 0 if payload["result"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
