"""Static checks that usage metering stays isolated in the 03-owned package."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
USAGE = ROOT / "genie-backend" / "src" / "main" / "java" / "com" / "jd" / "genie" / "platform" / "usage"
MIGRATION = ROOT / "genie-backend" / "src" / "main" / "resources" / "db" / "migration" / "V008__usage_metering.sql"


def main() -> int:
    assert USAGE.is_dir(), "usage package is missing"
    sql = MIGRATION.read_text(encoding="utf-8")
    assert "uk_usage_request" in sql or "uk_usage_message" in sql
    port = (USAGE / "service" / "MeteringConversationExecutionPort.java").read_text(encoding="utf-8")
    assert "@Primary" in port
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
