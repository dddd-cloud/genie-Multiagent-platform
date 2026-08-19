import { useState } from 'react';
import { Alert, Button, Card, Input, Radio, Space, Tag, Typography } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import { generateDraft } from '@/services/generation';
import type { GenerationDraftResponse, GenerationTarget } from '@/services/generation';

const EXAMPLES = ['帮我创建一个分析 CSV 并生成 PDF 报告的 Agent', '帮我创建一个由研究员和数据分析师组成的 Team'];

export interface GenerationPageProps {
  onDraftReady?: (draft: GenerationDraftResponse) => void;
}

export default function GenerationPage({ onDraftReady }: GenerationPageProps) {
  const [prompt, setPrompt] = useState('');
  const [target, setTarget] = useState<GenerationTarget | undefined>();
  const [result, setResult] = useState<GenerationDraftResponse>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();

  async function submit() {
    if (!prompt.trim()) return;
    setLoading(true); setError(undefined);
    try { const next = await generateDraft(prompt.trim(), target); if (next) { setResult(next); onDraftReady?.(next); } }
    catch { setError('生成失败，请换一种描述再试。'); }
    finally { setLoading(false); }
  }

  return (
    <section className="mx-auto w-full max-w-[960px] px-24 py-24" data-testid="generation-page">
      <Typography.Title level={2}>一句话生成</Typography.Title>
      <Typography.Paragraph type="secondary">描述你想要的 Agent 或 Team，系统先生成可检查的 Draft，不会未经确认直接创建。</Typography.Paragraph>
      <Card>
        <Radio.Group value={target ?? 'AUTO'} onChange={(event) => setTarget(event.target.value === 'AUTO' ? undefined : event.target.value)} className="mb-16"><Radio.Button value="AGENT">Agent</Radio.Button><Radio.Button value="TEAM">Team</Radio.Button><Radio.Button value="AUTO">自动判断</Radio.Button></Radio.Group>
        <Input.TextArea value={prompt} onChange={(event) => setPrompt(event.target.value)} placeholder="例如：帮我创建一个能分析 CSV 并输出 PDF 报告的 Agent" autoSize={{ minRows: 4, maxRows: 8 }} maxLength={2000} showCount />
        <Space wrap className="mt-12">{EXAMPLES.map((example) => <Button key={example} size="small" onClick={() => setPrompt(example)}>{example}</Button>)}</Space>
        <div className="mt-20"><Button type="primary" icon={<ThunderboltOutlined />} loading={loading} disabled={!prompt.trim()} onClick={() => void submit()}>生成 Draft</Button></div>
      </Card>
      {error && <Alert className="mt-16" type="warning" message={error} showIcon />}
      {result && <Card className="mt-20" title={<Space><span>{result.name}</span><Tag color="blue">{result.target}</Tag><Tag color={result.status === 'READY' ? 'green' : 'orange'}>{result.status === 'READY' ? '可继续使用' : '需要配置'}</Tag><Tag color="default">匹配度 {Math.round(result.confidence * 100)}%</Tag></Space>}>
        <Typography.Paragraph>{result.summary}</Typography.Paragraph>
        {result.matchedResourceIds.length > 0 && <div className="mb-12"><Typography.Text strong>匹配模板：</Typography.Text> {result.matchedResourceIds.map((id) => <Tag key={id}>{id}</Tag>)}</div>}
        {result.matchReasons.length > 0 && <div className="mb-12"><Typography.Text strong>匹配依据：</Typography.Text><ul>{result.matchReasons.map((item) => <li key={item}>{item}</li>)}</ul></div>}
        {result.missingFields.length > 0 && <Alert className="mb-12" type="warning" message="还需要补充配置" description={result.missingFields.join('、')} showIcon />}
        <Alert className="mb-12" type="info" message="下一步" description={<ul>{result.suggestions.map((item) => <li key={item}>{item}</li>)}</ul>} />
        <Typography.Text strong>Draft 预览</Typography.Text><pre className="mt-8 max-h-[320px] overflow-auto rounded bg-[#f6f6f8] p-12 text-[12px]">{JSON.stringify(result.draft, null, 2)}</pre>
      </Card>}
    </section>
  );
}
