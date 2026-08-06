import { memo } from 'react';
import { Input, Select, Tag, Typography } from 'antd';
import type { Phase2SkillResponse } from '@/contracts/phase2';
import type { ToolCapabilityItem } from '@/services/phase2/internalTypes';

const { Text } = Typography;
const { TextArea } = Input;

export interface SkillFormState {
  name: string;
  description: string;
  instruction: string;
  outputRequirement: string;
  capabilityKeys: string[];
  version: number | null;
  status: Phase2SkillResponse['status'] | null;
}

export function emptySkillFormState(): SkillFormState {
  return {
    name: '',
    description: '',
    instruction: '',
    outputRequirement: '',
    capabilityKeys: [],
    version: null,
    status: null,
  };
}

export function skillToFormState(skill: Phase2SkillResponse): SkillFormState {
  return {
    name: skill.name,
    description: skill.description,
    instruction: skill.instruction,
    outputRequirement: skill.outputRequirement,
    capabilityKeys: [...skill.capabilityKeys],
    version: skill.version,
    status: skill.status,
  };
}

export function validateSkillForm(state: SkillFormState): string | null {
  if (!state.name.trim()) return '请填写 Skill 名称';
  if (!state.instruction.trim()) return '请填写 instruction';
  return null;
}

export interface SkillFormProps {
  value: SkillFormState;
  onChange: (next: SkillFormState) => void;
  capabilities: ToolCapabilityItem[];
  disabled?: boolean;
  readOnly?: boolean;
}

const SkillForm: GenieType.FC<SkillFormProps> = memo(
  ({ value, onChange, capabilities, disabled = false, readOnly = false }) => {
    const locked = disabled || readOnly;
    const availableCaps = capabilities.filter((c) => c.available);
    const patch = (partial: Partial<SkillFormState>) => {
      onChange({
        ...value,
        ...partial
      });
    };

    return (
      <div className="flex flex-col gap-16" data-testid="skill-form">
        <div>
          <Text strong>名称</Text>
          <Input
            className="mt-6"
            value={value.name}
            disabled={locked}
            onChange={(e) => patch({ name: e.target.value })}
            data-testid="skill-name"
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
            data-testid="skill-description"
          />
        </div>
        <div>
          <Text strong>instruction</Text>
          <TextArea
            className="mt-6"
            rows={6}
            value={value.instruction}
            disabled={locked}
            onChange={(e) => patch({ instruction: e.target.value })}
            data-testid="skill-instruction"
          />
        </div>
        <div>
          <Text strong>outputRequirement</Text>
          <TextArea
            className="mt-6"
            rows={3}
            value={value.outputRequirement}
            disabled={locked}
            onChange={(e) => patch({ outputRequirement: e.target.value })}
            data-testid="skill-output-requirement"
          />
        </div>
        <div>
          <Text strong>能力（capabilityKeys）</Text>
          <Select
            className="mt-6 w-full"
            mode="multiple"
            allowClear
            disabled={locked}
            value={value.capabilityKeys}
            options={availableCaps.map((c) => ({
              value: c.key,
              label: c.displayName || c.key,
            }))}
            onChange={(keys) => patch({ capabilityKeys: keys })}
            data-testid="skill-capabilities"
          />
        </div>
        {value.status ? (
          <div className="flex items-center gap-8">
            <Text type="secondary">状态</Text>
            <Tag color={value.status === 'ENABLED' ? 'green' : 'default'}>
              {value.status}
            </Tag>
            {value.version != null ? (
              <Text type="secondary">version {value.version}</Text>
            ) : null}
          </div>
        ) : null}
      </div>
    );
  },
);

SkillForm.displayName = 'SkillForm';

export default SkillForm;
