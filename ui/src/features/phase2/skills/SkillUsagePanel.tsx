import { memo, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Typography } from 'antd';
import type { Phase2AgentResponse } from '@/contracts/phase2';

const { Text } = Typography;

export interface SkillUsagePanelProps {
  skillId: string;
  agents: Phase2AgentResponse[];
}

/** Derives usage from loaded agents' skillIds — no extra API. */
const SkillUsagePanel: GenieType.FC<SkillUsagePanelProps> = memo(
  ({ skillId, agents }) => {
    const users = useMemo(
      () => agents.filter((a) => a.skillIds.includes(skillId)),
      [agents, skillId],
    );

    return (
      <div
        className="rounded-[10px] border border-border p-16 bg-[#FAFAFA]"
        data-testid="skill-usage-panel"
      >
        <Text strong>使用该 Skill 的 Agent</Text>
        <div className="mt-10 flex flex-col gap-6">
          {users.length === 0 ? (
            <Text type="secondary">当前没有 Agent 引用此 Skill</Text>
          ) : (
            users.map((agent) => (
              <div key={agent.id} className="flex items-center gap-8">
                <Link to={`/app/agents/${agent.id}`}>{agent.name}</Link>
                <Text type="secondary">
                  顺序 #{agent.skillIds.indexOf(skillId) + 1}
                </Text>
              </div>
            ))
          )}
        </div>
      </div>
    );
  },
);

SkillUsagePanel.displayName = 'SkillUsagePanel';

export default SkillUsagePanel;
