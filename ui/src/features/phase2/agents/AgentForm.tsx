import { memo, useMemo } from 'react';
import { ConfigProvider, Input, Select } from 'antd';
import type { Phase2SkillResponse } from '@/contracts/phase2';
import type { ToolCapabilityItem } from '@/services/phase2/internalTypes';
import SettingRow from '@/features/settings/SettingRow';
import type { AgentFormState } from './agentFormModel';

const { TextArea } = Input;

const SELECT_THEME = {
  components: {
    Select: {
      multipleItemBg: '#F2F2F7',
      multipleItemColor: '#3A3A3C',
      multipleItemBorderColor: 'transparent',
      optionSelectedBg: '#F2F2F7',
      optionSelectedColor: '#1D1D1F',
    },
  },
};

const editorClassName =
  'agent-fixed-textarea p-0 text-[15px] leading-[22px]';

export interface AgentFormProps {
  value: AgentFormState;
  onChange: (next: AgentFormState) => void;
  skills: Phase2SkillResponse[];
  capabilities: ToolCapabilityItem[];
  disabled?: boolean;
  readOnly?: boolean;
}

function capabilityLabel(item: ToolCapabilityItem): string {
  if (item.displayName?.trim()) {
    return item.displayName;
  }
  return item.key.replace(/^(builtin|mcp):/, '');
}

const AgentForm: GenieType.FC<AgentFormProps> = memo(
  ({
    value,
    onChange,
    skills,
    capabilities,
    disabled = false,
    readOnly = false,
  }) => {
    const locked = disabled || readOnly;
    const skillOptions = useMemo(
      () =>
        skills
          .filter((skill) => skill.status === 'ENABLED' || value.skillIds.includes(skill.id))
          .map((skill) => ({
            value: skill.id,
            label: skill.name,
          })),
      [skills, value.skillIds],
    );
    const availableCaps = capabilities.filter(
      (item) => item.available || value.capabilityKeys.includes(item.key),
    );
    const builtinCaps = availableCaps.filter((item) => item.key.startsWith('builtin:'));
    const mcpCaps = availableCaps.filter((item) => item.key.startsWith('mcp:'));

    const patch = (partial: Partial<AgentFormState>) => {
      onChange({
        ...value,
        ...partial,
      });
    };

    return (
      <div className="flex flex-col" data-testid="agent-form">
        <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
          <SettingRow label="名称" last>
            <Input
              className="w-[260px]"
              value={value.name}
              disabled={locked}
              onChange={(event) => patch({ name: event.target.value })}
              placeholder="给它起个名字"
              data-testid="agent-name"
            />
          </SettingRow>
        </div>

        <h2 className="m-0 mb-8 mt-28 px-4 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
          简介
        </h2>
        <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
          <div className="px-16 py-14">
            <TextArea
              className={editorClassName}
              variant="borderless"
              autoSize={false}
              rows={2}
              style={{ resize: 'none' }}
              value={value.description}
              disabled={locked}
              onChange={(event) => patch({ description: event.target.value })}
              placeholder="一两句话说明它做什么"
              data-testid="agent-description"
            />
          </div>
        </div>

        <h2 className="m-0 mb-8 mt-28 px-4 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
          指令
        </h2>
        <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
          <div className="px-16 py-14">
            <TextArea
              className={editorClassName}
              variant="borderless"
              autoSize={false}
              rows={4}
              style={{ resize: 'none' }}
              value={value.systemPrompt}
              disabled={locked}
              onChange={(event) => patch({ systemPrompt: event.target.value })}
              placeholder="告诉它应该如何思考和回答。"
              data-testid="agent-instructions"
            />
          </div>
        </div>

        <h2 className="m-0 mb-8 mt-28 px-4 text-[13px] font-medium tracking-[0.02em] text-text-tertiary">
          技能与能力
        </h2>
        <div className="overflow-hidden rounded-xl bg-surface shadow-xs">
          <ConfigProvider theme={SELECT_THEME}>
            <SettingRow stacked label="技能">
              <Select
                className="w-full agent-bind-select"
                mode="multiple"
                allowClear
                disabled={locked}
                value={value.skillIds}
                placeholder="选择技能"
                optionFilterProp="label"
                options={skillOptions}
                onChange={(ids) => patch({ skillIds: ids })}
                data-testid="agent-skills"
              />
            </SettingRow>
            <SettingRow stacked label="能力（内置 + MCP）" last>
              <Select
                className="w-full agent-bind-select"
                mode="multiple"
                allowClear
                disabled={locked}
                value={value.capabilityKeys}
                placeholder="选择要使用的能力"
                optionFilterProp="label"
                options={[
                  ...(builtinCaps.length
                    ? [
                        {
                          label: '内置',
                          options: builtinCaps.map((item) => ({
                            value: item.key,
                            label: capabilityLabel(item),
                          })),
                        },
                      ]
                    : []),
                  ...(mcpCaps.length
                    ? [
                        {
                          label: 'MCP',
                          options: mcpCaps.map((item) => ({
                            value: item.key,
                            label: capabilityLabel(item),
                          })),
                        },
                      ]
                    : []),
                ]}
                onChange={(keys) => patch({ capabilityKeys: keys })}
                data-testid="agent-capabilities"
              />
            </SettingRow>
          </ConfigProvider>
        </div>
      </div>
    );
  },
);

AgentForm.displayName = 'AgentForm';

export default AgentForm;
