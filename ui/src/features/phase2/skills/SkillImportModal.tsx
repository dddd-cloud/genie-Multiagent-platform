import { memo, useRef, useState, type ChangeEvent, type InputHTMLAttributes } from 'react';
import { Alert, Button, Modal, Space, Typography, message } from 'antd';
import type { Phase2SkillResponse } from '@/contracts/phase2';
import { importSkillPackage } from '@/services/phase2/skills';
import { phase2ErrorMessage } from '../phase2UiError';
import { validateSkillZipFile, zipSkillFolderFiles } from './skillPackageZip';

const { Text } = Typography;

export interface SkillImportModalProps {
  open: boolean;
  skillId?: string;
  onClose: () => void;
  onImported: (skill: Phase2SkillResponse) => void;
}

const SkillImportModal: GenieType.FC<SkillImportModalProps> = memo(
  ({ open, skillId, onClose, onImported }) => {
    const zipInputRef = useRef<HTMLInputElement>(null);
    const folderInputRef = useRef<HTMLInputElement>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const resetInputs = () => {
      if (zipInputRef.current) zipInputRef.current.value = '';
      if (folderInputRef.current) folderInputRef.current.value = '';
    };

    const runImport = async (file: File) => {
      setBusy(true);
      setError(null);
      try {
        const imported = await importSkillPackage(file, skillId);
        if (!imported) {
          throw new Error('导入失败');
        }
        message.success(skillId ? '已更新 Skill 包' : '已导入 Skill 包');
        resetInputs();
        onImported(imported);
      } catch (err: unknown) {
        setError(phase2ErrorMessage(err));
      } finally {
        setBusy(false);
      }
    };

    const onZipChange = async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      resetInputs();
      if (!file) return;
      const invalid = validateSkillZipFile(file);
      if (invalid) {
        setError(invalid);
        return;
      }
      await runImport(file);
    };

    const onFolderChange = async (event: ChangeEvent<HTMLInputElement>) => {
      const list = event.target.files;
      const files = list ? Array.from(list) : [];
      resetInputs();
      if (files.length === 0) return;
      setBusy(true);
      setError(null);
      try {
        const zip = await zipSkillFolderFiles(files);
        await runImport(zip);
      } catch (err: unknown) {
        setBusy(false);
        setError(phase2ErrorMessage(err));
      }
    };

    return (
      <Modal
        title={skillId ? '更新 Skill 包' : '导入 Skill 包'}
        open={open}
        onCancel={() => {
          if (!busy) {
            setError(null);
            resetInputs();
            onClose();
          }
        }}
        footer={null}
        destroyOnHidden
        data-testid="skill-import-modal"
      >
        <Space direction="vertical" size={12} className="w-full">
          <Text type="secondary">
            上传包含 SKILL.md 的 zip，或选择一个 Skill 文件夹。GitHub 风格包（仅 name / description，附带 LICENSE.txt、README.md）也可以导入，多余文件会被忽略。
          </Text>
          {error ? (
            <Alert
              type="error"
              showIcon
              message={error}
              data-testid="skill-import-error"
            />
          ) : null}
          <Space wrap>
            <Button
              type="primary"
              loading={busy}
              disabled={busy}
              onClick={() => zipInputRef.current?.click()}
              data-testid="skill-import-zip-button"
            >
              上传 zip
            </Button>
            <Button
              loading={busy}
              disabled={busy}
              onClick={() => folderInputRef.current?.click()}
              data-testid="skill-import-folder-button"
            >
              选择文件夹
            </Button>
          </Space>
          <input
            ref={zipInputRef}
            type="file"
            accept=".zip,application/zip,application/x-zip-compressed"
            hidden
            data-testid="skill-import-zip"
            onChange={(event) => void onZipChange(event)}
          />
          <input
            ref={folderInputRef}
            type="file"
            hidden
            multiple
            data-testid="skill-import-folder"
            {...({
              webkitdirectory: '',
              directory: '',
            } as InputHTMLAttributes<HTMLInputElement>)}
            onChange={(event) => void onFolderChange(event)}
          />
        </Space>
      </Modal>
    );
  },
);

SkillImportModal.displayName = 'SkillImportModal';

export default SkillImportModal;
