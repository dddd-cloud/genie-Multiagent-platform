import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import type { UserPreferences } from '@/contracts';
import {
  USER_SETTINGS_FALLBACK,
  UserSettingsContext,
  type UserSettingsStatus,
} from '@/features/userSettings/useUserSettings';
import { DEFAULT_USER_PREFERENCES } from '@/services/settings';
import GeneralInput from '../index';

vi.mock('@/services/phase2/models', () => ({
  listModels: vi.fn().mockResolvedValue([
    {
      id: 'm1',
      name: 'gpt-4o-mini',
      displayName: 'GPT-4o mini',
      isDefault: true,
      available: true,
    },
  ]),
}));

function renderInput(
  send: (info: CHAT.TInputInfo) => void,
  status: UserSettingsStatus,
  preferences: Partial<UserPreferences>,
) {
  return render(
    <UserSettingsContext.Provider
      value={{
        ...USER_SETTINGS_FALLBACK,
        status,
        preferences: {
          ...DEFAULT_USER_PREFERENCES,
          ...preferences,
        },
      }}
    >
      <GeneralInput
        placeholder="x"
        showBtn
        disabled={false}
        size="big"
        send={send}
      />
    </UserSettingsContext.Provider>,
  );
}

function typeAndSend(text: string) {
  fireEvent.change(screen.getByPlaceholderText('x'), {target: { value: text },});
  fireEvent.click(screen.getByLabelText('发送'));
}

describe('GeneralInput composer', () => {
  it('starts with deep think off regardless of the retired preference key', () => {
    const send = vi.fn();
    renderInput(send, 'ready', { defaultDeepThink: true });

    typeAndSend('hi');

    expect(send).toHaveBeenCalledTimes(1);
    expect(send.mock.calls[0][0]).toMatchObject({
      message: 'hi',
      deepThink: false,
    });
  });

  it('keeps a manual deep-think toggle after send', () => {
    const send = vi.fn();
    renderInput(send, 'ready', { defaultDeepThink: false });

    fireEvent.click(screen.getByText('深度研究'));
    typeAndSend('hi');

    expect(send.mock.calls[0][0]).toMatchObject({ deepThink: true });
  });
});
