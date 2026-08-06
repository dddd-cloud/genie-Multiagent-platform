import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import AgentForm from '../AgentForm';
import {
  emptyAgentFormState,
  parsePromptConfigText,
  validateAgentForm,
  type AgentFormState,
} from '../agentFormModel';
import { useState } from 'react';

function AgentFormHarness({initial = emptyAgentFormState(),}: {
  initial?: AgentFormState;
}) {
  const [value, setValue] = useState(initial);
  return (
    <AgentForm
      value={value}
      onChange={setValue}
      models={[
        {
          name: 'gpt-4o-mini',
          displayName: 'GPT-4o mini',
          isDefault: true,
          available: true,
        },
        {
          name: 'offline-model',
          displayName: 'Offline',
          isDefault: false,
          available: false,
        },
      ]}
      skills={[
        {
          id: 'skill-a',
          name: 'Skill A',
          description: '',
          instruction: '',
          outputRequirement: '',
          status: 'ENABLED',
          version: 1,
          capabilityKeys: [],
          createdAt: '',
          updatedAt: '',
        },
      ]}
      capabilities={[
        {
          key: 'builtin:file',
          displayName: 'File',
          available: true,
        },
      ]}
    />
  );
}

describe('AgentEditorTest', () => {
  it('renders structured/raw toggle and validates empty name', () => {
    render(<AgentFormHarness />);
    expect(screen.getByTestId('agent-form')).toBeTruthy();
    expect(screen.getByTestId('agent-prompt-mode')).toBeTruthy();
    expect(screen.getByTestId('agent-prompt-config')).toBeTruthy();

    const state = emptyAgentFormState();
    expect(validateAgentForm(state)).toBe('请填写 Agent 名称');
  });

  it('validates STRUCTURED JSON object editor', () => {
    expect(parsePromptConfigText('{', 'STRUCTURED').ok).toBe(false);
    expect(parsePromptConfigText('[]', 'STRUCTURED').ok).toBe(false);
    expect(parsePromptConfigText('{"role":"x"}', 'STRUCTURED')).toEqual({
      ok: true,
      value: { role: 'x' },
    });

    const state: AgentFormState = {
      ...emptyAgentFormState(),
      name: 'A',
      promptMode: 'STRUCTURED',
      promptConfigText: 'not-json',
    };
    expect(validateAgentForm(state)).toBe('promptConfig JSON 格式无效');
  });

  it('switches to RAW systemPrompt field', () => {
    render(<AgentFormHarness />);
    fireEvent.click(screen.getByRole('radio', { name: 'RAW' }));
    expect(screen.getByTestId('agent-system-prompt')).toBeTruthy();
    expect(screen.queryByTestId('agent-prompt-config')).toBeNull();
  });

  it('notifies onChange when name is edited', () => {
    const onChange = vi.fn();
    render(
      <AgentForm
        value={emptyAgentFormState()}
        onChange={onChange}
        models={[]}
        skills={[]}
        capabilities={[]}
      />,
    );
    fireEvent.change(screen.getByTestId('agent-name'), {target: { value: 'Research' },});
    expect(onChange).toHaveBeenCalled();
    expect(onChange.mock.calls[0]![0].name).toBe('Research');
  });
});
