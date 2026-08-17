import { EyeInvisibleOutlined } from '@ant-design/icons';

type PrivacyModeToggleProps = {
  enabled: boolean;
  disabled?: boolean;
  onToggle: () => void;
};

const PrivacyModeToggle: GenieType.FC<PrivacyModeToggleProps> = (props) => {
  const { enabled, disabled, onToggle } = props;
  return (
    <button
      type="button"
      aria-pressed={enabled}
      aria-label="隐私模式"
      title="开启后，此对话不会写入长期记忆和对话笔记"
      disabled={disabled}
      onClick={onToggle}
      className={[
        'inline-flex shrink-0 items-center gap-6 rounded-[8px] border-0 px-10 py-6 text-[13px] leading-[20px] transition-colors duration-150',
        disabled ? 'cursor-default opacity-60' : 'cursor-pointer',
        enabled
          ? 'bg-[#F0F0F2] text-text-primary'
          : 'bg-transparent text-text-secondary hover:bg-[#F5F5F7]',
      ].join(' ')}
    >
      <EyeInvisibleOutlined className="text-[15px]" />
      <span>隐私模式</span>
    </button>
  );
};

export default PrivacyModeToggle;
