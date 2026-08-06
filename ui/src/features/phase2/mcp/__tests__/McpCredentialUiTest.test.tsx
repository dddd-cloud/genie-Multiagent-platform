import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, cleanup } from '@testing-library/react';
import { useState } from 'react';
import McpForm, {
  assertCredentialNotPersisted,
  emptyMcpFormState,
  type McpFormState,
} from '../McpForm';

function CredentialHarness({onCredentialChange,}: {
  onCredentialChange?: (v: string) => void;
}) {
  const [form, setForm] = useState<McpFormState>({
    ...emptyMcpFormState(),
    authType: 'BEARER_TOKEN',
    credentialConfigured: true,
  });
  const [credential, setCredential] = useState('');
  return (
    <McpForm
      value={form}
      onChange={setForm}
      credential={credential}
      onCredentialChange={(v) => {
        setCredential(v);
        onCredentialChange?.(v);
      }}
    />
  );
}

describe('McpCredentialUiTest', () => {
  it('keeps credential in component state only and never in URL/storage', () => {
    const secret = `secret-token-${Date.now()}`;
    render(<CredentialHarness />);

    const input = screen.getByTestId('mcp-credential');
    fireEvent.change(input, { target: { value: secret } });
    expect((input as HTMLInputElement).value).toBe(secret);

    assertCredentialNotPersisted(secret);
    expect(window.location.href.includes(secret)).toBe(false);
    expect(window.localStorage.getItem('mcp-credential')).toBeNull();
    expect(window.sessionStorage.getItem('mcp-credential')).toBeNull();
  });

  it('shows credentialConfigured boolean only, never a credential value from server', () => {
    render(<CredentialHarness />);
    expect(screen.getByTestId('mcp-credential-configured').textContent).toContain(
      '已配置凭据',
    );
    expect(screen.queryByText(/secret/i)).toBeNull();
  });

  it('clears credential on unmount', () => {
    const spy = vi.fn();
    const { unmount } = render(<CredentialHarness onCredentialChange={spy} />);
    fireEvent.change(screen.getByTestId('mcp-credential'), {target: { value: 'temp-cred' },});
    unmount();
    // McpForm cleanup calls onCredentialChange('')
    expect(spy).toHaveBeenCalledWith('');
    cleanup();
  });

  it('clears credential when switching auth to NONE', () => {
    function Harness() {
      const [form, setForm] = useState<McpFormState>({
        ...emptyMcpFormState(),
        authType: 'BEARER_TOKEN',
      });
      const [credential, setCredential] = useState('keep-me');
      return (
        <div>
          <div data-testid="cred-value">{credential}</div>
          <McpForm
            value={form}
            onChange={setForm}
            credential={credential}
            onCredentialChange={setCredential}
          />
        </div>
      );
    }
    render(<Harness />);
    fireEvent.click(screen.getByRole('radio', { name: 'NONE' }));
    expect(screen.getByTestId('cred-value').textContent).toBe('');
  });
});
