import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Empty, Space, Spin, Tag, Typography } from 'antd';
import { CopyOutlined, PlusOutlined } from '@ant-design/icons';
import { phase2ErrorMessage } from '@/features/phase2/phase2UiError';
import AddSuccessToast from './AddSuccessToast';
import {
  MARKETPLACE_PAGE_SIZE,
  installSkillHubSkill,
  searchExternalMarketplace,
} from '@/services/marketplace';
import type {
  ExternalMarketplaceResource,
  ExternalMarketplaceSource,
} from '@/services/marketplace';

export interface MarketplacePageProps {
  source: ExternalMarketplaceSource;
  query: string;
  onConfigureMcp?: (resource: ExternalMarketplaceResource) => void;
}

function resourceKey(resource: ExternalMarketplaceResource): string {
  return `${resource.source}-${resource.slug}`;
}

export default function MarketplacePage({
  source,
  query,
  onConfigureMcp,
}: MarketplacePageProps) {
  const [externalResources, setExternalResources] = useState<
    ExternalMarketplaceResource[]
  >([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [error, setError] = useState<string>();
  const [addingId, setAddingId] = useState<string>();
  const [successToast, setSuccessToast] = useState<{ id: number; text: string }>();
  const scrollRef = useRef<HTMLDivElement>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const itemsRef = useRef<ExternalMarketplaceResource[]>([]);
  const inflightRef = useRef(false);
  itemsRef.current = externalResources;

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => void loadFirstPage(), 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };

    async function loadFirstPage() {
      inflightRef.current = true;
      setLoading(true);
      setExternalResources([]);
      setHasMore(false);
      setNextCursor(null);
      try {
        const page = await searchExternalMarketplace(source, query || undefined, {
          limit: MARKETPLACE_PAGE_SIZE,
        });
        if (!cancelled) {
          setExternalResources(page.items);
          setHasMore(Boolean(page.hasMore));
          setNextCursor(page.nextCursor ?? null);
          setError(undefined);
        }
      } catch {
        if (!cancelled) {
          setError('广场暂时无法加载，请稍后重试。');
        }
      } finally {
        inflightRef.current = false;
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
  }, [query, source]);

  useEffect(() => {
    const root = scrollRef.current;
    const sentinel = sentinelRef.current;
    if (!root || !sentinel || loading || loadingMore || !hasMore) {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          void loadMore();
        }
      },
      { root, rootMargin: '120px' },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, loading, loadingMore, nextCursor, source, query, externalResources.length]);

  async function loadMore() {
    if (inflightRef.current || loading || loadingMore || !hasMore) {
      return;
    }
    inflightRef.current = true;
    setLoadingMore(true);
    try {
      const page = await searchExternalMarketplace(source, query || undefined, {
        limit: MARKETPLACE_PAGE_SIZE,
        cursor: nextCursor || undefined,
      });
      const seen = new Set(itemsRef.current.map(resourceKey));
      const incoming = page.items.filter((item) => !seen.has(resourceKey(item)));
      if (incoming.length === 0) {
        setHasMore(false);
        setNextCursor(null);
        return;
      }
      setExternalResources((prev) => [...prev, ...incoming]);
      setHasMore(Boolean(page.hasMore));
      setNextCursor(page.nextCursor ?? null);
    } catch {
      setError('广场暂时无法加载，请稍后重试。');
      setHasMore(false);
    } finally {
      inflightRef.current = false;
      setLoadingMore(false);
    }
  }

  async function addExternalSkill(resource: ExternalMarketplaceResource) {
    setAddingId(resource.slug);
    setError(undefined);
    try {
      const result = await installSkillHubSkill(resource.slug, resource.version);
      setSuccessToast({
        id: Date.now(),
        text: `${result?.name || resource.name} 已导入并启用`,
      });
    } catch (err: unknown) {
      setError(phase2ErrorMessage(err, '技能导入失败，请稍后重试。'));
    } finally {
      setAddingId(undefined);
    }
  }

  function configureExternalMcp(resource: ExternalMarketplaceResource) {
    onConfigureMcp?.(resource);
  }

  return (
    <div ref={scrollRef} className="h-full overflow-y-auto chat-scroll">
      {successToast ? (
        <AddSuccessToast
          key={successToast.id}
          text={successToast.text}
          onDone={() => setSuccessToast(undefined)}
        />
      ) : null}
      {error ? (
        <Alert className="mb-16" type="warning" message={error} showIcon />
      ) : null}
      {loading ? (
        <div className="flex justify-center py-64" data-testid="marketplace-initial-spinner">
          <Spin />
        </div>
      ) : externalResources.length === 0 ? (
        <Empty description="没有匹配的结果" />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-16 md:grid-cols-2 xl:grid-cols-3">
            {externalResources.map((resource) => (
              <Card
                key={resourceKey(resource)}
                className="h-full"
                title={
                  <div className="flex items-center justify-between gap-8">
                    <span className="truncate">{resource.name}</span>
                    <Tag>{resource.type === 'SKILL' ? '技能' : '连接器'}</Tag>
                  </div>
                }
              >
                <Typography.Paragraph ellipsis={{ rows: 2 }} className="!mb-12">
                  {resource.description || '暂无描述'}
                </Typography.Paragraph>
                <Space wrap size={[4, 4]} className="mb-16">
                  {resource.tags.map((tag) => (
                    <Tag key={tag}>{tag}</Tag>
                  ))}
                  <Tag color="gold">★ {resource.stars}</Tag>
                  {resource.downloads > 0 ? <Tag>下载 {resource.downloads}</Tag> : null}
                  {resource.type === 'MCP' ? (
                    <Tag color={resource.requiresCredential ? 'orange' : 'green'}>
                      {resource.requiresCredential ? '需要 API Key/OAuth' : '免费免 Key'}
                    </Tag>
                  ) : null}
                </Space>
                {resource.type === 'MCP' ? (
                  <Typography.Paragraph
                    type="secondary"
                    ellipsis={{ rows: 1 }}
                    className="!mb-12"
                  >
                    {resource.requiresCredential
                      ? '需要服务商凭据或付费后才能使用。'
                      : '可直接配置并使用。'}
                  </Typography.Paragraph>
                ) : null}
                <div className="flex items-center justify-between">
                  <Typography.Text type="secondary">{resource.category}</Typography.Text>
                  {resource.type === 'SKILL' ? (
                    <Button
                      type="link"
                      icon={<PlusOutlined />}
                      loading={addingId === resource.slug}
                      onClick={() => void addExternalSkill(resource)}
                    >
                      导入
                    </Button>
                  ) : (
                    <Button
                      type="link"
                      icon={<CopyOutlined />}
                      onClick={() => configureExternalMcp(resource)}
                    >
                      配置
                    </Button>
                  )}
                </div>
              </Card>
            ))}
          </div>
          {loadingMore ? (
            <div
              className="flex justify-center py-24"
              data-testid="marketplace-load-more-spinner"
            >
              <Spin />
            </div>
          ) : null}
          {hasMore && !loadingMore ? (
            <div ref={sentinelRef} className="h-8" data-testid="marketplace-scroll-sentinel" />
          ) : null}
        </>
      )}
    </div>
  );
}
