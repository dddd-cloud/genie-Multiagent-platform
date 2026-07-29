import React, { useMemo, useRef, useState } from "react";
import { Input, Button, Tooltip } from "antd";
import classNames from "classnames";
import { TextAreaRef } from "antd/es/input/TextArea";
import { getOS } from "@/utils";

const { TextArea } = Input;

type Props = {
  placeholder: string;
  showBtn: boolean;
  disabled: boolean;
  size: string;
  product?: CHAT.Product;
  send: (p: CHAT.TInputInfo) => void;
  dbsShow?: (show: boolean) => void;
};

const GeneralInput: GenieType.FC<Props> = (props) => {
  const { placeholder, showBtn, disabled, product, send, dbsShow } = props;
  const [question, setQuestion] = useState<string>("");
  const [deepThink, setDeepThink] = useState<boolean>(false);
  const textareaRef = useRef<TextAreaRef>(null);
  const tempData = useRef<{
    cmdPress?: boolean;
    compositing?: boolean;
  }>({});

  const questionChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setQuestion(e.target.value);
  };

  const changeThinkStatus = () => {
    setDeepThink(!deepThink);
  };

  const pressEnter: React.KeyboardEventHandler<HTMLTextAreaElement> = () => {
    if (tempData.current.compositing) {
      return;
    }
    // 按住command 回车换行逻辑
    if (tempData.current.cmdPress) {
      const textareaDom = textareaRef.current?.resizableTextArea?.textArea;
      if (!textareaDom) {
        return;
      }
      const { selectionStart, selectionEnd } = textareaDom || {};
      const newValue =
        question.substring(0, selectionStart) +
        "\n" + // 插入换行符
        question.substring(selectionEnd!);

      setQuestion(newValue);
      setTimeout(() => {
        textareaDom.selectionStart = selectionStart! + 1;
        textareaDom.selectionEnd = selectionStart! + 1;
        textareaDom.focus();
      }, 20);
      return;
    }
    // 屏蔽状态，不发
    if (!question || disabled) {
      return;
    }
    send({
      message: question,
      outputStyle: product?.type,
      deepThink,
    });

    setTimeout(() => {
      setQuestion("");
    });
  };

  const sendMessage = () => {
    send({
      message: question,
      outputStyle: product?.type,
      deepThink,
    });
    setQuestion("");
  };

  const enterTip = useMemo(() => {
    return `⏎发送，${getOS() === "Mac" ? "⌘" : "^"} + ⏎ 换行`;
  }, []);

  const canSend = Boolean(question) && !disabled;

  return (
    <div
      className={classNames(
        "rounded-lg border border-border bg-surface overflow-hidden p-[12px] transition-[border-color,box-shadow] duration-150",
        "focus-within:border-brand focus-within:shadow-[0_0_0_3px_rgba(64,64,255,0.12)]",
      )}
    >
      <div className="relative">
        <TextArea
          ref={textareaRef}
          value={question}
          placeholder={placeholder}
          className={classNames(
            "h-62 no-border-textarea border-0 resize-none p-[0px] focus:border-0 bg-transparent text-text-primary",
            showBtn && product ? "indent-86" : "",
          )}
          onChange={questionChange}
          onPressEnter={pressEnter}
          onKeyDown={(event) => {
            tempData.current.cmdPress = event.metaKey || event.ctrlKey;
          }}
          onKeyUp={() => {
            tempData.current.cmdPress = false;
          }}
          onCompositionStart={() => {
            tempData.current.compositing = true;
          }}
          onCompositionEnd={() => {
            tempData.current.compositing = false;
          }}
        />
        {showBtn && product ? (
          <div className="h-[24px] w-[80px] absolute top-0 left-0 flex items-center justify-center rounded-sm bg-brand-soft text-text-primary text-[12px]">
            <i className={`font_family ${product.img} ${product.color} text-[14px]`}></i>
            <div className="ml-[6px]">{product.name}</div>
          </div>
        ) : null}
      </div>
      <div className="h-30 flex justify-between items-center mt-[6px]">
        {showBtn ? (
          <div className="flex items-center">
            <Button
              color={deepThink ? "primary" : "default"}
              variant="outlined"
              className={classNames(
                "text-[12px] p-[8px] h-[28px] transition-colors duration-150",
                deepThink
                  ? "hover:text-brand"
                  : "hover:text-text-primary hover:border-border-strong",
              )}
              onClick={changeThinkStatus}
            >
              <i className="font_family icon-shendusikao"></i>
              <span className="ml-[-4px]">深度研究</span>
            </Button>
            {product?.type === "dataAgent" && (
              <Tooltip placement="right" title="查看知识库">
                <i
                  className="font_family icon-zhishiku cursor-pointer text-brand text-[18px] ml-[8px] border border-brand rounded-tr-md rounded-bl-md p-[3px] hover:bg-brand-soft transition-colors duration-150"
                  onClick={() => dbsShow && dbsShow(true)}
                ></i>
              </Tooltip>
            )}
          </div>
        ) : (
          <div></div>
        )}
        <div className="flex items-center">
          <span className="text-[12px] text-text-tertiary mr-8 flex items-center">
            {enterTip}
          </span>
          <Tooltip title="发送">
            <button
              type="button"
              aria-label="发送"
              disabled={!canSend}
              className={classNames(
                "font_family icon-fasongtianchong border-0 bg-transparent p-0 leading-none transition-colors duration-150",
                canSend
                  ? "cursor-pointer text-brand hover:text-brand-hover"
                  : "cursor-not-allowed text-text-tertiary",
              )}
              onClick={sendMessage}
            />
          </Tooltip>
        </div>
      </div>
    </div>
  );
};

export default GeneralInput;
