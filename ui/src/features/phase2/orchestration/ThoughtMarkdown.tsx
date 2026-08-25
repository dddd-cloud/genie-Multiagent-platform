import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

export default function ThoughtMarkdown({ text }: { text: string }) {
  if (!text.trim()) {
    return null;
  }
  return (
    <div
      className="markdown-body min-w-0 max-w-full overflow-hidden break-words text-[13px] leading-[22px] text-text-secondary [overflow-wrap:anywhere] [&_a]:break-all [&_p]:mb-6 [&_p]:last:mb-0 [&_ul]:mb-6 [&_ul]:list-disc [&_ul]:pl-18 [&_ol]:mb-6 [&_ol]:list-decimal [&_ol]:pl-18 [&_li]:mb-2 [&_strong]:font-semibold [&_strong]:text-text-primary [&_h1]:mb-6 [&_h1]:text-[14px] [&_h1]:font-semibold [&_h2]:mb-6 [&_h2]:text-[13px] [&_h2]:font-semibold [&_h3]:mb-4 [&_h3]:text-[13px] [&_h3]:font-semibold [&_code]:break-all [&_code]:rounded [&_code]:bg-black/[0.04] [&_code]:px-4 [&_code]:text-[12px] [&_pre]:mb-6 [&_pre]:max-w-full [&_pre]:overflow-auto [&_pre]:rounded-md [&_pre]:bg-black/[0.04] [&_pre]:p-8 [&_table]:block [&_table]:max-w-full [&_table]:overflow-x-auto"
      style={{ minWidth: 0, width: '100%', maxWidth: '100%', overflowX: 'hidden', overflowWrap: 'anywhere', wordBreak: 'break-word', whiteSpace: 'normal' }}
      data-testid="thought-markdown"
    >
      <ReactMarkdown remarkPlugins={[remarkGfm]} skipHtml>
        {text}
      </ReactMarkdown>
    </div>
  );
}
