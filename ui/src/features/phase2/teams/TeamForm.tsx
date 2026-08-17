import { memo, useMemo } from 'react';
import { Button, Input, Select, Space, Tag, Typography } from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined } from '@ant-design/icons';
import type { Phase2AgentResponse } from '@/contracts/phase2';
import { MAX_TEAM_MEMBERS, type TeamFormState } from './teamFormModel';

const { Text } = Typography;
const { TextArea } = Input;

export interface TeamFormProps {
  value: TeamFormState;
  onChange: (next: TeamFormState) => void;
  agents: Phase2AgentResponse[];
  disabled?: boolean;
  readOnly?: boolean;
}

function move(ids: string[], from: number, to: number): string[] {
  if (to < 0 || to >= ids.length) {
    return ids;
  }
  const next = [...ids];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

const TeamForm: GenieType.FC<TeamFormProps> = memo(
  ({ value, onChange, agents, disabled = false, readOnly = false }) => {
    const locked = disabled || readOnly;
    const agentMap = useMemo(
      () => new Map(agents.map((agent) => [agent.id, agent])),
      [agents],
    );
    const onlineAgents = useMemo(
      () => agents.filter((agent) => agent.status === 'ONLINE'),
      [agents],
    );
    const selectableMembers = useMemo(
      () =>
        agents.filter(
          (agent) =>
            agent.id !== value.masterAgentId &&
            !value.memberAgentIds.includes(agent.id),
        ),
      [agents, value.masterAgentId, value.memberAgentIds],
    );

    const patch = (partial: Partial<TeamFormState>) => {
      onChange({ ...value, ...partial });
    };

    return (
      <Space direction="vertical" size={16} className="w-full" data-testid="team-form">
        <div>
          <Text strong>团队名称</Text>
          <Input
            value={value.name}
            disabled={locked}
            maxLength={128}
            placeholder="例如：市场调研小组"
            onChange={(event) => patch({ name: event.target.value })}
            data-testid="team-name"
          />
        </div>

        <div>
          <Text strong>团队描述</Text>
          <TextArea
            value={value.description}
            disabled={locked}
            maxLength={1000}
            rows={3}
            placeholder="这个团队负责什么任务"
            onChange={(event) => patch({ description: event.target.value })}
            data-testid="team-description"
          />
        </div>

        <div>
          <Text strong>主 Agent</Text>
          <div className="text-[12px] text-text-secondary mb-4">
            主 Agent 负责拆解任务与最终成稿，只能选择已上线的 Agent。
          </div>
          <Select
            className="w-full"
            value={value.masterAgentId ?? undefined}
            disabled={locked}
            placeholder={
              onlineAgents.length === 0 ? '暂无已上线 Agent' : '选择主 Agent'
            }
            options={onlineAgents.map((agent) => ({
              value: agent.id,
              label: agent.name,
            }))}
            onChange={(masterAgentId: string) =>
              patch({
                masterAgentId,
                memberAgentIds: value.memberAgentIds.filter(
                  (id) => id !== masterAgentId,
                ),
              })
            }
            data-testid="team-master"
          />
        </div>

        <div>
          <Text strong>子 Agent（按顺序执行任务）</Text>
          <div className="text-[12px] text-text-secondary mb-4">
            最多 {MAX_TEAM_MEMBERS} 个。子 Agent 在主 Agent 的计划下执行具体步骤。
          </div>
          <Select
            className="w-full"
            value={undefined}
            disabled={locked || value.memberAgentIds.length >= MAX_TEAM_MEMBERS}
            placeholder="添加子 Agent"
            options={selectableMembers.map((agent) => ({
              value: agent.id,
              label:
                agent.status === 'ONLINE' ? agent.name : `${agent.name}（未上线）`,
            }))}
            onChange={(agentId: string) =>
              patch({ memberAgentIds: [...value.memberAgentIds, agentId] })
            }
            data-testid="team-member-add"
          />
          <div className="mt-8 flex flex-col gap-6">
            {value.memberAgentIds.length === 0 ? (
              <Text type="secondary">尚未选择子 Agent</Text>
            ) : null}
            {value.memberAgentIds.map((agentId, index) => {
              const agent = agentMap.get(agentId);
              return (
                <div
                  key={agentId}
                  className="flex items-center justify-between gap-8 rounded-[6px] border border-border px-8 py-4"
                >
                  <Space size={8}>
                    <Text>{index + 1}.</Text>
                    <Text>{agent?.name ?? agentId}</Text>
                    {agent && agent.status !== 'ONLINE' ? (
                      <Tag color="default">未上线</Tag>
                    ) : null}
                  </Space>
                  <Space size={0}>
                    <Button
                      type="text"
                      size="small"
                      icon={<ArrowUpOutlined />}
                      disabled={locked || index === 0}
                      onClick={() =>
                        patch({
                          memberAgentIds: move(
                            value.memberAgentIds,
                            index,
                            index - 1,
                          ),
                        })
                      }
                    />
                    <Button
                      type="text"
                      size="small"
                      icon={<ArrowDownOutlined />}
                      disabled={
                        locked || index === value.memberAgentIds.length - 1
                      }
                      onClick={() =>
                        patch({
                          memberAgentIds: move(
                            value.memberAgentIds,
                            index,
                            index + 1,
                          ),
                        })
                      }
                    />
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      disabled={locked}
                      onClick={() =>
                        patch({
                          memberAgentIds: value.memberAgentIds.filter(
                            (id) => id !== agentId,
                          ),
                        })
                      }
                    />
                  </Space>
                </div>
              );
            })}
          </div>
        </div>
      </Space>
    );
  },
);

TeamForm.displayName = 'TeamForm';

export default TeamForm;
