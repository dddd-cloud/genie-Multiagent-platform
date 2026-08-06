import { FC, type ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import AttachmentList from "@/components/AttachmentList";
import LoadingDot from "@/components/LoadingDot";
import LoadingSpinner from "@/components/LoadingSpinner";
import { buildAction, getIcon, buildAttachment } from "@/utils/chat";

type Props = {
  chat: CHAT.ChatItem;
  deepThink: boolean;
  changeTask?: (task: CHAT.Task) => void;
  changeFile?: (file: CHAT.TFile) => void;
  changePlan?: () => void;
  /** Rendered above the final answer body (e.g. orchestration work panel). */
  beforeResponse?: ReactNode;
};

const PlanSection: FC<{ plan: CHAT.PlanItem[] }> = ({ plan }) => (
  <div>
    <div className="text-[16px] font-semibold text-text-primary mb-[8px]">任务计划</div>
    {plan.map((p, i) => (
      <div key={i} className="mb-[8px]">
        <div className="h-[22px] text-text-secondary text-[15px] font-medium flex items-center mb-[5px]">
          <div className="w-[6px] h-[6px] rounded-[50%] bg-text-primary mx-8"></div>
          {p.name}
        </div>
        <div className="ml-[22px] text-[15px] text-text-primary">
          {p.list.map((step, j) => (
            <div key={j} className="leading-[22px]">
              {j + 1}.{step}
            </div>
          ))}
        </div>
      </div>
    ))}
  </div>
);

const ToolItem: FC<{
  tool: CHAT.Task;
  changePlan?: () => void;
  changeActiveChat: (task: CHAT.Task) => void;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ tool, changePlan, changeActiveChat, changeFile }) => {
  const actionInfo = buildAction(tool);
  switch (tool.messageType) {
    case "plan": {
      const completedIndex = tool.plan?.stepStatus.lastIndexOf("completed") || 0;
      return (
        <div
          className="mt-[8px] flex items-center px-10 py-6 bg-surface-subtle w-fit rounded-lg cursor-pointer overflow-hidden max-w-full border border-border"
          onClick={() => changePlan?.()}
        >
          <i className={`font_family ${getIcon(tool.messageType)}`}></i>
          <div className="px-8 flex items-center overflow-hidden">
            <div className="shrink-1 text-text-primary">已完成</div>
            <div className="text-text-secondary text-[13px] flex-1 overflow-hidden whitespace-nowrap text-ellipsis ml-[8px]">
              {tool.plan?.steps[completedIndex]}
            </div>
          </div>
        </div>
      );
    }
    case "tool_thought": {
      return (
        <div className="rounded-md bg-surface-subtle px-12 py-8 mt-[8px] border border-border">
          <div className="mb-[4px] text-text-primary">
            <i className="font_family icon-juli"></i>
            <span className="ml-[4px]">思考过程</span>
          </div>
          <div className="text-text-secondary text-[13px] leading-[20px]">
            {tool.toolThought}
          </div>
        </div>
      );
    }
    case "browser": {
      return (
        <div className="mt-[8px]">
          {tool.resultMap?.steps
            .filter((s) => s.status !== "completed")
            .map((s, idx) => (
              <div key={idx}>
                <i className={`font_family ${getIcon(tool.messageType)}`}></i>
                <div>
                  <div>{actionInfo.action}</div>
                  <div>{s.goal}</div>
                </div>
              </div>
            ))}
        </div>
      );
    }
    case "task_summary": {
      return (
        <div className="mt-[8px]">
          <div className="mb-[8px]">{tool.resultMap.taskSummary}</div>
          <AttachmentList
            files={buildAttachment(tool.resultMap.fileList!)}
            preview={true}
            review={changeFile}
          />
        </div>
      );
    }
    default: {
      const loadingType = ["html", "markdown", "data_analysis"];
      const loading =
        !tool.resultMap?.isFinal &&
        ((tool.messageType === "deep_search" &&
          (tool.resultMap.messageType === "extend" ||
            tool.resultMap.messageType === "report")) ||
          loadingType.includes(tool.messageType));
      return (
        <div
          className="mt-[8px] flex items-center px-10 py-6 bg-surface-subtle w-fit rounded-lg cursor-pointer overflow-hidden max-w-full border border-border"
          onClick={() => changeActiveChat(tool)}
        >
          {loading ? (
            <LoadingSpinner color="#FAFAFC"/>
          ) : (
            <i
              className={`font_family ${getIcon(
                tool.messageType === "deep_search" &&
                  tool.resultMap.messageType === "report"
                  ? "file"
                  : tool.messageType
              )}`}
            ></i>
          )}
          <div className="px-8 flex items-center overflow-hidden">
            <div className="shrink-0 text-text-primary">{actionInfo.action}</div>
            <div className="text-text-secondary text-[13px] overflow-hidden whitespace-nowrap text-ellipsis flex-1 ml-[8px]">
              {actionInfo.name}
            </div>
          </div>
        </div>
      );
    }
  }
};

