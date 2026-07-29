import { FC } from "react";
import LoadingDot from "@/components/LoadingDot";
import DataChat from "@/components/DataChat";

type Props = {
  chat: Record<string, any>;
};

const DataDialogue: FC<Props> = (props) => {
  const { chat } = props;

  function renderBreakText(text: string) {
    return (
      <>
        <div className="font-semibold text-[16px] text-text-primary mb-[8px]">思考过程</div>
        {text.split("\n").map((seg: string, i: number) => (
          <span key={i}>
            {seg}
            {i !== text.split("\n").length - 1 && <br />}
          </span>
        ))}
      </>
    );
  }

  return (
    <div className="h-full text-[14px] font-normal flex flex-col text-text-primary">
      {chat.query ? (
        <div className="w-full mt-[24px] flex justify-end">
          <div className="max-w-[80%] bg-brand text-white px-12 py-8 rounded-lg rounded-tr-lg rounded-br-sm rounded-bl-lg">
            {chat.query}
          </div>
        </div>
      ) : null}
      <div className="border border-border mt-[24px] bg-surface-subtle rounded-md p-12">
        {chat.think ? <div className="w-full text-text-primary">{renderBreakText(chat.think)}</div> : null}
        {chat.chartData && (
          <div className="font-semibold text-[16px] text-text-primary mt-[18px] mb-[-10px]">
            输出结果
          </div>
        )}
        {chat.chartData?.map((n: Record<string, any> | undefined, index: number) => {
          return <DataChat key={index} data={n} />;
        })}
        {chat.error?.length > 0 && (
          <div className="leading-[22px] text-text-primary mt-[20px]">
            <span className="font-medium">回答失败，没能理解您的意图。</span>
          </div>
        )}
        {chat.loading ? <LoadingDot /> : null}
      </div>
    </div>
  );
};

export default DataDialogue;
