import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import AgentForm from '../AgentForm';
import {
  emptyAgentFormState,
  parsePromptConfigText,
  validateAgentForm,
  type AgentFormState,
} from '../agentFormModel';

function AgentFormHarness({ initial = emptyAgentFormState() }: { initial?: AgentFormState }) {
  const [value, setValue] = useState(initial);
  return (
    <AgentForm
      value={value}
      onChange={setValue}
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
          displayName: '文件',
          available: true,
        },
      ]}
    />
  );
}

describe('AgentEditorTest', () => {
  it('renders product fields and hides developer internals', () => {
    render(<AgentFormHarness />);
    expect(screen.getByTestId('agent-form')).toBeTruthy();
    expect(screen.getByTestId('agent-name')).toBeTruthy();
    expect(screen.getByTestId('agent-description')).toBeTruthy();
    expect(screen.getByTestId('agent-instructions')).toBeTruthy();
    expect(screen.queryByTestId('agent-test-open')).toBeNull();
    expect(screen.getByTestId('agent-skills')).toBeTruthy();
    expect(screen.getByTestId('agent-capabilities')).toBeTruthy();
    expect(screen.queryByTestId('prompt-preview-panel')).toBeNull();
    expect(screen.queryByTestId('agent-prompt-mode')).toBeNull();
    expect(screen.queryByText(/ID:/)).toBeNull();
    expect(screen.queryByText(/promptConfig/)).toBeNull();
    expect(validateAgentForm(emptyAgentFormState())).toBe('请填写名称');
  });

  it('validates missing instructions', () => {
    expect(
      validateAgentForm({
        ...emptyAgentFormState(),
        name: '研究助手',
      }),
    ).toBe('请填写指令');
  });

  it('keeps parsePromptConfigText for leftover structured payloads', () => {
    expect(parsePromptConfigText('{', 'STRUCTURED').ok).toBe(false);
    expect(parsePromptConfigText('{"role":"x"}', 'STRUCTURED')).toEqual({
      ok: true,
      value: { role: 'x' },
    });
  });

  it('notifies onChange when name is edited', () => {
    const onChange = vi.fn();
    render(
      <AgentForm
        value={emptyAgentFormState()}
        onChange={onChange}
        skills={[]}
        capabilities={[]}
      />,
    );
    fireEvent.change(screen.getByTestId('agent-name'), {
      target: { value: 'Research' },
    });
    expect(onChange).toHaveBeenCalled();
    expect(onChange.mock.calls[0]![0].name).toBe('Research');
  });
});
