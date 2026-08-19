import { useEffect, useState } from 'react';
import { Alert, Button, Card, Empty, Input, Modal, Segmented, Select, Space, Spin, Tag, Typography } from 'antd';
import { CheckCircleOutlined, CopyOutlined, SearchOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { createMarketplaceDraft, installMarketplaceResource, listMarketplaceCategories, listMarketplaceResources } from '@/services/marketplace';
import type { MarketplaceDraftResponse, MarketplaceInstallResponse, MarketplaceResource, MarketplaceResourceType } from '@/services/marketplace';

const TYPE_OPTIONS: Array<{ label: string; value: MarketplaceResourceType | 'ALL' }> = [
  { label: '全部', value: 'ALL' },
  { label: 'Agent', value: 'AGENT' },
  { label: 'Team', value: 'TEAM' },
  { label: 'Skill', value: 'SKILL' },
  { label: 'MCP', value: 'MCP' },
];

export interface MarketplacePageProps {
  onDraftCreated?: (result: MarketplaceDraftResponse) => void;
}

export default function MarketplacePage({ onDraftCreated }: MarketplacePageProps) {
  const [resources, setResources] = useState<MarketplaceResource[]>([]);
  const [selectedType, setSelectedType] = useState<MarketplaceResourceType | 'ALL'>('ALL');
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<string>();
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<MarketplaceResource>();
  const [draftLoading, setDraftLoading] = useState(false);
  const [draftResult, setDraftResult] = useState<MarketplaceDraftResponse>();
  const [installLoading, setInstallLoading] = useState(false);
  const [installResult, setInstallResult] = useState<MarketplaceInstallResponse>();

  useEffect(() => {
    let cancelled = false;
    listMarketplaceCategories()
      .then((items) => {
        if (!cancelled) setCategories(items);
      })
      .catch(() => {
        if (!cancelled) setCategories([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => void loadResources(), 250);
    return () => { cancelled = true; window.clearTimeout(timer); };
    async function loadResources() {
    setLoading(true);
    listMarketplaceResources({ type: selectedType === 'ALL' ? undefined : selectedType, category, query: query || undefined })
      .then((items) => { if (!cancelled) { setResources(items); setError(undefined); } })
      .catch(() => { if (!cancelled) setError('广场暂时无法加载，请稍后重试。'); })
    .finally(() => { if (!cancelled) setLoading(false); });
    }
  }, [selectedType, category, query]);

  async function copyDraft(resource: MarketplaceResource) {
    setDraftLoading(true);
    try {
      const result = await createMarketplaceDraft(resource.id);
      if (result) { setDraftResult(result); onDraftCreated?.(result); }
    } catch { setError('模板草稿生成失败，请稍后重试。'); }
    finally { setDraftLoading(false); }
  }

  async function install(resource: MarketplaceResource) {
    setInstallLoading(true);
    try {
      const result = await installMarketplaceResource(resource.id);
      if (result) setInstallResult(result);
    } catch {
      setError(resource.type === 'MCP'
        ? 'MCP 模板需要在设置中填写自己的服务地址和凭据，不能从广场复制。'
        : '安装失败：请检查模型、资源权限或同名资源后重试。');
    } finally { setInstallLoading(false); }
  }

  function installLabel(resource: MarketplaceResource) {
    if (resource.type === 'SKILL') return '添加并启用';
    if (resource.type === 'TEAM') return '创建并启用';
    if (resource.type === 'AGENT') return '添加并上线';
    return '添加配置';
  }

  return (
    <section className="mx-auto w-full max-w-[1180px] px-24 py-24" data-testid="marketplace-page">
      <div className="mb-24 flex flex-wrap items-start justify-between gap-16">
        <div>
          <Typography.Title level={2} className="!mb-4">资源广场</Typography.Title>
          <Typography.Text type="secondary">发现经过整理的 Agent、Team、Skill 和 MCP 模板，复制后由你确认配置。</Typography.Text>
        </div>
        <Space direction="vertical" align="end">
          <Tag icon={<SafetyCertificateOutlined />} color="green">公开模板不包含 Credential</Tag>
          {categories.length > 0 && <Typography.Text type="secondary">{categories.length} 个分类</Typography.Text>}
        </Space>
      </div>
      <div className="mb-20 flex flex-wrap items-center gap-12">
        <Segmented options={TYPE_OPTIONS} value={selectedType} onChange={(value) => setSelectedType(value as MarketplaceResourceType | 'ALL')} />
        <Select allowClear placeholder="按分类筛选" value={category} onChange={setCategory} options={categories.map((item) => ({ label: item, value: item }))} className="w-[180px]" />
        <Input allowClear prefix={<SearchOutlined />} placeholder="搜索名称、标签或能力" value={query} onChange={(event) => setQuery(event.target.value)} className="w-[280px]" />
      </div>
      {error && <Alert className="mb-16" type="warning" message={error} showIcon />}
      {loading ? <div className="flex justify-center py-64"><Spin /></div> : resources.length === 0 ? <Empty description="没有匹配的模板" /> : (
        <div className="grid grid-cols-1 gap-16 md:grid-cols-2 xl:grid-cols-3">
          {resources.map((resource) => (
            <Card key={resource.id} hoverable onClick={() => { setSelected(resource); setDraftResult(undefined); setInstallResult(undefined); }} className="h-full" title={<div className="flex items-center justify-between gap-8"><span>{resource.name}</span><Tag>{resource.type}</Tag></div>}>
              <Typography.Paragraph ellipsis={{ rows: 2 }} className="!mb-12">{resource.tagline}</Typography.Paragraph>
              <Space wrap size={[4, 4]} className="mb-16">{resource.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}</Space>
              <div className="flex items-center justify-between"><Typography.Text type="secondary">{resource.category}</Typography.Text><Button type="link" icon={<CheckCircleOutlined />} onClick={(event) => { event.stopPropagation(); void install(resource); }}>{installLabel(resource)}</Button></div>
            </Card>
          ))}
        </div>
      )}
      <Modal open={Boolean(selected)} title={selected?.name} footer={null} onCancel={() => { setSelected(undefined); setDraftResult(undefined); setInstallResult(undefined); }}>
        {selected && <div className="space-y-16">
          <Typography.Paragraph>{selected.description}</Typography.Paragraph>
          <div><Typography.Text strong>来源：</Typography.Text> {selected.sourceType} {selected.sourceUrl && <a href={selected.sourceUrl} target="_blank" rel="noreferrer">查看项目</a>}</div>
          <div><Typography.Text strong>许可证：</Typography.Text> {selected.license} <Tag color="green">{selected.trustTier}</Tag></div>
          <div><Typography.Text strong>能力：</Typography.Text><div className="mt-4">{selected.capabilities.map((item) => <Tag key={item}>{item}</Tag>)}</div></div>
          <div><Typography.Text strong>使用前准备：</Typography.Text><ul>{selected.setup.map((item) => <li key={item}>{item}</li>)}</ul></div>
          <Space wrap>
            <Button type="primary" loading={installLoading} icon={<CheckCircleOutlined />} onClick={() => void install(selected)}>{installLabel(selected)}</Button>
            <Button loading={draftLoading} icon={<CopyOutlined />} onClick={() => void copyDraft(selected)}>仅复制为草稿</Button>
          </Space>
          {installResult && <Alert type="success" showIcon message={installResult.enabled ? '资源已安装并启用' : '资源已安装'} description={<Typography.Paragraph className="!mb-0">已创建 {installResult.createdSkillIds.length} 个 Skill、{installResult.createdAgentIds.length} 个 Agent{installResult.createdTeamId ? ' 和 1 个 Team' : ''}。它们已属于当前用户，可在现有设置和管理页面中查看。</Typography.Paragraph>} />}
          {draftResult && <Alert type={draftResult.status === 'READY' ? 'success' : 'info'} showIcon message={draftResult.status === 'READY' ? '草稿已生成，可以继续确认' : '草稿已生成，还需要补充配置'} description={<><Typography.Paragraph>缺少配置：{draftResult.missingFields.length ? draftResult.missingFields.join('、') : '无'}</Typography.Paragraph><pre className="max-h-[220px] overflow-auto whitespace-pre-wrap">{JSON.stringify(draftResult.draft, null, 2)}</pre></>} />}
        </div>}
      </Modal>
    </section>
  );
}
