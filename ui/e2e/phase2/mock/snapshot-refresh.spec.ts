import { expect, test } from '@playwright/test';
import {
  clickSend,
  createConversationFromSidebar,
  fillComposer,
  loginAsMock,
  selectExecutionMode,
  setPhase2SseScenario,
} from '../helpers';

test.describe('Phase2 snapshot refresh (mock)', () => {
  test('after V2 complete, reload keeps assistant content', async ({page}) => {
    await loginAsMock(page, 'user-a');
    await setPhase2SseScenario(page, 'orchestrated-success');
    await createConversationFromSidebar(page);
    await selectExecutionMode(page, 'ORCHESTRATED');

    await fillComposer(page, 'Persist after reload');
    await clickSend(page);

    const finalText = 'Orchestrated run finished with a full summary.';
    await expect(page.getByText(finalText)).toBeVisible({ timeout: 30_000 });

    await page.reload();
    await expect(page.getByText('Persist after reload')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(finalText)).toBeVisible();
  });
});
