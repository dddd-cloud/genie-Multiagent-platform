import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  Input,
  Modal,
  Segmented,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import {
  CopyOutlined,
  PlusOutlined,
  SearchOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { phase2ErrorMessage } from '@/features/phase2/phase2UiError';
import { useSettingsModal } from '../settings/SettingsModalContext';
import AddSuccessToast from './AddSuccessToast';
import {
  createMarketplaceDraft,
  installMarketplaceResource,
  installSkillHubSkill,
  listMarketplaceCategories,
  listMarketplaceResources,
  searchExternalMarketplace,
} from '@/services/marketplace';
import type {
  ExternalMarketplaceResource,
  ExternalMarketplaceSource,
  MarketplaceDraftResponse,
  MarketplaceResource,
  MarketplaceResourceType,
} from '@/services/marketplace';

type MarketplaceSource = 'EXPERTS' | 'TEAMS' | ExternalMarketplaceSource;

function isCuratedSource(source: MarketplaceSource): source is 'EXPERTS' | 'TEAMS' {
  return source === 'EXPERTS' || source === 'TEAMS';
}

function curatedType(source: 'EXPERTS' | 'TEAMS'): MarketplaceResourceType {
  return source === 'EXPERTS' ? 'AGENT' : 'TEAM';
}

function resourceTypeLabel(type: MarketplaceResourceType): string {
  return type === 'AGENT' ? '专家' : type === 'TEAM' ? '专家团队' : type;
}

function canInstall(resource: MarketplaceResource): boolean {
  return resource.installMode === 'INSTALL';
}

export interface MarketplacePageProps {
  onDraftCreated?: (result: MarketplaceDraftResponse) => void;
}

export default function MarketplacePage({ onDraftCreated }: MarketplacePageProps) {
  const { openSettings } = useSettingsModal();
  const [resources, setResources] = useState<MarketplaceResource[]>([]);
  const [externalResources, setExternalResources] = useState<ExternalMarketplaceResource[]>([]);
  const [source, setSource] = useState<MarketplaceSource>('EXPERTS');
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<string>();
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<MarketplaceResource>();
  const [draftLoading, setDraftLoading] = useState(false);
  const [addingId, setAddingId] = useState<string>();
  const [successToast, setSuccessToast] = useState<{ id: number; text: string }>();
  const [draftResult, setDraftResult] = useState<MarketplaceDraftResponse>();
  const dismissSuccessToast = useCallback(() => {
    setSuccessToast(undefined);
  }, []);

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
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
    async function loadResources() {
      setLoading(true);
      const request = isCuratedSource(source)
        ? listMarketplaceResources({ type: curatedType(source), category, query: query || undefined })
        : searchExternalMarketplace(source, query || undefined);
      request
        .then((items) => {
          if (!cancelled) {
            if (isCuratedSource(source)) setResources(items as MarketplaceResource[]);
            else setExternalResources(items as ExternalMarketplaceResource[]);
            setError(undefined);
          }
        })
        .catch(() => {
          if (!cancelled) setError('广场暂时无法加载，请稍后重试。');
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    }
  }, [category, query, source]);

  async function copyDraft(resource: MarketplaceResource) {
    setDraftLoading(true);
    try {
      const result = await createMarketplaceDraft(resource.id);
      if (result) {
        setDraftResult(result);
        onDraftCreated?.(result);
      }
    } catch {
      setError(
        resource.type === 'MCP'
          ? 'MCP 模板需要在设置中填写自己的服务地址和凭据，不能从广场复制。'
          : '模板草稿生成失败，请稍后重试。',
      );
    } finally {
      setDraftLoading(false);
    }
  }

  async function addResource(resource: MarketplaceResource) {
    if (!canInstall(resource)) {
      await copyDraft(resource);
      return;
    }
    setAddingId(resource.id);
    setError(undefined);
    try {
      const result = await installMarketplaceResource(resource.id);
      setSelected(undefined);
      setDraftResult(undefined);
      if (result?.enabled) {
        setSuccessToast({
          id: Date.now(),
          text: result.warnings[0] || '添加并启用成功',
        });
      } else {
        setError(result?.warnings.join('；') || '资源已添加，但尚未通过可用性检测。');
      }
    } catch (err: unknown) {
      setError(phase2ErrorMessage(err, '安装失败：请检查模型、资源权限或同名资源后重试。'));
    } finally {
      setAddingId(undefined);
    }
  }

  async function addExternalSkill(resource: ExternalMarketplaceResource) {
    setAddingId(resource.slug);
    setError(undefined);
    try {
      const result = await installSkillHubSkill(resource.slug, resource.version);
      setSuccessToast({ id: Date.now(), text: `${result?.name || resource.name} 已导入并启用` });
      setSelected(undefined);
    } catch (err: unknown) {
      setError(phase2ErrorMessage(err, 'SkillHub 导入失败，请稍后重试。'));
    } finally {
      setAddingId(undefined);
    }
  }

  function configureExternalMcp(resource: ExternalMarketplaceResource) {
    openSettings('/app/settings/mcp/new', {
      externalMcpTemplate: {
        name: resource.name,
        serverUrl: resource.remoteUrl,
      },
    });
  }

  return (
    <section className="mx-auto w-full max-w-[1180px] px-24 py-24" data-testid="marketplace-page">
      {successToast ? (
        <AddSuccessToast
          key={successToast.id}
          text={successToast.text}
          onDone={dismissSuccessToast}
        />
      ) : null}
      <div className="mb-24 flex flex-wrap items-start justify-between gap-16">
        <div>
          <Typography.Title level={2} className="!mb-4">
            资源广场
          </Typography.Title>
          <Typography.Text type="secondary">
            从精选专家、专家团队、SkillHub 和官方 MCP Registry 搜索并安装可用能力。
          </Typography.Text>
        </div>
        <Space direction="vertical" align="end">
          <Tag icon={<SafetyCertificateOutlined />} color="green">
            公开模板不包含 Credential
          </Tag>
          {categories.length > 0 && (
            <Typography.Text type="secondary">{categories.length} 个分类</Typography.Text>
          )}
        </Space>
      </div>
      <div className="mb-20 flex flex-wrap items-center gap-12">
        <Segmented
          options={[
            { label: '专家', value: 'EXPERTS' },
            { label: '专家团队', value: 'TEAMS' },
            { label: 'SkillHub', value: 'SKILLHUB' },
            { label: '官方 MCP', value: 'MCP_REGISTRY' },
          ]}
          value={source}
          onChange={(value) => {
            const next = value as MarketplaceSource;
            setSource(next);
            setCategory(undefined);
            setSelected(undefined);
          }}
        />
        <Select
          disabled={!isCuratedSource(source)}
          allowClear
          placeholder="按分类筛选"
          value={category}
          onChange={setCategory}
          options={categories.map((item) => ({
            label: item,
            value: item
          }))}
          className="w-[180px]"
        />
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder={source === 'SKILLHUB' ? '搜索 SkillHub Skills' : source === 'MCP_REGISTRY' ? '搜索官方 MCP Server' : source === 'EXPERTS' ? '搜索专家、专长或能力' : '搜索专家团队、场景或能力'}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className="w-[280px]"
        />
      </div>
      {error && <Alert className="mb-16" type="warning" message={error} showIcon />}
      {loading ? (
        <div className="flex justify-center py-64">
          <Spin />
        </div>
      ) : (isCuratedSource(source) ? resources.length : externalResources.length) === 0 ? (
        <Empty description="没有匹配的模板" />
      ) : (
        <div className="grid grid-cols-1 gap-16 md:grid-cols-2 xl:grid-cols-3">
          {isCuratedSource(source) && resources.map((resource) => (
            <Card
              key={resource.id}
              hoverable
              onClick={() => {
                setSelected(resource);
                setDraftResult(undefined);
              }}
              className="h-full"
              title={
                <div className="flex items-center justify-between gap-8">
                  <span>{resource.name}</span>
                  <Tag>{resourceTypeLabel(resource.type)}</Tag>
                </div>
              }
            >
              <Typography.Paragraph ellipsis={{ rows: 2 }} className="!mb-12">
                {resource.tagline}
              </Typography.Paragraph>
              <Space wrap size={[4, 4]} className="mb-16">
                {resource.tags.map((tag) => (
                  <Tag key={tag}>{tag}</Tag>
                ))}
              </Space>
              <div className="flex items-center justify-between">
                <Typography.Text type="secondary">{resource.category}</Typography.Text>
                {canInstall(resource) ? (
                  <Button
                    type="link"
                    icon={<PlusOutlined />}
                    loading={addingId === resource.id}
                    onClick={(event) => {
                      event.stopPropagation();
                      void addResource(resource);
                    }}
                  >
                    {resource.type === 'AGENT' ? '添加专家' : '添加团队'}
                  </Button>
                ) : (
                  <Button
                    type="link"
                    icon={<CopyOutlined />}
                    onClick={(event) => {
                      event.stopPropagation();
                      void copyDraft(resource);
                    }}
                  >
                    查看配置
                  </Button>
                )}
              </div>
            </Card>
          ))}
          {!isCuratedSource(source) && externalResources.map((resource) => (
            <Card key={`${resource.source}-${resource.slug}`} hoverable onClick={() => setSelected(undefined)} className="h-full"
              title={<div className="flex items-center justify-between gap-8"><span>{resource.name}</span><Tag>{resource.type}</Tag></div>}>
              <Typography.Paragraph ellipsis={{ rows: 2 }} className="!mb-12">{resource.description || '暂无描述'}</Typography.Paragraph>
              <Space wrap size={[4, 4]} className="mb-16">{resource.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}<Tag color="gold">★ {resource.stars}</Tag>{resource.downloads > 0 && <Tag>下载 {resource.downloads}</Tag>}{resource.type === 'MCP' && <Tag color={resource.requiresCredential ? 'orange' : 'green'}>{resource.requiresCredential ? '需要 API Key/OAuth' : '免费免 Key'}</Tag>}</Space>
              <Typography.Paragraph type="secondary" ellipsis={{ rows: 1 }} className="!mb-12">{resource.requiresCredential ? '需要服务商 API Key、OAuth 或付费凭据；请打开服务详情按提供方指引申请后再配置。' : '免费公开服务，无需 API Key，可直接配置并使用。'}</Typography.Paragraph>
              <div className="flex items-center justify-between"><Typography.Text type="secondary">{resource.category}</Typography.Text>
                {resource.type === 'SKILL' ? <Button type="link" icon={<PlusOutlined />} loading={addingId === resource.slug} onClick={(event) => { event.stopPropagation(); void addExternalSkill(resource); }}>导入 Skill</Button>
                  : <Button type="link" icon={<CopyOutlined />} onClick={(event) => { event.stopPropagation(); configureExternalMcp(resource); }}>配置 MCP</Button>}
              </div>
            </Card>
          ))}
        </div>
      )}
      <Modal
        open={Boolean(selected)}
        title={selected?.name}
        footer={null}
        onCancel={() => {
          setSelected(undefined);
          setDraftResult(undefined);
        }}
      >
        {selected && (
          <div className="space-y-16">
            <Typography.Paragraph>{selected.description}</Typography.Paragraph>
            <div>
              <Typography.Text strong>来源：</Typography.Text> {selected.sourceType}{' '}
              {selected.sourceUrl && (
                <a href={selected.sourceUrl} target="_blank" rel="noreferrer">
                  查看项目
                </a>
              )}
            </div>
            <div>
              <Typography.Text strong>许可证：</Typography.Text> {selected.license}{' '}
              <Tag color="green">{selected.trustTier}</Tag>
              <Tag color={canInstall(selected) ? 'blue' : 'orange'}>
                {canInstall(selected) ? '可直接添加' : '需要授权配置'}
              </Tag>
            </div>
            <div>
              <Typography.Text strong>能力：</Typography.Text>
              <div className="mt-4">
                {selected.capabilities.map((item) => (
                  <Tag key={item}>{item}</Tag>
                ))}
              </div>
            </div>
            <div>
              <Typography.Text strong>使用前准备：</Typography.Text>
              <ul>
                {selected.setup.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
            {canInstall(selected) ? (
              <Button
                type="primary"
                loading={addingId === selected.id}
                icon={<PlusOutlined />}
                data-testid="marketplace-add-agent-confirm"
                onClick={() => void addResource(selected)}
              >
                {selected.type === 'AGENT' ? '添加专家' : selected.type === 'TEAM' ? '添加团队' : '添加并启用'}
              </Button>
            ) : (
              <Button
                type="primary"
                loading={draftLoading}
                icon={<CopyOutlined />}
                onClick={() => void copyDraft(selected)}
              >
                查看配置要求
              </Button>
            )}
            {draftResult && !canInstall(selected) && (
              <Alert
                type={draftResult.status === 'READY' ? 'success' : 'info'}
                showIcon
                message={
                  draftResult.status === 'READY'
                    ? '草稿已生成，可以继续确认'
                    : '草稿已生成，还需要补充配置'
                }
                description={
                  <>
                    <Typography.Paragraph>
                      缺少配置：
                      {draftResult.missingFields.length
                        ? draftResult.missingFields.join('、')
                        : '无'}
                    </Typography.Paragraph>
                    <pre className="max-h-[220px] overflow-auto whitespace-pre-wrap">
                      {JSON.stringify(draftResult.draft, null, 2)}
                    </pre>
                  </>
                }
              />
            )}
          </div>
        )}
      </Modal>
    </section>
  );
}
