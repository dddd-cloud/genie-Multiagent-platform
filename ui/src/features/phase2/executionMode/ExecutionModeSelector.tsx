import { Radio } from 'antd';
import { EXECUTION_MODES, type ExecutionMode } from '@/contracts';

export interface ExecutionModeSelectorProps {
  value?: ExecutionMode;
  onChange?: (mode: ExecutionMode) => void;
  disabled?: boolean;
}

const LABELS: Record<ExecutionMode, string> = {
  AUTO: 'AUTO',
  DIRECT: 'DIRECT',
  ORCHESTRATED: 'ORCHESTRATED',
};

export default function ExecutionModeSelector({
  value = 'AUTO',
  onChange,
  disabled = false,
}: ExecutionModeSelectorProps) {
  return (
    <Radio.Group
      value={value}
      disabled={disabled}
      onChange={(e) => {
        const next = e.target.value as ExecutionMode;
        if ((EXECUTION_MODES as readonly string[]).includes(next)) {
          onChange?.(next);
        }
      }}
      options={EXECUTION_MODES.map((mode) => ({
        label: LABELS[mode],
        value: mode,
      }))}
      optionType="button"
      buttonStyle="solid"
      data-testid="execution-mode-selector"
    />
  );
}