const TimeLineContent: FC<{
  tasks: CHAT.Task[];
  isReactType: boolean;
  changeActiveChat: (task: CHAT.Task) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ tasks, isReactType, changeActiveChat, changePlan, changeFile }) => (
  <>
    {tasks.map((t, i) => (
      <div key={i} className="overflow-hidden">
        {!isReactType ? <div className="font-medium text-text-primary">{t.task}</div> : null}
        {(t.children || []).map((tool, j) => (
          <div key={j}>
            <ToolItem
              tool={tool}
              changePlan={changePlan}
              changeActiveChat={changeActiveChat}
              changeFile={changeFile}
            />
          </div>
        ))}
      </div>
    ))}
  </>
);

const TimeLine: FC<{
  chat: CHAT.ChatItem;
  isReactType: boolean;
  changeActiveChat: (task: CHAT.Task) => void;
  changePlan?: () => void;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ chat, isReactType, changeActiveChat, changePlan, changeFile }) => (
  <>
    {chat.tasks.map((t, i) => {
      const lastTask = i === chat.tasks.length - 1;
      return (
        <div className="w-full flex" key={i}>
          {!isReactType ? (
            <div className="w-[30px] mt-[2px] mb-[8px] relative shrink-0 overflow-hidden">
              {lastTask && chat.loading ? (
                <LoadingSpinner/>
              ) : (
                <i className="font_family icon-yiwanchengtianchong text-brand text-[16px] absolute top-[-4px] left-0"></i>
              )}
              <div className="h-full w-[1px] border-dashed border-l-[1px] border-border ml-[7px] "></div>
            </div>
          ) : null}
          <div className="flex-1 mb-[8px] overflow-hidden">
            <TimeLineContent
              tasks={t}
              isReactType={isReactType}
              changeActiveChat={changeActiveChat}
              changePlan={changePlan}
              changeFile={changeFile}
            />
          </div>
        </div>
      );
    })}
  </>
);

const ConclusionSection: FC<{
  chat: CHAT.ChatItem;
  changeFile?: (file: CHAT.TFile) => void;
}> = ({ chat, changeFile }) => {
  const summary =
    chat.conclusion?.resultMap?.taskSummary ||
    chat.conclusion?.result ||
    "任务已完成";
  return (
    <div className="mb-[8px]">
      <div className="mb-[8px] text-text-primary">{summary}</div>
      <AttachmentList
        files={buildAttachment(chat.conclusion?.resultMap.fileList || [])}
        preview={true}
        review={changeFile}
      />
    </div>
  );
};

const Dialogue: FC<Props> = (props) => {
  const { chat, deepThink, changeTask, changeFile, changePlan, beforeResponse } =
    props;
  const isReactType = !deepThink;

  const changeActiveChat = (task: CHAT.Task) => {
    changeTask?.(task);
  };

  return (
    <div className="h-full text-[14px] font-normal flex flex-col text-text-primary">
      {(chat.files || []).length ? (
        <div className="w-full mt-[24px] justify-end">
          <AttachmentList files={chat.files} preview={false} />
        </div>
      ) : null}
      {chat.query ? (
        <div className="w-full mt-[24px] flex justify-end">
          <div className="max-w-[80%] bg-brand text-white px-12 py-8 rounded-lg rounded-tr-lg rounded-br-sm rounded-bl-lg">
            {chat.query}
          </div>
        </div>
      ) : null}
      {chat.tip ? (
        <div className="w-full rounded-md mt-[24px] text-text-secondary">{chat.tip}</div>
      ) : null}
      {!isReactType && chat.thought ? (
        <div className="w-full px-12 py-8 bg-surface-subtle rounded-md mt-[24px] border border-border">
          <div>{chat.thought}</div>
        </div>
      ) : null}
      {!isReactType && chat.planList?.length ? (
        <div className="w-full px-12 py-8 rounded-md mt-[24px] bg-surface-subtle border border-border">
          <PlanSection plan={chat.planList} />
        </div>
      ) : null}
      {chat.tasks.length ? (
        <div className="w-full mt-[24px]">
          <TimeLine
            chat={chat}
            isReactType={isReactType}
            changeActiveChat={changeActiveChat}
            changePlan={changePlan}
            changeFile={changeFile}
          />
        </div>
      ) : null}
      {beforeResponse ? (
        <div className="w-full mt-[24px]">{beforeResponse}</div>
      ) : null}
      {chat.conclusion ? (
        <div className="w-full">
          <ConclusionSection chat={chat} changeFile={changeFile} />
        </div>
      ) : null}
      {!chat.conclusion && chat.response ? (
        <div className="w-full mt-[24px]">
          <div className="mb-[8px] markdown-body text-[15px] leading-[24px] text-text-primary [&_h1]:text-[18px] [&_h1]:font-semibold [&_h1]:mb-8 [&_h2]:text-[16px] [&_h2]:font-semibold [&_h2]:mb-8 [&_h3]:text-[15px] [&_h3]:font-semibold [&_h3]:mb-6 [&_p]:mb-8 [&_strong]:font-semibold">
            <ReactMarkdown remarkPlugins={[remarkGfm]} skipHtml>
              {chat.response}
            </ReactMarkdown>
          </div>
        </div>
      ) : null}
      {chat.loading ? <LoadingDot /> : null}
    </div>
  );
};

export default Dialogue;
