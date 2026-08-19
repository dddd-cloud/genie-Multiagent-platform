"""Static checks for the settings-center shell.

These checks do not start the application. They confirm the 03-owned routes,
settings nav, and old-path redirects still exist after the containerization.
"""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
ROUTER = ROOT / "ui" / "src" / "router" / "index.tsx"
SETTINGS_NAV = ROOT / "ui" / "src" / "features" / "settings" / "settingsNav.ts"
PHASE2_NAV = ROOT / "ui" / "src" / "layout" / "Phase2Navigation.tsx"


def main() -> int:
    router = ROUTER.read_text(encoding="utf-8")
    nav = SETTINGS_NAV.read_text(encoding="utf-8")
    phase2 = PHASE2_NAV.read_text(encoding="utf-8")

    for needle in (
        "path: 'models'",
        "path: 'agents'",
        "path: 'skills'",
        "path: 'mcp'",
        "path: 'memory'",
        "path: 'preferences'",
        "path: 'account'",
        "path: 'workspace'",
        "path: 'marketplace'",
        "path: 'generate'",
        "Navigate to=\"/app/settings/agents\"",
        "Navigate to=\"/app/settings/models\"",
    ):
        assert needle in router, f"missing route fragment {needle}"

    for label in ("模型", "Agent", "Skill", "MCP", "本地记忆", "偏好"):
        assert label in nav, f"settings nav missing {label}"

    assert "data-testid=\"phase2-navigation\"" in phase2
    assert "label: '团队'" in phase2
    assert "label: '会话'" not in phase2
    assert "label: 'Agent'" not in phase2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
