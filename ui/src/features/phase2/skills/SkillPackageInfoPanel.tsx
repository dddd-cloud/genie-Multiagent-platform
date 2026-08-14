import { memo } from 'react';
import { Typography } from 'antd';
import { readSkillPackageInfo } from './skillPackageInfo';

const { Text } = Typography;

export interface SkillPackageInfoPanelProps {
  skill: unknown;
}

/**
 * Read-only package metadata when the backend provides it.
 * Does not invent PackageMode — unknown fields show as 未提供.
 */
const SkillPackageInfoPanel: GenieType.FC<SkillPackageInfoPanelProps> = memo(
  ({ skill }) => {
    const info = readSkillPackageInfo(skill);
    const mode = info.packageMode?.trim() || null;
    const hash = info.packageHash?.trim() || null;
    const entrypoints = info.entrypoints ?? [];

    return (
      <div
        className="flex flex-col gap-8 rounded-md border border-border px-12 py-10"
        data-testid="skill-package-info"
      >
        <Text strong>Package</Text>
        <div className="text-[13px] text-text-secondary" data-testid="skill-package-mode">
          PackageMode：
          {mode
            ? `${mode}${
              mode === 'FILESYSTEM' || mode === 'LEGACY_SYNTHETIC'
                ? `（${mode === 'FILESYSTEM' ? 'FILESYSTEM' : 'LEGACY'}）`
                : ''
            }`
            : '未提供'}
        </div>
        <div className="text-[13px] text-text-secondary" data-testid="skill-package-hash">
          packageHash：{hash || '未提供'}
        </div>
        <div data-testid="skill-package-entrypoints">
          <Text type="secondary">entrypoints</Text>
          {entrypoints.length === 0 ? (
            <div className="mt-4 text-[12px] text-text-tertiary">
              当前后端未返回 entrypoints
            </div>
          ) : (
            <ul className="mt-4 text-[12px] text-text-secondary list-disc pl-16">
              {entrypoints.map((ep) => (
                <li key={`${ep.name}-${ep.runtime}-${ep.script}`}>
                  {ep.name} · runtime={ep.runtime} · script={ep.script}
                  {ep.packages && ep.packages.length > 0
                    ? ` · packages=[${ep.packages.join(', ')}]`
                    : ''}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    );
  },
);

SkillPackageInfoPanel.displayName = 'SkillPackageInfoPanel';

export default SkillPackageInfoPanel;
