import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import classNames from 'classnames';
import type { Phase2ModelResponse } from '@/contracts/phase2';
import { listModels } from '@/services/phase2/models';

type Props = {
  value: string;
  onChange: (name: string) => void;
  disabled?: boolean;
};

function labelOf(item: Phase2ModelResponse) {
  return item.displayName || item.name;
}

const ComposerModelPicker: GenieType.FC<Props> = memo(
  ({ value, onChange, disabled = false }) => {
    const [open, setOpen] = useState(false);
    const [models, setModels] = useState<Phase2ModelResponse[]>([]);
    const rootRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
      const controller = new AbortController();
      listModels(controller.signal)
        .then((items) => {
          setModels(
            (items ?? []).filter(
              (item) => item.name !== 'system-default' && item.available,
            ),
          );
        })
        .catch(() => {
          setModels([]);
        });
      return () => controller.abort();
    }, []);

    useEffect(() => {
      if (!open) {
        return;
      }
      const onDoc = (event: MouseEvent) => {
        if (!rootRef.current?.contains(event.target as Node)) {
          setOpen(false);
        }
      };
      document.addEventListener('mousedown', onDoc);
      return () => document.removeEventListener('mousedown', onDoc);
    }, [open]);

    const selected = useMemo(() => {
      return (
        models.find((item) => item.name === value) ??
        models.find((item) => item.isDefault) ??
        models[0] ??
        null
      );
    }, [models, value]);

    const handleSelect = useCallback(
      (name: string) => {
        onChange(name);
        setOpen(false);
      },
      [onChange],
    );

    const currentLabel = selected ? labelOf(selected) : '选择模型';

    return (
      <div ref={rootRef} className="relative">
        <button
          type="button"
          disabled={disabled || models.length === 0}
          data-testid="composer-model-picker"
          aria-label="选择模型"
          aria-expanded={open}
          className={classNames(
            'max-w-[168px] h-28 px-10 rounded-full border-0 flex items-center gap-4 text-[12px] leading-none transition-colors duration-150',
            disabled || models.length === 0
              ? 'cursor-not-allowed text-[#C7C7CC] bg-transparent'
              : 'cursor-pointer text-text-secondary hover:bg-[#F2F2F7] hover:text-text-primary',
          )}
          onClick={() => setOpen((prev) => !prev)}
        >
          <span className="truncate">{currentLabel}</span>
          <span className="shrink-0 text-[10px] opacity-60" aria-hidden>
            ▾
          </span>
        </button>
        {open ? (
          <div
            role="listbox"
            data-testid="composer-model-menu"
            className="absolute bottom-[36px] right-0 z-20 min-w-[220px] overflow-hidden rounded-2xl border border-black/8 bg-white py-6 shadow-[0_12px_40px_rgba(0,0,0,0.12)]"
          >
            {models.map((item) => {
              const active = selected?.name === item.name;
              return (
                <button
                  key={item.id || item.name}
                  type="button"
                  role="option"
                  aria-selected={active}
                  data-testid={`composer-model-option-${item.name}`}
                  className={classNames(
                    'flex w-full items-center justify-between gap-12 border-0 bg-transparent px-14 py-10 text-left text-[13px] text-text-primary hover:bg-[#F5F5F7]',
                    active ? 'font-medium' : 'font-normal',
                  )}
                  onClick={() => handleSelect(item.name)}
                >
                  <span className="min-w-0 truncate">{labelOf(item)}</span>
                  {active ? (
                    <span className="shrink-0 text-[12px] text-text-primary">
                      ✓
                    </span>
                  ) : null}
                </button>
              );
            })}
          </div>
        ) : null}
      </div>
    );
  },
);

ComposerModelPicker.displayName = 'ComposerModelPicker';

export default ComposerModelPicker;
