import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { Button } from 'antd';
import { useState } from 'react';
import VersionConflictAlert from '../VersionConflictAlert';
import { getPhase2ErrorMessage } from '@/services/phase2/errorMessages';
import { isVersionConflict } from '../phase2UiError';
import { MvpApiError } from '@/services/apiError';

function EditorConflictHarness() {
  const [versionConflict, setVersionConflict] = useState(false);
  const [saveDisabled, setSaveDisabled] = useState(false);

  const simulateConflict = () => {
    const err = new MvpApiError(409, 'VERSION_CONFLICT', 'agent version conflict');
    expect(isVersionConflict(err)).toBe(true);
    setVersionConflict(true);
    setSaveDisabled(true);
  };

  return (
    <div>
      {versionConflict ? (
        <VersionConflictAlert
          onReload={() => {
            setVersionConflict(false);
            setSaveDisabled(false);
          }}
        />
      ) : null}
      <Button
        data-testid="save-btn"
        disabled={saveDisabled || versionConflict}
        onClick={simulateConflict}
      >
        保存
      </Button>
      <Button data-testid="trigger-conflict" onClick={simulateConflict}>
        触发冲突
      </Button>
    </div>
  );
}

describe('VersionConflictUiTest', () => {
  it('maps VERSION_CONFLICT to shared Chinese message', () => {
    expect(getPhase2ErrorMessage('VERSION_CONFLICT')).toBe(
      '数据已被他人更新，请刷新后重试',
    );
  });

  it('disables save and shows reload action on conflict', () => {
    render(<EditorConflictHarness />);
    fireEvent.click(screen.getByTestId('trigger-conflict'));

    expect(screen.getByTestId('version-conflict-alert')).toBeTruthy();
    expect(screen.getByText('数据已被他人更新，请刷新后重试')).toBeTruthy();
    expect(screen.getByTestId('version-conflict-reload').textContent).toContain(
      '重新加载服务器版本',
    );
    expect(screen.getByTestId('save-btn')).toBeDisabled();

    fireEvent.click(screen.getByTestId('version-conflict-reload'));
    expect(screen.queryByTestId('version-conflict-alert')).toBeNull();
    expect(screen.getByTestId('save-btn')).not.toBeDisabled();
  });

  it('reload callback is invoked', () => {
    const onReload = vi.fn();
    render(<VersionConflictAlert onReload={onReload} />);
    fireEvent.click(screen.getByTestId('version-conflict-reload'));
    expect(onReload).toHaveBeenCalledTimes(1);
  });
});
