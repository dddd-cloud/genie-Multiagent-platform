import React, { useEffect, useRef, useState, type ReactNode } from "react";
import { Input, Button, Tooltip } from "antd";
import classNames from "classnames";
import { TextAreaRef } from "antd/es/input/TextArea";
import { useUserSettings } from "@/features/userSettings/useUserSettings";
import ComposerAttachments from "@/features/chatAttachments/ComposerAttachments";
import ComposerModelPicker from "@/features/chatModel/ComposerModelPicker";
import {
  COMPOSER_ATTACHMENT_ACCEPT,
  COMPOSER_ATTACHMENT_MAX_FILES,
} from "@/features/chatAttachments/constants";
import { useComposerAttachments } from "@/features/chatAttachments/useComposerAttachments";

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
  conversationId?: string;
  ensureConversation?: () => Promise<string | null>;
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
    conversationId,
    ensureConversation,
  } = props;
  const { preferences, status: preferencesStatus, save } = useUserSettings();
  const [question, setQuestion] = useState<string>("");
  const [deepThink, setDeepThink] = useState<boolean>(false);
  const [selectedModel, setSelectedModel] = useState<string>("");
  const deepThinkTouchedRef = useRef(false);
  const textareaRef = useRef<TextAreaRef>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [fileDragOver, setFileDragOver] = useState(false);
  const fileDragCountRef = useRef(0);
  const {
    attachments,
    addFiles,
    removeAttachment,
    clearAttachments,
    uploading,
    ready,
    readyIds,
  } = useComposerAttachments({
    conversationId,
    ensureConversation,
  });
  const tempData = useRef<{
    cmdPress?: boolean;
    compositing?: boolean;
  }>({});

  const questionChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setQuestion(e.target.value);
  };

  useEffect(() => {
    if (preferencesStatus === 'ready' && !selectedModel && preferences.preferredModelName) {
      setSelectedModel(preferences.preferredModelName);
    }
  }, [preferences.preferredModelName, preferencesStatus, selectedModel]);

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
    if (disabled || running || uploading) {
      return;
    }
    if (!question.trim() && readyIds.length === 0) {
      return;
    }
    emitSend();
  };

  const toInputFiles = (): CHAT.TFile[] =>
    ready.map((file) => ({
      name: file.name,
      url: '',
      type: file.type,
      size: file.size,
    }));

  const emitSend = () => {
    const trimmed = question.trim();
    send({
      message: trimmed || (readyIds.length > 0 ? '请阅读并分析我上传的文件。' : ''),
      outputStyle: product?.type,
      deepThink,
      files: toInputFiles(),
      attachmentIds: readyIds,
      modelName: selectedModel || undefined,
    });
    setQuestion("");
    clearAttachments();
  };

  const sendMessage = () => {
    if (disabled || running || uploading) {
      return;
    }
    if (!question.trim() && readyIds.length === 0) {
      return;
    }
    emitSend();
  };

  const canSend =
    !disabled &&
    !running &&
    !uploading &&
    (Boolean(question.trim()) || readyIds.length > 0);
  const canAttach = Boolean(conversationId || ensureConversation);
  const atFileLimit = attachments.length >= COMPOSER_ATTACHMENT_MAX_FILES;
  const allowFileDrop = canAttach && !disabled && !running;

  const dataTransferHasFiles = (event: React.DragEvent) =>
    Array.from(event.dataTransfer?.types ?? []).includes("Files");

  const resetFileDrag = () => {
    fileDragCountRef.current = 0;
    setFileDragOver(false);
  };

  const onFileDragEnter = (event: React.DragEvent<HTMLDivElement>) => {
    if (!allowFileDrop || !dataTransferHasFiles(event)) return;
    event.preventDefault();
    event.stopPropagation();
    fileDragCountRef.current += 1;
    setFileDragOver(true);
  };

  const onFileDragOver = (event: React.DragEvent<HTMLDivElement>) => {
    if (!allowFileDrop || !dataTransferHasFiles(event)) return;
    event.preventDefault();
    event.stopPropagation();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = atFileLimit ? "none" : "copy";
    }
  };

  const onFileDragLeave = (event: React.DragEvent<HTMLDivElement>) => {
    if (!dataTransferHasFiles(event)) return;
    event.preventDefault();
    event.stopPropagation();
    fileDragCountRef.current = Math.max(0, fileDragCountRef.current - 1);
    if (fileDragCountRef.current === 0) {
      setFileDragOver(false);
    }
  };

  const onFileDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.stopPropagation();
    resetFileDrag();
    if (!allowFileDrop) return;
    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      void addFiles(files);
    }
  };

  return (
    <div
      data-testid="composer-dropzone"
      onDragEnter={onFileDragEnter}
      onDragOver={onFileDragOver}
      onDragLeave={onFileDragLeave}
      onDrop={onFileDrop}
      className="relative w-full"
    >
      <ComposerAttachments attachments={attachments} onRemove={removeAttachment} />
      <div
        data-testid="composer-shell"
        className={classNames(
          "relative rounded-[28px] border bg-white overflow-visible p-[14px] pb-[10px] shadow-[0_1px_2px_rgba(0,0,0,0.04)]",
          "transition-[border-color,box-shadow] duration-150",
          fileDragOver && allowFileDrop
            ? "border-[#1D1D1F]/35 shadow-[0_8px_28px_rgba(0,0,0,0.06)]"
            : "border-black/8 focus-within:border-black/15 focus-within:shadow-[0_8px_28px_rgba(0,0,0,0.06)]",
        )}
      >
      {fileDragOver && allowFileDrop ? (
        <div
          className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center rounded-[28px] border-2 border-dashed border-[#1D1D1F]/25 bg-white/88 text-[14px] text-text-secondary"
          data-testid="composer-drop-overlay"
        >
          {atFileLimit ? `最多上传 ${COMPOSER_ATTACHMENT_MAX_FILES} 个文件` : "松开以上传文件"}
        </div>
      ) : null}
      <div className="relative">
        <TextArea
          ref={textareaRef}
          value={question}
          placeholder={placeholder}
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
        <div className="flex items-center shrink-0 gap-8">
          <ComposerModelPicker
            value={selectedModel}
            disabled={disabled || running}
            onChange={(name) => {
              setSelectedModel(name);
              void save({ preferredModelName: name }).catch(() => undefined);
            }}
          />
          {canAttach ? (
            <>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept={COMPOSER_ATTACHMENT_ACCEPT}
            className="hidden"
            aria-hidden
            tabIndex={-1}
            onChange={(event) => {
              const files = event.target.files;
              if (files && files.length > 0) {
                void addFiles(files);
              }
              event.target.value = '';
            }}
          />
          <Tooltip title={atFileLimit ? `最多上传 ${COMPOSER_ATTACHMENT_MAX_FILES} 个文件` : "上传文件"}>
            <button
              type="button"
              aria-label="上传文件"
              data-testid="composer-attach-button"
              disabled={disabled || running || atFileLimit}
              className={classNames(
                "size-28 rounded-full border-0 flex items-center justify-center transition-colors duration-150",
                disabled || running || atFileLimit
                  ? "cursor-not-allowed text-[#C7C7CC] bg-transparent"
                  : "cursor-pointer text-text-secondary hover:bg-[#F2F2F7] hover:text-text-primary",
              )}
              onClick={() => {
                if (disabled || running || atFileLimit) return;
                fileInputRef.current?.click();
              }}
            >
              <i className="font_family icon-fujian text-[16px] leading-none" />
            </button>
          </Tooltip>
            </>
          ) : null}
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
    </div>
  );
};

export default GeneralInput;
