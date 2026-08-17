import { useEffect, useRef, useState } from 'react';
import { DownOutlined } from '@ant-design/icons';
import classNames from 'classnames';
import {
  EXECUTION_MODES,
  type ExecutionMode,
  type Phase2AgentResponse,
} from '@/contracts';
import { listAgents } from '@/services/phase2/agents';
import { ALLOWED_AGENTS_MAX, dedupeAllowedAgentIds } from './requestValidation';

export interface ExecutionModeSelectorProps {
  value?: ExecutionMode;
  onChange?: (mode: ExecutionMode) => void;
  allowedAgentIds?: readonly string[];
  onAllowedAgentIdsChange?: (agentIds: string[]) => void;
  disabled?: boolean;
}

const LABELS: Record<ExecutionMode, string> = {
  AUTO: 'Auto',
  DIRECT: 'Solo',
  ORCHESTRATED: 'Ensemble',
};

const menuClassName =
  'absolute bottom-[calc(100%+8px)] left-0 z-30 w-[240px] overflow-hidden rounded-[18px] border border-black/6 bg-white py-6 shadow-[0_12px_40px_rgba(0,0,0,0.12)]';

const pillClassName = (disabled: boolean) =>
  classNames(
    'inline-flex items-center gap-6 rounded-full px-10 py-5 text-[13px] leading-none border-0',
    'bg-transparent text-text-secondary transition-colors duration-150',
    disabled
      ? 'cursor-not-allowed opacity-40'
      : 'cursor-pointer hover:bg-black/[0.04] hover:text-text-primary',
  );

