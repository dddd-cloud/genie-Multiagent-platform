import { expect, test } from '@playwright/test';
import {
  clickSend,
  createConversationFromSidebar,
  fillComposer,
  loginAsMock,
  selectExecutionMode,
  setPhase2SseScenario,
} from '../helpers';

test.describe('Phase2 orchestration timeline (mock)', () => {
  test('ORCHESTRATED send shows timeline from orchestrated-success', async ({page}) => {
    await loginAsMock(page, 'user-a');
    await setPhase2SseScenario(page, 'orchestrated-success');
    await createConversationFromSidebar(page);
    await selectExecutionMode(page, 'ORCHESTRATED');

    await fillComposer(page, 'Run orchestrated plan');
    await clickSend(page);

    await expect(page.getByTestId('orchestration-timeline')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('orchestration-route')).toContainText(
      /ORCHESTRATED|MULTI_STEP/i,
    );
    await expect(page.getByTestId('orchestration-attempt-1')).toBeVisible();
    await expect(
      page.getByText('Orchestrated run finished with a full summary.'),
    ).toBeVisible({ timeout: 30_000 });
  });
});
