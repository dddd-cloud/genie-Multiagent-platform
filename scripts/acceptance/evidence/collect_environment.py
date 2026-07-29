#!/usr/bin/env python3
"""Collect a whitelist-only environment snapshot for MVP acceptance evidence.

Never dumps the full process environment (secrets).
"""

from __future__ import annotations

import argparse
import json
import platform
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def _run(cmd: list[str]) -> str | None:
    try:
        proc = subprocess.run(
            cmd,
            check=False,
            capture_output=True,
            text=True,
            timeout=30,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    out = (proc.stdout or "").strip()
    return out if proc.returncode == 0 and out else None


def _first_line(value: str | None) -> str | None:
    if not value:
        return None
    return value.splitlines()[0].strip()


def collect() -> dict[str, Any]:
    java = _first_line(_run(["java", "-version"]))
    # java -version writes to stderr on most JDKs
    if java is None:
        try:
            proc = subprocess.run(
                ["java", "-version"],
                check=False,
                capture_output=True,
                text=True,
                timeout=30,
            )
            err = (proc.stderr or proc.stdout or "").strip()
            java = err.splitlines()[0].strip() if err else None
        except (OSError, subprocess.TimeoutExpired):
            java = None

    mysql_image = _first_line(
        _run(
            [
                "docker",
                "compose",
                "-f",
                "deploy/docker-compose.mvp.yml",
                "images",
                "-q",
                "mysql",
            ]
        )
    )
    if mysql_image is None:
        mysql_image = _first_line(_run(["docker", "image", "inspect", "--format", "{{.Id}}", "mysql:8.0"]))

    return {
        "collectedAt": datetime.now(timezone.utc).isoformat(),
        "os": {
            "system": platform.system(),
            "release": platform.release(),
            "version": platform.version(),
            "machine": platform.machine(),
            "python": platform.python_version(),
        },
        "java": java,
        "maven": _first_line(_run(["mvn", "-version"])),
        "node": _first_line(_run(["node", "-v"])),
        "pnpm": _first_line(_run(["pnpm", "-v"])),
        "docker": _first_line(_run(["docker", "version", "--format", "{{.Server.Version}}"]))
        or _first_line(_run(["docker", "--version"])),
        "compose": _first_line(_run(["docker", "compose", "version", "--short"]))
        or _first_line(_run(["docker", "compose", "version"])),
        "mysqlImageId": mysql_image,
        "timezone": datetime.now().astimezone().tzname(),
        "timezoneOffset": datetime.now().astimezone().strftime("%z"),
        "toolsPresent": {
            "java": shutil.which("java") is not None,
            "mvn": shutil.which("mvn") is not None,
            "node": shutil.which("node") is not None,
            "pnpm": shutil.which("pnpm") is not None,
            "docker": shutil.which("docker") is not None,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out",
        required=True,
        help="Output path for environment.json",
    )
    args = parser.parse_args()
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = collect()
    out.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
