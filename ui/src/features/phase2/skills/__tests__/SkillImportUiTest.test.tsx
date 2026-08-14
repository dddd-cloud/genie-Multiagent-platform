import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { Phase2SkillResponse } from '@/contracts/phase2';
import { MvpApiError } from '@/services/apiError';
import * as skillService from '@/services/phase2/skills';
import SkillListPage from '../SkillListPage';
import { validateSkillZipFile, zipSkillFolderFiles } from '../skillPackageZip';

vi.mock('@/services/phase2/skills', () => ({
  listSkills: vi.fn(),
  enableSkill: vi.fn(),
  disableSkill: vi.fn(),
  importSkillPackage: vi.fn(),
}));

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

const skill: Phase2SkillResponse = {
  id: 'skill-a',
  name: 'Skill A',
  description: '',
  instruction: 'a',
  outputRequirement: '',
  status: 'ENABLED',
  version: 1,
  capabilityKeys: [],
  createdAt: '',
  updatedAt: '',
};

function renderList() {
  return render(
    <MemoryRouter initialEntries={['/app/skills']}>
      <Routes>
        <Route path="/app/skills" element={<SkillListPage />} />
        <Route path="/app/skills/:skillId" element={<div>编辑页</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('SkillImportUiTest', () => {
  beforeEach(() => {
    vi.mocked(skillService.listSkills).mockResolvedValue([skill]);
    vi.mocked(skillService.importSkillPackage).mockReset();
  });

  it('shows import package button and zip/folder pickers', async () => {
    renderList();
    fireEvent.click(await screen.findByTestId('skill-import-open'));
    expect(await screen.findByTestId('skill-import-modal')).toBeTruthy();
    expect(screen.getByTestId('skill-import-zip')).toBeTruthy();
    expect(screen.getByTestId('skill-import-folder')).toBeTruthy();
    expect(screen.getByTestId('skill-import-zip-button').textContent).toContain(
      '上传 zip',
    );
    expect(screen.getByTestId('skill-import-folder-button').textContent).toContain(
      '选择文件夹',
    );
  });

  it('uploads a zip through the import API and navigates to the skill', async () => {
    vi.mocked(skillService.importSkillPackage).mockResolvedValue({
      ...skill,
      id: 'skill-imported',
      name: 'imported-example',
      packageMode: 'FILESYSTEM',
    });
    renderList();
    fireEvent.click(await screen.findByTestId('skill-import-open'));
    const zip = new File(['PK'], 'skill.zip', { type: 'application/zip' });
    fireEvent.change(screen.getByTestId('skill-import-zip'), {
      target: { files: [zip] },
    });
    await waitFor(() => {
      expect(skillService.importSkillPackage).toHaveBeenCalled();
    });
    expect(await screen.findByText('编辑页')).toBeTruthy();
  });

  it('shows validation errors from a failed import', async () => {
    vi.mocked(skillService.importSkillPackage).mockRejectedValue(
      new MvpApiError(422, 'SKILL_PACKAGE_INVALID', 'SKILL_PACKAGE_INVALID'),
    );
    renderList();
    fireEvent.click(await screen.findByTestId('skill-import-open'));
    const zip = new File(['PK'], 'skill.zip', { type: 'application/zip' });
    fireEvent.change(screen.getByTestId('skill-import-zip'), {
      target: { files: [zip] },
    });
    expect(await screen.findByTestId('skill-import-error')).toBeTruthy();
  });

  it('rejects non-zip files client-side', () => {
    const txt = new File(['hello'], 'notes.txt', { type: 'text/plain' });
    expect(validateSkillZipFile(txt)).toBe('请上传 zip 格式的 Skill 包');
    const huge = new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'big.zip', {
      type: 'application/zip',
    });
    expect(validateSkillZipFile(huge)).toBe('Skill 包超过 10MB 上限');
  });

  it('rejects a folder without SKILL.md before upload', async () => {
    const file = new File(['readme'], 'README.md', { type: 'text/markdown' });
    Object.defineProperty(file, 'webkitRelativePath', {
      value: 'my-skill/README.md',
    });
    await expect(zipSkillFolderFiles([file])).rejects.toThrow(
      '所选文件夹必须包含 SKILL.md',
    );
  });
});
