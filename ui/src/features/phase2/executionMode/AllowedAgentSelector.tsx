import { Select } from 'antd';
import type { ExecutionMode, Phase2AgentResponse } from '@/contracts';
import { ALLOWED_AGENTS_MAX, dedupeAllowedAgentIds } from './requestValidation';

export interface AllowedAgentSelectorProps {
  agents: readonly Phase2AgentResponse[];
  value: readonly string[];
  onChange?: (agentIds: string[]) => void;
  executionMode: ExecutionMode;
  disabled?: boolean;
}

/**
 * Multi-select ONLINE agents (max 20).
 * DIRECT forces empty selection.
 * ORCHESTRATED with empty selection means all ONLINE agents on the backend.
 */
export default function AllowedAgentSelector({
  agents,
  value,
  onChange,
  executionMode,
  disabled = false,
}: AllowedAgentSelectorProps) {
  const online = agents.filter((a) => a.status === 'ONLINE');
  const forcedEmpty = executionMode === 'DIRECT';
  const selected = forcedEmpty ? [] : dedupeAllowedAgentIds(value);

  return (
    <Select
      mode="multiple"
      allowClear
      showSearch
      optionFilterProp="label"
      placeholder={
        forcedEmpty
          ? 'DIRECT 模式不可选择 Agent'
          : '空选 = 全部 ONLINE Agent'
      }
      disabled={disabled || forcedEmpty}
      value={selected}
      maxCount={ALLOWED_AGENTS_MAX}
      options={online.map((agent) => ({
        value: agent.id,
        label: agent.name,
      }))}
      onChange={(next) => {
        if (forcedEmpty) {
          onChange?.([]);
          return;
        }
        onChange?.(dedupeAllowedAgentIds(next).slice(0, ALLOWED_AGENTS_MAX));
      }}
      style={{ minWidth: 220 }}
      data-testid="allowed-agent-selector-legacy"
    />
  );
}
