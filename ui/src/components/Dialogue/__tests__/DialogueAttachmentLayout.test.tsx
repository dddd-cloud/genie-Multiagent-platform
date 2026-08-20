import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Dialogue from '../index';

function chat(partial: Partial<CHAT.ChatItem>): CHAT.ChatItem {
  return {
    query: '分析简历',
    files: [],
    responseType: 'txt',
    sessionId: 's1',
    requestId: 'r1',
    loading: false,
    forceStop: false,
    tasks: [],
    thought: '',
    response: '',
    taskStatus: 0,
    multiAgent: { tasks: [] },
    ...partial,
  };
}

describe('Dialogue user attachments', () => {
  it('places sent files on the right and hides the remove control', () => {
    render(
      <Dialogue
        chat={chat({
          files: [{ name: '我的简历.pdf', url: '', type: 'pdf', size: 942640 }],
        })}
        deepThink={false}
      />,
    );
    const turn = screen.getByTestId('user-turn');
    expect(turn.className).toContain('items-end');
    expect(screen.getByText('我的简历.pdf')).toBeInTheDocument();
    expect(screen.queryByLabelText('移除 我的简历.pdf')).toBeNull();
  });
});
