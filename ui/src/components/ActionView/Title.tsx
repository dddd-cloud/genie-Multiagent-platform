const Title: GenieType.FC<{
  onClose?: () => void;
}> = (props) => {
  const { children, onClose } = props;

  return (
    <div className="text-[16px] font-semibold text-text-primary flex items-center justify-between mb-[12px]">
      {children}
      <button
        type="button"
        aria-label="关闭"
        className="inline-flex items-center justify-center size-28 rounded-sm text-text-tertiary hover:bg-surface hover:text-text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand transition-colors duration-150"
        onClick={onClose}
      >
        <i className="font_family icon-guanbi cursor-pointer"></i>
      </button>
    </div>
  );
};

export default Title;
