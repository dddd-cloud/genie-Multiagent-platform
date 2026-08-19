import React, { useEffect, useRef, useState, type ReactNode } from "react";
import { Input, Button, Tooltip } from "antd";
import classNames from "classnames";
import { TextAreaRef } from "antd/es/input/TextArea";
import { useUserSettings } from "@/features/userSettings/useUserSettings";

const { TextArea } = Input;

type Props = {
  placeholder: string;
  showBtn: boolean;
  disabled: boolean;
  size: string;
  product?: CHAT.Product;
  send: (p: CHAT.TInputInfo) => void;
  dbsShow?: (show: boolean) => void;
  leftExtra?: ReactNode;
  /** Live generation: replace send with a ChatGPT-style stop control. */
  running?: boolean;
  onStop?: () => void;
};

const GeneralInput: GenieType.FC<Props> = (props) => {
  const {
    placeholder,
    showBtn,
    disabled,
    product,
    send,
    dbsShow,
    leftExtra,
    running,
    onStop,
  } = props;
  const { preferences, status: preferencesStatus } = useUserSettings();
  const [question, setQuestion] = useState<string>("");
  const [deepThink, setDeepThink] = useState<boolean>(false);
  const deepThinkTouchedRef = useRef(false);
  const textareaRef = useRef<TextAreaRef>(null);
  const tempData = useRef<{
    cmdPress?: boolean;
    compositing?: boolean;
  }>({});

  const questionChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setQuestion(e.target.value);
  };

  /**
   * The saved default is applied only once preferences are known to be loaded, and never after the
   * user has flipped the toggle — a late response must not undo their choice.
   */
  useEffect(() => {
    if (preferencesStatus === 'ready' && !deepThinkTouchedRef.current) {
      setDeepThink(preferences.defaultDeepThink);
    }
  }, [preferences.defaultDeepThink, preferencesStatus]);

  const changeThinkStatus = () => {
    deepThinkTouchedRef.current = true;
    setDeepThink(!deepThink);
  };

  const insertNewline = () => {
    const textareaDom = textareaRef.current?.resizableTextArea?.textArea;
    if (!textareaDom) {
      return;
    }
    const { selectionStart, selectionEnd } = textareaDom;
    const start = selectionStart ?? question.length;
    const end = selectionEnd ?? question.length;
    const newValue =
      question.substring(0, start) + "\n" + question.substring(end);
    setQuestion(newValue);
    setTimeout(() => {
      textareaDom.selectionStart = start + 1;
      textareaDom.selectionEnd = start + 1;
      textareaDom.focus();
    }, 20);
  };

  const pressEnter: React.KeyboardEventHandler<HTMLTextAreaElement> = (event) => {
    if (tempData.current.compositing) {
      return;
    }
    if (event.shiftKey) {
      event.preventDefault();
      insertNewline();
      return;
    }
    if (event.metaKey || event.ctrlKey || tempData.current.cmdPress) {
      event.preventDefault();
      insertNewline();
      return;
    }
    event.preventDefault();
    if (!question || disabled || running) {
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

  const canSend = Boolean(question) && !disabled;

  return (
    <div
      className={classNames(
        "rounded-[28px] border border-black/8 bg-white overflow-visible p-[14px] pb-[10px] shadow-[0_1px_2px_rgba(0,0,0,0.04)]",
        "transition-[border-color,box-shadow] duration-150",
        "focus-within:border-black/15 focus-within:shadow-[0_8px_28px_rgba(0,0,0,0.06)]",
      )}
    >
      <div className="relative">
        <TextArea
          ref={textareaRef}
          value={question}
          aria-label={placeholder.trim() ? placeholder : '消息'}
          autoSize={{ minRows: 1, maxRows: 20 }}
          className={classNames(
            "chat-input-textarea no-border-textarea border-0 p-[0px] focus:border-0 bg-transparent text-text-primary text-[15px] leading-[22px]",
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
          <div className="h-[24px] w-[80px] absolute top-0 left-0 flex items-center justify-center rounded-full bg-[#F2F2F7] text-text-primary text-[12px]">
            <i className={`font_family ${product.img} ${product.color} text-[14px]`}></i>
            <div className="ml-[6px]">{product.name}</div>
          </div>
        ) : null}
      </div>
      <div className="min-h-32 flex justify-between items-center mt-[4px] gap-8">
        <div className="flex items-center min-w-0">
          {leftExtra}
          {showBtn ? (
            <div className="flex items-center">
              <Button
                color={deepThink ? "primary" : "default"}
                variant="outlined"
                className={classNames(
                  "text-[12px] px-[10px] h-[28px] rounded-full transition-colors duration-150",
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
                    className="font_family icon-zhishiku cursor-pointer text-text-secondary text-[18px] ml-[8px] border border-border rounded-full p-[3px] hover:bg-[#F2F2F7] transition-colors duration-150"
                    onClick={() => dbsShow && dbsShow(true)}
                  ></i>
                </Tooltip>
              )}
            </div>
          ) : null}
        </div>
        <div className="flex items-center shrink-0">
          {running ? (
            <Tooltip title="停止生成">
              <button
                type="button"
                aria-label="停止生成"
                data-testid="chat-stop-button"
                className="relative size-28 rounded-full border-0 bg-text-primary text-white cursor-pointer flex items-center justify-center hover:bg-black"
                onClick={() => onStop?.()}
              >
                <span
                  className="absolute inset-[2px] rounded-full border-[1.5px] border-white/25 border-t-white animate-spin"
                  aria-hidden
                />
                <span className="relative size-[10px] rounded-[2px] bg-white" aria-hidden />
              </button>
            </Tooltip>
          ) : (
            <Tooltip title="发送">
              <button
                type="button"
                aria-label="发送"
                disabled={!canSend}
                className={classNames(
                  "size-28 rounded-full border-0 flex items-center justify-center transition-colors duration-150",
                  canSend
                    ? "cursor-pointer bg-text-primary text-white hover:bg-black"
                    : "cursor-not-allowed bg-[#EBEBF0] text-[#C7C7CC]",
                )}
                onClick={sendMessage}
              >
                <i className="font_family icon-fasongtianchong text-[14px] leading-none" />
              </button>
            </Tooltip>
          )}
        </div>
      </div>
    </div>
  );
};

export default GeneralInput;
