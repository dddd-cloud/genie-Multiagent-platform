"""Flag UI copy that fights JoyAgent's existing Chinese chrome."""

CANONICAL = {
    "login": "登录",
    "username": "用户名",
    "password": "密码",
    "new chat": "新会话",
    "new conversation": "新会话",
    "send": "发送",
    "submit": "登录",
}


def main(input):
    payload = input if isinstance(input, dict) else {}
    copy = payload.get("copy") or payload.get("labels") or []
    if isinstance(copy, str):
        copy = [item.strip() for item in copy.split(",") if item.strip()]
    findings = []
    for label in copy:
        key = str(label).strip().lower()
        if key in CANONICAL and str(label).strip() != CANONICAL[key]:
            findings.append(
                {
                    "input": label,
                    "prefer": CANONICAL[key],
                    "reason": "matches ui/e2e/helpers/auth.ts and ConversationSidebar",
                }
            )
    return {
        "ok": len(findings) == 0,
        "findings": findings,
        "canonical": CANONICAL,
        "source": "anthropics/skills frontend-design + JoyAgent ChatView labels",
    }
