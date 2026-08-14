import { describe, expect, it } from 'vitest';
import type { Phase2SkillResponse } from '@/contracts/phase2';
import {
  skillToFormState,
  validateSkillForm,
} from '../SkillForm';

describe('SkillFormNullFieldsTest', () => {
  it('maps null optional strings to empty form fields', () => {
    const skill = {
      id: 'skill-a',
      name: 'brand-guidelines',
      description: 'Apply brand colors',
      instruction: '# Anthropic Brand Styling',
      outputRequirement: null,
      status: 'ENABLED',
      version: 0,
      capabilityKeys: null,
      createdAt: '',
      updatedAt: '',
    } as unknown as Phase2SkillResponse;

    const form = skillToFormState(skill);
    expect(form.outputRequirement).toBe('');
    expect(form.capabilityKeys).toEqual([]);
    expect(validateSkillForm(form)).toBeNull();
    expect(() => form.outputRequirement.trim()).not.toThrow();
  });
});
