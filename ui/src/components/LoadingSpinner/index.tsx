import classNames from "classnames";

const LoadingSpinner: GenieType.FC<{
  color?: string;
}> = (props) => {
  const { className, children, color = 'white' } = props;

  return (
    <>
      <div className={classNames('relative size-[1em] shrink-0', className)}>
        <div className="absolute inset-0 rounded-full border-2 border-brand/20 border-t-brand animate-spin box-border" />
        <div
          className="absolute inset-[3px] rounded-full"
          style={{ backgroundColor: color }}
        />
      </div>
      {children}
    </>
  );
};

export default LoadingSpinner;
