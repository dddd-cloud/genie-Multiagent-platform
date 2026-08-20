import { iconType } from "@/utils/constants";
import { toDownloadUrl } from "@/utils/chat";
import docxIcon from "@/assets/icon/docx.png";
import { Tooltip } from "antd";

type Props = {
  files: CHAT.TFile[];
  preview?: boolean;
  remove?: (index: number) => void;
  review?: (file: CHAT.TFile) => void;
};

const GeneralInput: GenieType.FC<Props> = (props) => {
  const { files, preview, remove, review } = props;

  const formatSize = (size: number) => {
    const units = ["B", "KB", "MB", "GB"];
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }
    return `${size?.toFixed(2)} ${units[unitIndex]}`;
  };

  const combinIcon = (f: CHAT.TFile) => {
    const imgType = ["jpg", "png", "jpeg"];
    if (imgType.includes(f.type)) {
      return f.url;
    } else {
      return iconType[f.type] || docxIcon;
    }
  };

  const removeFile = (index: number) => {
    remove?.(index);
  };

  const reviewFile = (f: CHAT.TFile) => {
    review?.(f);
  };

  const downloadFile = (f: CHAT.TFile) => {
    const link = document.createElement('a');
    link.href = toDownloadUrl(f.url) || f.url;
    link.download = f.name || 'download';
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const renderFile = (f: CHAT.TFile, index: number) => {
    return (
      <div
        key={index}
        className={`group w-200 h-56 rounded-xl border border-[#E9E9F0] p-[8px] box-border flex items-center relative ${preview ? "cursor-pointer" : "cursor-default"}`}
        onClick={() => {
          if (!preview) return;
          downloadFile(f);
          reviewFile(f);
        }}
        data-testid="generated-file-chip"
      >
        <img src={combinIcon(f)} alt={f.name} className="w-32 h-32 shrink" />
        <div className="flex-1 ml-[4px] overflow-hidden">
          <Tooltip title={f.name}>
            <div className="w-full overflow-hidden whitespace-nowrap text-ellipsis text-[14px] text-[#27272A] leading-[20px]">
              {f.name}
            </div>
          </Tooltip>
          <div className="w-full text-[12px] text-[#9E9FA3] leading-[18px]">
            {formatSize(f.size)}
          </div>
        </div>
        {!preview && remove ? (
          <button
            type="button"
            aria-label={`移除 ${f.name}`}
            className="absolute top-[10px] right-[8px] flex size-18 items-center justify-center rounded-full border-0 bg-[#8E8E93] text-white cursor-pointer"
            onClick={(event) => {
              event.stopPropagation();
              removeFile(index);
            }}
          >
            <span className="block text-[12px] leading-none">×</span>
          </button>
        ) : null}
      </div>
    );
  };

  return (
    <div className={preview ? "w-full flex gap-8 flex-wrap" : "flex gap-8 flex-wrap justify-end"}>
      {files.map((f, index) => renderFile(f, index))}
    </div>
  );
};

export default GeneralInput;
