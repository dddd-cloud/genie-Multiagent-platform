import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ModelSettingsPage from '../ModelSettingsPage';

vi.mock('@/services/phase2/models', () => ({
  listModels: vi.fn().mockResolvedValue([
    {
      id: 'm1',
      name: 'gpt-4o-mini',
      displayName: 'GPT-4o mini',
      isDefault: true,
      available: true,
    },
    {
      id: 'm2',
      name: 'system-default',
      displayName: 'system-default',
      isDefault: false,
      available: true,
    },
  ]),
}));

describe('ModelSettingsPage', () => {
  it('renders clickable models and hides system-default', async () => {
    render(
      <MemoryRouter initialEntries={['/app/settings/models']}>
        <Routes>
          <Route path="/app/settings/models" element={<ModelSettingsPage />} />
          <Route path="/app/settings/models/new" element={<div>new-model</div>} />
          <Route
            path="/app/settings/models/:modelId"
            element={<div>edit-model</div>}
          />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByTestId('settings-model-row-gpt-4o-mini')).toBeTruthy();
    expect(screen.queryByTestId('settings-model-row-system-default')).toBeNull();

    fireEvent.click(screen.getByTestId('settings-models-new'));
    await waitFor(() => {
      expect(screen.getByText('new-model')).toBeTruthy();
    });
  });
});
