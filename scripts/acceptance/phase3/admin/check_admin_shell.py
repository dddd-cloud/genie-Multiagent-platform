"""Static checks for admin routes and the client-side ADMIN gate."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
ROUTER = ROOT / "ui" / "src" / "router" / "index.tsx"
GUARD = ROOT / "ui" / "src" / "features" / "admin" / "AdminGuard.tsx"


def main() -> int:
    router = ROUTER.read_text(encoding="utf-8")
    guard = GUARD.read_text(encoding="utf-8")
    assert "path: 'admin'" in router
    assert "path: 'users'" in router
    assert "path: 'usage'" in router
    assert "AdminGuard" in router
    assert "user?.role !== 'ADMIN'" in guard
    assert "admin-forbidden" in guard
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
