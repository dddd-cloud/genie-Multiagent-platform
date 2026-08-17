---
schemaVersion: 1
name: joyagent-ui-e2e
description: Write Playwright tests that match JoyAgent ui/e2e helpers (loginAs, 新会话, 发送). Adapted from anthropics/skills webapp-testing for this React UI. Use when adding or reviewing frontend acceptance tests.
version: 1.0.0
entrypoints:
  - name: main
    runtime: pyodide
    script: scripts/run.py
    description: Generate a Playwright spec skeleton for JoyAgent UI
    packages: []
---

# JoyAgent UI E2E

Adapted from [anthropics/skills webapp-testing](https://github.com/anthropics/skills/tree/main/skills/webapp-testing).
Do not invent Playwright locators. Copy the real helpers in `ui/e2e/helpers/auth.ts`.

## Real selectors in this repo

- Login: labels `/用户名|username/i` and `/密码|password/i`, submit `/登录|login/i`
- After login wait for `/\/app/`
- New chat button: `/新会话|新建|新对话|new/i`
- Composer send: `aria-label` `/发送|send/i`
- Skip unless `MVP_E2E_READY=1` for heavier suites

Passwords come from `MVP_ACCEPTANCE_USER_PASSWORD`. Do not hardcode production secrets.

## Script input

```json
{"name": "conversation-send", "username": "lin-wei", "path": "/app"}
```

Returns a spec that imports `loginAs`, `createConversationFromSidebar`, and `clickSend`.
