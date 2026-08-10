import { memo, useMemo } from 'react';
import {
  Button,
  Input,
  Radio,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import type {
  Phase2ModelResponse,
  Phase2SkillResponse,
} from '@/contracts/phase2';
import type { ToolCapabilityItem } from '@/services/phase2/internalTypes';
import {
  moveSkillId,
  parsePromptConfigText,
  type AgentFormState,
} from './agentFormModel';
import PromptPreviewPanel from './PromptPreviewPanel';

const { Text } = Typography;
const { TextArea } = Input;

export interface AgentFormProps {
  value: AgentFormState;
  onChange: (next: AgentFormState) => void;
  models: Phase2ModelResponse[];
  skills: Phase2SkillResponse[];
  capabilities: ToolCapabilityItem[];
  disabled?: boolean;
  readOnly?: boolean;
}

const AgentForm: GenieType.FC<AgentFormProps> = memo(
  ({
    value,
    onChange,
    models,
    skills,
    capabilities,
    disabled = false,
    readOnly = false,
  }) => {
    const locked = disabled || readOnly;
    const skillMap = useMemo(
      () => new Map(skills.map((s) => [s.id, s])),
      [skills],
    );
    const availableModels = models.filter((m) => m.available);
    const availableCaps = capabilities.filter((c) => c.available);
    const unselectedSkills = skills.filter((s) => !value.skillIds.includes(s.id));

    const patch = (partial: Partial<AgentFormState>) => {
      onChange({
        ...value,
        ...partial
      });
    };

    const jsonError =
      value.promptMode === 'STRUCTURED'
        ? (() => {
          const parsed = parsePromptConfigText(
            value.promptConfigText,
            'STRUCTURED',
          );
          return parsed.ok ? null : parsed.error;
        })()
        : null;

    return (
      <div className="flex flex-col gap-16" data-testid="agent-form">
        <div>
          <Text strong>名称</Text>
          <Input
            className="mt-6"
            value={value.name}
            disabled={locked}
            onChange={(e) => patch({ name: e.target.value })}
            placeholder="Agent 名称"
            data-testid="agent-name"
          />
        </div>

        <div>
          <Text strong>描述</Text>
          <TextArea
            className="mt-6"
            rows={2}
            value={value.description}
            disabled={locked}
            onChange={(e) => patch({ description: e.target.value })}
            placeholder="描述"
            data-testid="agent-description"
          />
        </div>

        <div>
          <Text strong>Prompt 模式</Text>
          <div className="mt-6">
            <Radio.Group
              value={value.promptMode}
              disabled={locked}
              onChange={(e) => patch({ promptMode: e.target.value })}
              optionType="button"
              buttonStyle="solid"
              options={[
                {
                  label: 'STRUCTURED',
                  value: 'STRUCTURED'
                },
                {
                  label: 'RAW',
                  value: 'RAW'
                },
              ]}
              data-testid="agent-prompt-mode"
            />
          </div>
        </div>

        {value.promptMode === 'STRUCTURED' ? (
          <div>
            <Text strong>promptConfig（JSON 对象）</Text>
            <TextArea
              className="mt-6 font-mono"
              rows={8}
              value={value.promptConfigText}
              disabled={locked}
              status={jsonError ? 'error' : undefined}
              onChange={(e) => patch({ promptConfigText: e.target.value })}
              data-testid="agent-prompt-config"
            />
            {jsonError ? (
              <Text type="danger" className="mt-4 block" data-testid="agent-json-error">
                {jsonError}
              </Text>
            ) : null}
          </div>
        ) : (
          <div>
            <Text strong>systemPrompt</Text>
            <TextArea
              className="mt-6"
              rows={8}
              value={value.systemPrompt}
              disabled={locked}
              onChange={(e) => patch({ systemPrompt: e.target.value })}
              data-testid="agent-system-prompt"
            />
          </div>
        )}

        <PromptPreviewPanel formState={value} />

        <div>
          <Text strong>模型</Text>
          <Select
            className="mt-6 w-full"
            allowClear
            disabled={locked}
            value={value.modelName ?? undefined}
            placeholder="选择可用模型"
            options={availableModels.map((m) => ({
              value: m.name,
              label: m.isDefault ? `${m.displayName}（默认）` : m.displayName,
            }))}
            onChange={(v) => patch({ modelName: v ?? null })}
            data-testid="agent-model"
          />
        </div>

        <div>
          <div className="flex items-center justify-between gap-8 mb-6">
            <Text strong>Skill（有序）</Text>
            <Select
              className="min-w-[220px]"
              disabled={locked || unselectedSkills.length === 0}
              placeholder="添加 Skill"
              value={undefined}
              options={unselectedSkills.map((s) => ({
                value: s.id,
                label: s.name,
              }))}
              onChange={(id: string) => {
                if (!id || value.skillIds.includes(id)) return;
                patch({ skillIds: [...value.skillIds, id] });
              }}
              data-testid="agent-skill-add"
            />
          </div>
          <div className="flex flex-col gap-6" data-testid="agent-skill-list">
            {value.skillIds.length === 0 ? (
              <Text type="secondary">尚未选择 Skill</Text>
            ) : (
              value.skillIds.map((id, index) => {
                const skill = skillMap.get(id);
                return (
                  <div
                    key={id}
                    className="flex items-center gap-8 rounded-[8px] border border-border px-10 py-8"
                    data-testid={`agent-skill-row-${id}`}
                  >
                    <Tag>{index + 1}</Tag>
                    <span className="flex-1 min-w-0 truncate">
                      {skill?.name ?? id}
                    </span>
                    <Button
                      size="small"
                      icon={<ArrowUpOutlined />}
                      disabled={locked || index === 0}
                      onClick={() =>
                        patch({skillIds: moveSkillId(value.skillIds, index, 'up'),})
                      }
                      data-testid={`agent-skill-up-${id}`}
                    />
                    <Button
                      size="small"
                      icon={<ArrowDownOutlined />}
                      disabled={locked || index === value.skillIds.length - 1}
                      onClick={() =>
                        patch({skillIds: moveSkillId(value.skillIds, index, 'down'),})
                      }
                      data-testid={`agent-skill-down-${id}`}
                    />
                    <Button
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      disabled={locked}
                      onClick={() =>
                        patch({skillIds: value.skillIds.filter((x) => x !== id),})
                      }
                    />
                  </div>
                );
              })
            )}
          </div>
        </div>

        <div>
          <Text strong>能力（内置 + MCP）</Text>
          <Text type="secondary" className="mt-4 block">
            MCP 工具在此多选绑定，没有单独的「绑定 MCP」按钮
          </Text>
          <Select
            className="mt-6 w-full"
            mode="multiple"
            allowClear
            disabled={locked}
            value={value.capabilityKeys}
            placeholder="选择内置能力或 MCP 工具"
            optionFilterProp="label"
            options={[
              {
                label: '内置能力',
                options: availableCaps
                  .filter((c) => c.key.startsWith('builtin:'))
                  .map((c) => ({
                    value: c.key,
                    label: c.displayName || c.key,
                  })),
              },
              {
                label: 'MCP 工具',
                options: availableCaps
                  .filter((c) => c.key.startsWith('mcp:'))
                  .map((c) => ({
                    value: c.key,
                    label: c.displayName || c.key,
                  })),
              },
            ]}
            onChange={(keys) => patch({ capabilityKeys: keys })}
            data-testid="agent-capabilities"
          />
        </div>

        {value.status ? (
          <Space>
            <Text type="secondary">状态</Text>
            <Tag color={value.status === 'ONLINE' ? 'green' : 'default'}>
              {value.status}
            </Tag>
            {value.version != null ? (
              <Text type="secondary">version {value.version}</Text>
            ) : null}
          </Space>
        ) : null}
      </div>
    );
  },
);

AgentForm.displayName = 'AgentForm';

export default AgentForm;
