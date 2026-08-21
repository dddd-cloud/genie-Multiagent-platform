import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Input, Segmented, Select } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import MarketplacePage from '@/features/marketplace/MarketplacePage';
import CuratedMarketplacePage from '@/features/marketplace/CuratedMarketplacePage';
import LibraryModal, { type LibraryKind } from '@/features/marketplace/LibraryModal';
import {
  AGENTS_LIBRARY_PATH,
  MARKETPLACE_ROOT,
  MCP_LIBRARY_PATH,
  SKILLS_LIBRARY_PATH,
  TEAMS_LIBRARY_PATH,
} from '@/features/marketplace/paths';
import { listMarketplaceCategories } from '@/services/marketplace';
import type { ExternalMarketplaceResource } from '@/services/marketplace';

export type MarketplaceTab = 'agents' | 'teams' | 'skills' | 'connectors';

const TAB_OPTIONS: Array<{ label: string; value: MarketplaceTab }> = [
  { label: '智能体', value: 'agents' },
  { label: '智能体团队', value: 'teams' },
  { label: '技能', value: 'skills' },
  { label: '连接器', value: 'connectors' },
];

function tabFromSearch(searchTab: string | null): MarketplaceTab {
  if (
    searchTab === 'agents' ||
    searchTab === 'teams' ||
    searchTab === 'connectors'
  ) {
    return searchTab;
  }
  return 'skills';
}

function searchPlaceholder(tab: MarketplaceTab): string {
  switch (tab) {
    case 'agents':
      return '搜索智能体、专长或能力';
    case 'teams':
      return '搜索智能体团队、场景或能力';
    case 'connectors':
      return '搜索连接器';
    default:
      return '搜索技能';
  }
}

export default function MarketplaceMount() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const tab = tabFromSearch(params.get('tab'));
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<string>();
  const [categories, setCategories] = useState<string[]>([]);
  const [libraryOpen, setLibraryOpen] = useState(false);
  const [libraryKind, setLibraryKind] = useState<LibraryKind>('skills');
  const [libraryPath, setLibraryPath] = useState(SKILLS_LIBRARY_PATH);
  const [libraryState, setLibraryState] = useState<unknown>();
  const [librarySession, setLibrarySession] = useState(0);
  const curated = tab === 'agents' || tab === 'teams';

  useEffect(() => {
    setQuery('');
    setCategory(undefined);
  }, [tab]);

  useEffect(() => {
    if (!curated) {
      return;
    }
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
  }, [curated]);

  const handleTabChange = (next: MarketplaceTab) => {
    if (next === 'agents') {
      navigate(`${MARKETPLACE_ROOT}?tab=agents`);
      return;
    }
    if (next === 'teams') {
      navigate(`${MARKETPLACE_ROOT}?tab=teams`);
      return;
    }
    if (next === 'connectors') {
      navigate(`${MARKETPLACE_ROOT}?tab=connectors`);
      return;
    }
    navigate(MARKETPLACE_ROOT);
  };

  function openLibrary(kind: LibraryKind, path?: string, state?: unknown) {
    const fallback =
      kind === 'mcp'
        ? MCP_LIBRARY_PATH
        : kind === 'agents'
          ? AGENTS_LIBRARY_PATH
          : kind === 'teams'
            ? TEAMS_LIBRARY_PATH
            : SKILLS_LIBRARY_PATH;
    setLibraryKind(kind);
    setLibraryPath(path || fallback);
    setLibraryState(state);
    setLibrarySession((value) => value + 1);
    setLibraryOpen(true);
  }

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden" data-testid="marketplace-page">
      <div className="shrink-0 px-24 pt-20">
        <h1 className="m-0 text-[20px] font-semibold tracking-[-0.02em] text-text-primary">
          资源广场
        </h1>
        <div className="mt-16 flex flex-wrap items-center gap-12">
          <Segmented
            options={TAB_OPTIONS}
            value={tab}
            onChange={(value) => handleTabChange(value as MarketplaceTab)}
            data-testid="marketplace-tabs"
          />
          {curated ? (
            <Select
              allowClear
              placeholder="按分类筛选"
              value={category}
              onChange={setCategory}
              options={categories.map((item) => ({
                label: item,
                value: item,
              }))}
              className="w-[180px]"
              data-testid="marketplace-category"
            />
          ) : null}
          <div className="ml-auto flex min-w-0 items-center justify-end gap-8">
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder={searchPlaceholder(tab)}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              className="w-[240px]"
              data-testid="marketplace-search"
            />
            {tab === 'agents' ? (
              <Button
                className="rounded-full"
                data-testid="marketplace-my-agents"
                onClick={() => openLibrary('agents')}
              >
                我的智能体
              </Button>
            ) : null}
            {tab === 'teams' ? (
              <Button
                className="rounded-full"
                data-testid="marketplace-my-teams"
                onClick={() => openLibrary('teams')}
              >
                我的团队
              </Button>
            ) : null}
            {tab === 'skills' ? (
              <Button
                className="rounded-full"
                data-testid="marketplace-my-skills"
                onClick={() => openLibrary('skills')}
              >
                我的技能
              </Button>
            ) : null}
            {tab === 'connectors' ? (
              <Button
                className="rounded-full"
                data-testid="marketplace-my-connectors"
                onClick={() => openLibrary('mcp')}
              >
                我的连接器
              </Button>
            ) : null}
          </div>
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-hidden px-24 py-16">
        {curated ? (
          <CuratedMarketplacePage
            source={tab === 'teams' ? 'TEAMS' : 'EXPERTS'}
            query={query}
            category={category}
          />
        ) : (
          <MarketplacePage
            source={tab === 'connectors' ? 'MCP_REGISTRY' : 'SKILLHUB'}
            query={query}
            onConfigureMcp={(resource: ExternalMarketplaceResource) =>
              openLibrary('mcp', `${MCP_LIBRARY_PATH}/new`, {
                externalMcpTemplate: {
                  name: resource.name,
                  serverUrl: resource.remoteUrl,
                },
              })
            }
          />
        )}
      </div>
      <LibraryModal
        open={libraryOpen}
        session={librarySession}
        kind={libraryKind}
        initialPath={libraryPath}
        initialState={libraryState}
        onClose={() => setLibraryOpen(false)}
      />
    </div>
  );
}
