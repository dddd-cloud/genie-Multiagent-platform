---
schemaVersion: 1
name: frontend-design
description: Distinctive UI direction for JoyAgent React screens. Adapted from anthropics/skills frontend-design, plus a script that flags generic AI-UI copy against this product's Chinese labels.
version: 1.0.0
entrypoints:
  - name: main
    runtime: pyodide
    script: scripts/run.py
    description: Score UI copy against JoyAgent ChatView vocabulary
    packages: []
---

# Frontend Design for JoyAgent

Adapted from [anthropics/skills frontend-design](https://github.com/anthropics/skills/tree/main/skills/frontend-design).

When changing JoyAgent UI (`ui/src/features/conversation`, `ui/src/components/ChatView`, Phase2 editors):

- Keep existing product words: 登录, 用户名, 密码, 新会话, 发送, 当前用户
- Do not invent English-only chrome on Chinese screens
- Match Ant Design buttons already used (`ant-btn-primary`)
- Login lives at `/login` and lands on `/app`

## Script input

```json
{"copy": ["Submit", "New Chat", "Send"]}
```

The script flags labels that should stay 登录 / 新会话 / 发送.
