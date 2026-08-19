import type { ReactNode } from 'react';
import classNames from 'classnames';

type SettingRowProps = {
  label: string;
  hint?: string;
  last?: boolean;
  children: ReactNode;
};

function SettingRow({ label, hint, last, children }: SettingRowProps) {
  return (
    <div
      className={classNames(
        'flex flex-wrap items-center justify-between gap-12 px-16 py-14',
        last ? '' : 'border-0 border-b border-solid border-border',
      )}
    >
      <div className="min-w-0 flex-1">
        <div className="text-[15px] text-text-primary">{label}</div>
        {hint ? (
          <div className="mt-2 text-[13px] leading-[20px] text-text-secondary">
            {hint}
          </div>
        ) : null}
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  );
}

SettingRow.displayName = 'SettingRow';

export default SettingRow;
