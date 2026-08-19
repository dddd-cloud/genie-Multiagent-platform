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

describe('GeneralInput deep-think default', () => {
  it('sends with deep think on when the saved preference enables it', () => {
    const send = vi.fn();
    renderInput(send, 'ready', { defaultDeepThink: true });

    typeAndSend('hi');

    expect(send).toHaveBeenCalledTimes(1);
    expect(send.mock.calls[0][0]).toMatchObject({
      message: 'hi',
      deepThink: true,
    });
  });

  it('sends with deep think off while preferences are still loading', () => {
    const send = vi.fn();
    renderInput(send, 'loading', { defaultDeepThink: true });

    typeAndSend('hi');

    expect(send.mock.calls[0][0]).toMatchObject({ deepThink: false });
  });

  it('does not let a late preference override a toggle the user already flipped', () => {
    const send = vi.fn();
    const { rerender } = renderInput(send, 'loading', {defaultDeepThink: false,});

    fireEvent.click(screen.getByText('深度研究'));

    rerender(
      <UserSettingsContext.Provider
        value={{
          ...USER_SETTINGS_FALLBACK,
          status: 'ready',
          preferences: {
            ...DEFAULT_USER_PREFERENCES,
            defaultDeepThink: false,
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

    typeAndSend('hi');

    expect(send.mock.calls[0][0]).toMatchObject({ deepThink: true });
  });
});
