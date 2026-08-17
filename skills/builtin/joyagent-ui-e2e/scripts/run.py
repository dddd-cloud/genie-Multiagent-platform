"""Generate a Playwright spec that follows ui/e2e/helpers/auth.ts."""


def main(input):
    payload = input if isinstance(input, dict) else {}
    name = (payload.get("name") or "generated").strip() or "generated"
    username = (payload.get("username") or "admin").strip() or "admin"
    path = payload.get("path") or "/app"
    slug = "".join(ch if ch.isalnum() or ch in "-_" else "-" for ch in name).strip("-") or "generated"
    spec = f'''import {{ expect, test }} from '@playwright/test';
import {{
  clickSend,
  createConversationFromSidebar,
  loginAs,
}} from './helpers/auth';

const e2eReady = process.env.MVP_E2E_READY === '1';

test.describe('{slug}', () => {{
  test.skip(!e2eReady, 'set MVP_E2E_READY=1');

  test('login and open {path}', async ({{ page }}) => {{
    await loginAs(page, '{username}');
    await expect(page).toHaveURL(/\\/app/);
    await createConversationFromSidebar(page);
    await page.waitForURL(/\\/app\\/chat\\//);
    await clickSend(page);
  }});
}});
'''
    return {
        "ok": True,
        "filename": f"ui/e2e/{slug}.spec.ts",
        "spec": spec,
        "helpers": [
            "ui/e2e/helpers/auth.ts loginAs",
            "createConversationFromSidebar uses 新会话",
            "clickSend uses aria-label 发送",
        ],
    }
