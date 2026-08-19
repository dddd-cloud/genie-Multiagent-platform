"""Static acceptance checks for the Phase3 marketplace catalog.

This check intentionally does not start the application. It protects the 02
boundary while the full Java/TypeScript toolchain is unavailable on some
developer machines.
"""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
CATALOG = ROOT / "genie-backend" / "src" / "main" / "resources" / "marketplace" / "catalog.json"
ALLOWED_TYPES = {"AGENT", "TEAM", "SKILL", "MCP"}
SECRET_KEYS = re.compile(r"(credential|api[_-]?key|cookie|token|tenantid|ownerid|private.?prompt|memory|tool.?result)", re.I)


def walk_keys(value):
    if isinstance(value, dict):
        for key, child in value.items():
            yield key
            yield from walk_keys(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_keys(child)


def main() -> int:
    entries = json.loads(CATALOG.read_text(encoding="utf-8"))
    assert isinstance(entries, list) and entries, "catalog must be a non-empty array"
    ids = [entry["id"] for entry in entries]
    assert len(ids) == len(set(ids)), "catalog ids must be unique"
    assert {entry["type"] for entry in entries} == ALLOWED_TYPES, "all four catalog types are required"

    for entry in entries:
        for required in ("id", "type", "name", "description", "category", "tags", "sourceType", "license", "trustTier", "capabilities", "setup", "draft"):
            assert required in entry, f"{entry.get('id', '<unknown>')} missing {required}"
        assert entry["type"] in ALLOWED_TYPES
        assert isinstance(entry["tags"], list) and entry["tags"]
        assert isinstance(entry["capabilities"], list)
        assert isinstance(entry["setup"], list)
        assert not any(SECRET_KEYS.search(str(key)) for key in walk_keys(entry["draft"])), f"secret-like draft key in {entry['id']}"
        if entry["type"] == "MCP":
            draft = entry["draft"]
            assert draft.get("authType") in {"NONE", "BEARER_TOKEN", "QUERY_PARAM"}, f"invalid MCP auth type in {entry['id']}"
            assert draft.get("transportType") in {"SSE", "STREAMABLE_HTTP"}, f"invalid MCP transport in {entry['id']}"
            if draft.get("serverUrl"):
                assert draft["serverUrl"].startswith("https://"), f"MCP URL must use HTTPS in {entry['id']}"
            if draft.get("authType") == "NONE" and draft.get("transportType") == "SSE":
                assert draft.get("allowedTools"), f"installable MCP must declare a reviewed tool allowlist in {entry['id']}"
            if draft.get("authType") != "NONE":
                assert not any(str(key).lower() in {"credential", "token", "apikey", "password"} for key in draft), (
                    f"authenticated MCP template must not embed credential fields in {entry['id']}"
                )

    print(f"marketplace catalog: PASS ({len(entries)} curated entries; four types; reviewed packages; no embedded secrets)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
