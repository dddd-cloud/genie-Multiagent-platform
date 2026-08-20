import { useEffect, useRef, useState } from 'react';
import { DownOutlined } from '@ant-design/icons';
import classNames from 'classnames';
import {
  EXECUTION_MODES,
  type ExecutionMode,
  type Phase2AgentResponse,
} from '@/contracts';
import type { Phase2TeamResponse } from '@/contracts/phase2';
import { listAgents } from '@/services/phase2/agents';
import { listTeams } from '@/services/phase2/teams';
import { ALLOWED_AGENTS_MAX, dedupeAllowedAgentIds } from './requestValidation';

export interface ExecutionModeSelectorProps {
  value?: ExecutionMode;
  onChange?: (mode: ExecutionMode) => void;
  allowedAgentIds?: readonly string[];
  onAllowedAgentIdsChange?: (agentIds: string[]) => void;
  teamId?: string | null;
  onTeamIdChange?: (teamId: string | null) => void;
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

const rowClassName =
  'flex w-full items-center gap-8 border-0 bg-transparent px-14 py-7 text-left text-[13px] transition-colors duration-150 hover:bg-black/[0.03]';

export default function ExecutionModeSelector({
  value = 'AUTO',
  onChange,
  allowedAgentIds = [],
  onAllowedAgentIdsChange,
  teamId = null,
  onTeamIdChange,
  disabled = false,
}: ExecutionModeSelectorProps) {
  const [modeOpen, setModeOpen] = useState(false);
  const [agentOpen, setAgentOpen] = useState(false);
  const [agents, setAgents] = useState<Phase2AgentResponse[]>([]);
  const [teams, setTeams] = useState<Phase2TeamResponse[]>([]);
  const [teamsLoaded, setTeamsLoaded] = useState(false);
  const [customMode, setCustomMode] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const autoFilledRef = useRef(false);
  const [cleared, setCleared] = useState(false);

  const online = agents.filter((agent) => agent.status === 'ONLINE');
  const onlineIds = online.map((agent) => agent.id).slice(0, ALLOWED_AGENTS_MAX);
  const onlineKey = onlineIds.join(',');
  const selectedIds = dedupeAllowedAgentIds(allowedAgentIds);
  const allSelected =
    onlineIds.length > 0 && onlineIds.every((id) => selectedIds.includes(id));
  /** With teams available the second pill picks a team; 自定义 falls back to agents. */
  const teamBranch = teams.length > 0 && !customMode;
  const selectedTeam = teams.find((team) => team.id === teamId) ?? null;

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
    let cancelled = false;
    void (async () => {
      try {
        const list = await listTeams();
        if (!cancelled) {
          setTeams(list ?? []);
        }
      } catch {
        if (!cancelled) {
          setTeams([]);
        }
      } finally {
        if (!cancelled) {
          setTeamsLoaded(true);
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
      setCustomMode(false);
      if (value !== 'DIRECT') {
        setAgentOpen(false);
      }
      return;
    }
    if (!teamsLoaded || teamBranch) {
      return;
    }
    if (autoFilledRef.current || onlineIds.length === 0) {
      return;
    }
    autoFilledRef.current = true;
    onAllowedAgentIdsChange?.(onlineIds);
  }, [
    value,
    onlineKey,
    onlineIds,
    onAllowedAgentIdsChange,
    teamsLoaded,
    teamBranch,
  ]);

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
    setModeOpen(false);
    if (mode === 'ORCHESTRATED') {
      setCleared(false);
      setCustomMode(false);
      if (teamBranch) {
        autoFilledRef.current = false;
        onAllowedAgentIdsChange?.([]);
        return;
      }
      if (onlineIds.length > 0) {
        autoFilledRef.current = true;
        onAllowedAgentIdsChange?.(onlineIds);
      } else {
        autoFilledRef.current = false;
        onAllowedAgentIdsChange?.([]);
      }
      return;
    }
    autoFilledRef.current = false;
    setCleared(false);
    setCustomMode(false);
    onAllowedAgentIdsChange?.([]);
    onTeamIdChange?.(null);
    setAgentOpen(false);
  };

  const selectSoloAgent = (id: string) => {
    if (disabled) {
      return;
    }
    onAllowedAgentIdsChange?.([id]);
    onTeamIdChange?.(null);
    setAgentOpen(false);
  };

  const selectTeam = (id: string) => {
    if (disabled) {
      return;
    }
    onTeamIdChange?.(id);
    onAllowedAgentIdsChange?.([]);
    setAgentOpen(false);
  };

  const enterCustom = () => {
    if (disabled) {
      return;
    }
    setCustomMode(true);
    onTeamIdChange?.(null);
    setCleared(false);
    if (onlineIds.length > 0) {
      autoFilledRef.current = true;
      onAllowedAgentIdsChange?.(onlineIds);
    }
  };

  const backToTeams = () => {
    if (disabled) {
      return;
    }
    setCustomMode(false);
    autoFilledRef.current = false;
    setCleared(false);
    onAllowedAgentIdsChange?.([]);
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

  const soloSelected = online.find((agent) => agent.id === selectedIds[0]) ?? null;
  const soloLabel = soloSelected?.name ?? '选择智能体';

  const secondPillLabel = teamBranch
    ? (selectedTeam?.name ?? '选择团队')
    : agentTriggerLabel();

  const pickerScrollClass = 'max-h-[216px] overflow-y-auto overscroll-contain';

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
            data-testid={teamBranch ? 'team-selector' : 'allowed-agent-selector'}
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
              {secondPillLabel}
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

          {agentOpen && teamBranch ? (
            <div
              role="listbox"
              className={classNames(menuClassName, 'w-[220px]')}
              data-testid="team-menu"
            >
              <div className="max-h-[216px] overflow-y-auto overscroll-contain">
                {teams.map((team) => {
                  const active = team.id === teamId;
                  return (
                    <button
                      key={team.id}
                      type="button"
                      data-testid={`team-option-${team.id}`}
                      className={classNames(
                        rowClassName,
                        'justify-between',
                        active ? 'text-text-primary' : 'text-text-secondary',
                      )}
                      onClick={() => selectTeam(team.id)}
                    >
                      <span className="truncate">{team.name}</span>
                      {active ? (
                        <span aria-hidden className="text-[12px]">
                          ✓
                        </span>
                      ) : null}
                    </button>
                  );
                })}
              </div>
              <div className="my-4 border-t border-black/6" />
              <button
                type="button"
                data-testid="team-custom"
                className={classNames(rowClassName, 'text-text-secondary')}
                onClick={enterCustom}
              >
                自定义
              </button>
            </div>
          ) : null}

          {agentOpen && !teamBranch ? (
            <div
              role="listbox"
              className={classNames(menuClassName, 'w-[220px]')}
            >
              {teams.length > 0 ? (
                <>
                  <button
                    type="button"
                    data-testid="team-back"
                    className={classNames(rowClassName, 'text-text-secondary')}
                    onClick={backToTeams}
                  >
                    返回团队
                  </button>
                  <div className="my-4 border-t border-black/6" />
                </>
              ) : null}
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
              <div className="max-h-[216px] overflow-y-auto overscroll-contain">
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
                          rowClassName,
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

      {value === 'DIRECT' ? (
        <div className="relative">
          <button
            type="button"
            disabled={disabled}
            aria-expanded={agentOpen}
            aria-haspopup="listbox"
            data-testid="solo-agent-selector"
            className={pillClassName(disabled)}
            onClick={() => {
              if (disabled) {
                return;
              }
              setAgentOpen((prev) => !prev);
              setModeOpen(false);
            }}
          >
            <span className="max-w-[120px] truncate font-medium">{soloLabel}</span>
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
              data-testid="solo-agent-menu"
            >
              <div className={pickerScrollClass}>
                {online.length === 0 ? (
                  <div className="px-14 py-6 text-[12px] text-text-tertiary">
                    暂无 ONLINE Agent
                  </div>
                ) : (
                  online.map((agent) => {
                    const active = agent.id === soloSelected?.id;
                    return (
                      <button
                        key={agent.id}
                        type="button"
                        data-testid={`solo-agent-option-${agent.id}`}
                        className={classNames(
                          rowClassName,
                          'justify-between',
                          active ? 'text-text-primary' : 'text-text-secondary',
                        )}
                        onClick={() => selectSoloAgent(agent.id)}
                      >
                        <span className="truncate">{agent.name}</span>
                        {active ? (
                          <span aria-hidden className="text-[12px]">
                            ✓
                          </span>
                        ) : null}
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
