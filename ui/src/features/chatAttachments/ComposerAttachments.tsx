import classNames from 'classnames';
import { iconType } from '@/utils/constants';
import txtIcon from '@/assets/icon/txt.png';
import {
  COMPOSER_ATTACHMENT_LABELS,
  type ComposerAttachmentType,
} from './constants';
import type { ComposerAttachment } from './useComposerAttachments';

type Props = {
  attachments: ComposerAttachment[];
  onRemove: (clientId: string) => void;
};

function chipIcon(type: string): string {
  if (type === 'md' || type === 'py' || type === 'json' || type === 'txt') {
    return iconType.txt || txtIcon;
  }
  return iconType[type] || txtIcon;
}

function UploadRing({ progress }: { progress: number }) {
  const radius = 13;
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.min(100, Math.max(0, progress));
  const offset = circumference - (clamped / 100) * circumference;
  return (
    <svg
      className="absolute inset-0 size-full -rotate-90"
      viewBox="0 0 32 32"
      aria-hidden
      data-testid="composer-attachment-progress"
    >
      <circle
        cx="16"
        cy="16"
        r={radius}
        fill="none"
        stroke="currentColor"
        strokeWidth="2.5"
        className="text-black/15"
      />
      <circle
        cx="16"
        cy="16"
        r={radius}
        fill="none"
        stroke="currentColor"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeDasharray={circumference}
        strokeDashoffset={offset}
        className="text-[#8E8E93] transition-[stroke-dashoffset] duration-200 ease-linear"
      />
    </svg>
  );
}

export default function ComposerAttachments({ attachments, onRemove }: Props) {
  if (attachments.length === 0) {
    return null;
  }
  return (
    <div className="mb-8 flex flex-wrap gap-8" data-testid="composer-attachments">
      {attachments.map((file) => {
        const pending = file.status !== 'ready';
        const label =
          COMPOSER_ATTACHMENT_LABELS[file.type as ComposerAttachmentType] ??
          file.type.toUpperCase();
        return (
          <div
            key={file.clientId}
            data-testid="composer-attachment-chip"
            data-status={file.status}
            className={classNames(
              'group relative flex h-[52px] w-[188px] items-center gap-8 rounded-[16px] border px-8 pr-12',
              pending
                ? 'border-transparent bg-[#F2F2F7] text-[#8E8E93]'
                : 'border-[#E8E8ED] bg-white text-text-primary',
            )}
          >
            <div className="relative size-32 shrink-0">
              <img
                src={chipIcon(file.type)}
                alt=""
                className={classNames(
                  'size-32 rounded-[6px] object-contain',
                  pending ? 'opacity-40 grayscale' : '',
                )}
              />
              {file.status === 'uploading' ? <UploadRing progress={file.progress} /> : null}
            </div>
            <div className={classNames('min-w-0 flex-1', pending ? 'opacity-55' : '')}>
              <div className="truncate text-[13px] leading-[18px] font-medium">{file.name}</div>
              <div className="text-[11px] leading-[16px] text-[#8E8E93]">
                {file.status === 'error' ? file.errorMessage || '上传失败' : label}
              </div>
            </div>
            <button
              type="button"
              aria-label={`移除 ${file.name}`}
              data-testid="composer-attachment-remove"
              className="absolute -right-6 -top-6 flex size-18 items-center justify-center rounded-full border-0 bg-[#8E8E93] text-white cursor-pointer opacity-0 transition-opacity duration-150 group-hover:opacity-100 focus-visible:opacity-100"
              onClick={() => onRemove(file.clientId)}
            >
              <span className="block text-[12px] leading-none">×</span>
            </button>
          </div>
        );
      })}
    </div>
  );
}
