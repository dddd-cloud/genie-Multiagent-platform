#!/usr/bin/env python3
"""Validate a gate result JSON (schema-ish) for MVP acceptance."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

REQUIRED_FIELDS = ("gate", "command", "exitCode", "startedAt", "finishedAt", "result")
ALLOWED_RESULTS = frozenset({"PASS", "FAIL", "BLOCKED", "SKIPPED"})


def validate(payload: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    for field in REQUIRED_FIELDS:
        if field not in payload:
            errors.append(f"missing field: {field}")

    if "result" in payload and payload["result"] not in ALLOWED_RESULTS:
        errors.append(
            f"invalid result: {payload['result']!r} (allowed: {sorted(ALLOWED_RESULTS)})"
        )

    if "exitCode" in payload and not isinstance(payload["exitCode"], int):
        errors.append("exitCode must be an integer")

    for ts_field in ("startedAt", "finishedAt"):
        if ts_field in payload and not isinstance(payload[ts_field], str):
            errors.append(f"{ts_field} must be a string")

    if "command" in payload and not isinstance(payload["command"], str):
        errors.append("command must be a string")

    if "gate" in payload and not isinstance(payload["gate"], str):
        errors.append("gate must be a string")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", help="Path to gate result JSON")
    parser.add_argument(
        "--strict-pass",
        action="store_true",
        help="Also require result==PASS and exitCode==0",
    )
    args = parser.parse_args()
    path = Path(args.path)
    if not path.is_file():
        print(f"NOT FOUND: {path}", file=sys.stderr)
        return 2

    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        print(f"INVALID JSON: {exc}", file=sys.stderr)
        return 2

    if not isinstance(payload, dict):
        print("INVALID: root must be an object", file=sys.stderr)
        return 2

    errors = validate(payload)
    if args.strict_pass:
        if payload.get("result") != "PASS":
            errors.append("strict-pass requires result=PASS")
        if payload.get("exitCode") != 0:
            errors.append("strict-pass requires exitCode=0")

    if errors:
        for err in errors:
            print(f"ERROR: {err}", file=sys.stderr)
        return 1

    print(f"OK: {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
