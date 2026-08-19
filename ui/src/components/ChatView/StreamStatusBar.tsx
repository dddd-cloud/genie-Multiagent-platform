export default function StreamStatusBar({
  status,
  errorMessage,
  errorCode,
}: {
  status: 'failed' | 'interrupted';
  errorMessage?: string;
  errorCode?: string;
}) {
  return (
    <div className="mt-8 mb-16 px-12 py-10 rounded-md bg-danger-soft text-danger text-[13px] border border-[rgba(217,45,32,0.12)]">
      <div className="font-medium">
        {status === 'interrupted' ? '本次执行已中断，可重新发送' : '执行失败'}
      </div>
      {errorMessage ? <div className="mt-4">{errorMessage}</div> : null}
      {errorCode ? <div className="mt-4 text-[12px] opacity-80">{errorCode}</div> : null}
    </div>
  );
}
