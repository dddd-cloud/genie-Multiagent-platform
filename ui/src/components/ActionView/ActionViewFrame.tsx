import { LeftOutlined } from "@ant-design/icons";
import classNames from "classnames";

const ActionViewFrame: GenieType.FC<{
  titleNode?: React.ReactNode;
  onClickTitle?: React.MouseEventHandler<HTMLDivElement>;
  footer?: React.ReactNode;
}> = (props) => {
  const { children, className, titleNode, footer, onClickTitle } = props;

  return <>
    {titleNode && <div
      className={classNames("py-8 flex items-center text-text-primary")}
    >
      <LeftOutlined
        className={classNames("mr-4 text-text-secondary", {'cursor-pointer hover:text-text-primary': onClickTitle})}
        onClick={onClickTitle}
      />
      {titleNode}
    </div>}
    <div
      className={classNames('w-full rounded-md border border-solid border-border bg-surface flex flex-col box-border flex-1 h-0 overflow-y-auto', className)}
    >
      {children}
    </div>
    {footer}
  </>;
};

export default ActionViewFrame;