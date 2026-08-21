import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  Modal,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { CopyOutlined, PlusOutlined } from '@ant-design/icons';
import { phase2ErrorMessage } from '@/features/phase2/phase2UiError';
import AddSuccessToast from './AddSuccessToast';
import {
  createMarketplaceDraft,
  installMarketplaceResource,
  listMarketplaceResources,
} from '@/services/marketplace';
import type {
  MarketplaceDraftResponse,
  MarketplaceResource,
  MarketplaceResourceType,
} from '@/services/marketplace';

type CuratedSource = 'EXPERTS' | 'TEAMS';

function curatedType(source: CuratedSource): MarketplaceResourceType {
  return source === 'EXPERTS' ? 'AGENT' : 'TEAM';
}

function resourceTypeLabel(type: MarketplaceResourceType): string {
  return type === 'AGENT' ? '智能体' : type === 'TEAM' ? '智能体团队' : type;
}

function canInstall(resource: MarketplaceResource): boolean {
  return resource.installMode === 'INSTALL';
}

type CuratedMarketplacePageProps = {
  source: CuratedSource;
  query: string;
  category?: string;
};

export default function CuratedMarketplacePage({
  source,
  query,
  category,
}: CuratedMarketplacePageProps) {
  const [resources, setResources] = useState<MarketplaceResource[]>([]);
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
    setSelected(undefined);
    setDraftResult(undefined);
  }, [source, category, query]);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => void loadResources(), 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };

    async function loadResources() {
      setLoading(true);
      try {
        const items = await listMarketplaceResources({
          type: curatedType(source),
          category,
          query: query || undefined,
        });
        if (!cancelled) {
          setResources(items);
          setError(undefined);
        }
      } catch {
        if (!cancelled) setError('广场暂时无法加载，请稍后重试。');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
  }, [category, query, source]);

  async function copyDraft(resource: MarketplaceResource) {
    setDraftLoading(true);
    try {
      const result = await createMarketplaceDraft(resource.id);
      if (result) {
        setDraftResult(result);
      }
    } catch {
      setError('模板草稿生成失败，请稍后重试。');
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

  return (
    <div className="h-full overflow-y-auto chat-scroll" data-testid="curated-marketplace-page">
      {successToast ? (
        <AddSuccessToast
          key={successToast.id}
          text={successToast.text}
          onDone={dismissSuccessToast}
        />
      ) : null}
      {error ? <Alert className="mb-16" type="warning" message={error} showIcon /> : null}
      {loading ? (
        <div className="flex justify-center py-64">
          <Spin />
        </div>
      ) : resources.length === 0 ? (
        <Empty description="没有匹配的模板" />
      ) : (
        <div className="grid grid-cols-1 gap-16 md:grid-cols-2 xl:grid-cols-3">
          {resources.map((resource) => (
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
                    {resource.type === 'AGENT' ? '添加智能体' : '添加团队'}
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
        {selected ? (
          <div className="space-y-16">
            <Typography.Paragraph>{selected.description}</Typography.Paragraph>
            <div>
              <Typography.Text strong>来源：</Typography.Text> {selected.sourceType}{' '}
              {selected.sourceUrl ? (
                <a href={selected.sourceUrl} target="_blank" rel="noreferrer">
                  查看项目
                </a>
              ) : null}
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
                {selected.type === 'AGENT'
                  ? '添加智能体'
                  : selected.type === 'TEAM'
                    ? '添加团队'
                    : '添加并启用'}
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
            {draftResult && !canInstall(selected) ? (
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
            ) : null}
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
