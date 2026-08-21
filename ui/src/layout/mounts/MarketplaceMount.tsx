import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Input, Segmented } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import MarketplacePage from '@/features/marketplace/MarketplacePage';
import CuratedMarketplacePage from '@/features/marketplace/CuratedMarketplacePage';
import LibraryModal, { type LibraryKind } from '@/features/marketplace/LibraryModal';
import {
  MARKETPLACE_ROOT,
  MCP_LIBRARY_PATH,
  SKILLS_LIBRARY_PATH,
} from '@/features/marketplace/paths';
import type { ExternalMarketplaceResource } from '@/services/marketplace';

export type MarketplaceTab = 'agents' | 'skills' | 'connectors';

const TAB_OPTIONS: Array<{ label: string; value: MarketplaceTab }> = [
  { label: '智能体', value: 'agents' },
  { label: '技能', value: 'skills' },
  { label: '连接器', value: 'connectors' },
];

function tabFromSearch(searchTab: string | null): MarketplaceTab {
  if (searchTab === 'agents' || searchTab === 'connectors') {
    return searchTab;
  }
  return 'skills';
}

export default function MarketplaceMount() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const tab = tabFromSearch(params.get('tab'));
  const [query, setQuery] = useState('');
  const [libraryOpen, setLibraryOpen] = useState(false);
  const [libraryKind, setLibraryKind] = useState<LibraryKind>('skills');
  const [libraryPath, setLibraryPath] = useState(SKILLS_LIBRARY_PATH);
  const [libraryState, setLibraryState] = useState<unknown>();
  const [librarySession, setLibrarySession] = useState(0);

  useEffect(() => {
    setQuery('');
  }, [tab]);

  const handleTabChange = (next: MarketplaceTab) => {
    if (next === 'agents') {
      navigate(`${MARKETPLACE_ROOT}?tab=agents`);
      return;
    }
    if (next === 'connectors') {
      navigate(`${MARKETPLACE_ROOT}?tab=connectors`);
      return;
    }
    navigate(MARKETPLACE_ROOT);
  };

  function openLibrary(kind: LibraryKind, path?: string, state?: unknown) {
    setLibraryKind(kind);
    setLibraryPath(path || (kind === 'mcp' ? MCP_LIBRARY_PATH : SKILLS_LIBRARY_PATH));
    setLibraryState(state);
    setLibrarySession((value) => value + 1);
    setLibraryOpen(true);
  }

  const showToolbar = tab !== 'agents';

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden" data-testid="marketplace-page">
      <div className="shrink-0 px-24 pt-20">
        <h1 className="m-0 text-[20px] font-semibold tracking-[-0.02em] text-text-primary">
          资源广场
        </h1>
        <div className="mt-16 flex items-center gap-12">
          <Segmented
            options={TAB_OPTIONS}
            value={tab}
            onChange={(value) => handleTabChange(value as MarketplaceTab)}
            data-testid="marketplace-tabs"
          />
          {showToolbar ? (
            <div className="ml-auto flex min-w-0 items-center justify-end gap-8">
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder={tab === 'skills' ? '搜索技能' : '搜索连接器'}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                className="w-[240px]"
                data-testid="marketplace-search"
              />
              {tab === 'skills' ? (
                <Button
                  className="rounded-full"
                  data-testid="marketplace-my-skills"
                  onClick={() => openLibrary('skills')}
                >
                  我的技能
                </Button>
              ) : (
                <Button
                  className="rounded-full"
                  data-testid="marketplace-my-connectors"
                  onClick={() => openLibrary('mcp')}
                >
                  我的连接器
                </Button>
              )}
            </div>
          ) : (
            <div className="ml-auto" />
          )}
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-hidden px-24 py-16">
        {tab === 'agents' ? (
          <CuratedMarketplacePage />
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
