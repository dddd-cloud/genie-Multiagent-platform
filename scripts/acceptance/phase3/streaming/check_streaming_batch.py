"""Static checks for the compatible streaming batching path."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
CHAT = ROOT / "ui" / "src" / "components" / "ChatView"
HOOK = CHAT / "useStreamingText.ts"
VIEW = CHAT / "index.tsx"


def main() -> int:
    hook = HOOK.read_text(encoding="utf-8")
    view = VIEW.read_text(encoding="utf-8")
    assert "patchLiveChatRun" in hook
    assert "requestAnimationFrame" in hook
    assert "flushStreamingView" in view
    assert "StreamStatusBar" in view
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