export default function ExecutionModeSelector({
  value = 'AUTO',
  onChange,
  allowedAgentIds = [],
  onAllowedAgentIdsChange,
  disabled = false,
}: ExecutionModeSelectorProps) {
  const [modeOpen, setModeOpen] = useState(false);
  const [agentOpen, setAgentOpen] = useState(false);
  const [agents, setAgents] = useState<Phase2AgentResponse[]>([]);
  const rootRef = useRef<HTMLDivElement>(null);
  const autoFilledRef = useRef(false);
  const [cleared, setCleared] = useState(false);

  const online = agents.filter((agent) => agent.status === 'ONLINE');
  const onlineIds = online.map((agent) => agent.id).slice(0, ALLOWED_AGENTS_MAX);
  const onlineKey = onlineIds.join(',');
  const selectedIds =
    value === 'DIRECT' ? [] : dedupeAllowedAgentIds(allowedAgentIds);
  const allSelected =
    onlineIds.length > 0 && onlineIds.every((id) => selectedIds.includes(id));

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const list = await listAgents();
        if (!cancelled) {
          setAgents(list ?? []);
        }
      } catch {
        if (!cancelled) {
          setAgents([]);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (value !== 'ORCHESTRATED') {
      autoFilledRef.current = false;
      setCleared(false);
      setAgentOpen(false);
      return;
    }
    if (autoFilledRef.current || onlineIds.length === 0) {
      return;
    }
    autoFilledRef.current = true;
    onAllowedAgentIdsChange?.(onlineIds);
  }, [value, onlineKey, onlineIds, onAllowedAgentIdsChange]);

  useEffect(() => {
    if (!modeOpen && !agentOpen) {
      return;
    }
    const onPointerDown = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setModeOpen(false);
        setAgentOpen(false);
      }
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setModeOpen(false);
        setAgentOpen(false);
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [modeOpen, agentOpen]);

  const selectMode = (mode: ExecutionMode) => {
    if (disabled || mode === value) {
      return;
    }
    onChange?.(mode);
    if (mode === 'ORCHESTRATED') {
      setCleared(false);
      if (onlineIds.length > 0) {
        autoFilledRef.current = true;
        onAllowedAgentIdsChange?.(onlineIds);
      } else {
        autoFilledRef.current = false;
        onAllowedAgentIdsChange?.([]);
      }
    } else {
      autoFilledRef.current = false;
      setCleared(false);
      onAllowedAgentIdsChange?.([]);
      setAgentOpen(false);
    }
  };

  const selectAll = () => {
    if (disabled || value !== 'ORCHESTRATED') {
      return;
    }
    autoFilledRef.current = true;
    setCleared(false);
    onAllowedAgentIdsChange?.(onlineIds);
  };

  const clearAll = () => {
    if (disabled || value !== 'ORCHESTRATED') {
      return;
    }
    autoFilledRef.current = true;
    setCleared(true);
    onAllowedAgentIdsChange?.([]);
  };

  const toggleAgent = (agentId: string) => {
    if (disabled || value !== 'ORCHESTRATED') {
      return;
    }
    autoFilledRef.current = true;
    const next = selectedIds.includes(agentId)
      ? selectedIds.filter((id) => id !== agentId)
      : [...selectedIds, agentId].slice(0, ALLOWED_AGENTS_MAX);
    setCleared(next.length === 0);
    onAllowedAgentIdsChange?.(dedupeAllowedAgentIds(next));
  };

  const agentTriggerLabel = () => {
    if (cleared && selectedIds.length === 0) {
      return '未选择';
    }
    if (allSelected || selectedIds.length === 0) {
      return 'All';
    }
    if (selectedIds.length === 1) {
      return online.find((agent) => agent.id === selectedIds[0])?.name ?? '1';
    }
    return String(selectedIds.length);
  };

  return (
    <div ref={rootRef} className="flex items-center min-w-0">
      <div className="relative">
        <button
          type="button"
          disabled={disabled}
          aria-expanded={modeOpen}
          aria-haspopup="listbox"
          data-testid="execution-mode-selector"
          className={pillClassName(disabled)}
          onClick={() => {
            if (disabled) {
              return;
            }
            setModeOpen((prev) => !prev);
            setAgentOpen(false);
          }}
        >
          <span className="font-medium">{LABELS[value]}</span>
          <span aria-hidden className="inline-flex">
            <DownOutlined
              className={classNames(
                'text-[10px] transition-transform duration-150',
                modeOpen ? 'rotate-180' : 'rotate-0',
              )}
            />
          </span>
        </button>

        {modeOpen ? (
          <div role="listbox" className={menuClassName}>
            {EXECUTION_MODES.map((mode) => {
              const active = mode === value;
              return (
                <button
                  key={mode}
                  type="button"
                  role="option"
                  aria-selected={active}
                  className={classNames(
                    'flex w-full items-center justify-between gap-12 border-0 bg-transparent px-14 py-8 text-left',
                    'transition-colors duration-150',
                    active
                      ? 'text-text-primary'
                      : 'text-text-secondary hover:bg-black/[0.03]',
                  )}
                  onClick={() => selectMode(mode)}
                >
                  <span className="text-[13px] font-medium leading-[18px]">
                    {LABELS[mode]}
                  </span>
                  {active ? (
                    <span aria-hidden className="text-[12px] text-text-primary">
                      ✓
                    </span>
                  ) : null}
                </button>
              );
            })}
          </div>
        ) : null}
      </div>

      {value === 'ORCHESTRATED' ? (
        <div className="relative">
          <button
            type="button"
            disabled={disabled}
            aria-expanded={agentOpen}
            aria-haspopup="listbox"
            data-testid="allowed-agent-selector"
            className={pillClassName(disabled)}
            onClick={() => {
              if (disabled) {
                return;
              }
              setAgentOpen((prev) => !prev);
              setModeOpen(false);
            }}
          >
            <span className="max-w-[120px] truncate font-medium">
              {agentTriggerLabel()}
            </span>
            <span aria-hidden className="inline-flex">
              <DownOutlined
                className={classNames(
                  'text-[10px] transition-transform duration-150',
                  agentOpen ? 'rotate-180' : 'rotate-0',
                )}
              />
            </span>
          </button>

          {agentOpen ? (
            <div
              role="listbox"
              className={classNames(menuClassName, 'w-[220px]')}
            >
              <button
                type="button"
                data-testid="allowed-agent-clear"
                className="flex w-full items-center border-0 bg-transparent px-14 py-8 text-left text-[13px] text-text-secondary transition-colors duration-150 hover:bg-black/[0.03] hover:text-text-primary"
                onClick={clearAll}
              >
                清空
              </button>
              <button
                type="button"
                data-testid="allowed-agent-all"
                className={classNames(
                  'flex w-full items-center justify-between gap-12 border-0 bg-transparent px-14 py-8 text-left text-[13px]',
                  'transition-colors duration-150 hover:bg-black/[0.03]',
                  allSelected ? 'text-text-primary' : 'text-text-secondary',
                )}
                onClick={selectAll}
              >
                <span className="font-medium">All</span>
                {allSelected ? (
                  <span aria-hidden className="text-[12px] text-text-primary">
                    ✓
                  </span>
                ) : null}
              </button>
              <div className="my-4 border-t border-black/6" />
              <div className="max-h-[220px] overflow-y-auto">
                {online.length === 0 ? (
                  <div className="px-14 py-6 text-[12px] text-text-tertiary">
                    暂无 ONLINE Agent
                  </div>
                ) : (
                  online.map((agent) => {
                    const checked = selectedIds.includes(agent.id);
                    return (
                      <button
                        key={agent.id}
                        type="button"
                        className={classNames(
                          'flex w-full items-center gap-8 border-0 bg-transparent px-14 py-7 text-left text-[13px]',
                          'transition-colors duration-150 hover:bg-black/[0.03]',
                          checked ? 'text-text-primary' : 'text-text-secondary',
                        )}
                        onClick={() => toggleAgent(agent.id)}
                      >
                        <span
                          className={classNames(
                            'flex size-16 shrink-0 items-center justify-center rounded-full border text-[10px]',
                            checked
                              ? 'border-text-primary bg-text-primary text-white'
                              : 'border-black/15 bg-transparent text-transparent',
                          )}
                        >
                          ✓
                        </span>
                        <span className="truncate">{agent.name}</span>
                      </button>
                    );
                  })
                )}
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
