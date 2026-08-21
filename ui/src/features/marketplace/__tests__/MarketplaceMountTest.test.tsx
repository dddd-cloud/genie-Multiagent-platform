import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import MarketplaceMount from '@/layout/mounts/MarketplaceMount';

vi.mock('@/services/marketplace', () => ({
  MARKETPLACE_PAGE_SIZE: 12,
  searchExternalMarketplace: vi.fn().mockResolvedValue({
    items: [],
    hasMore: false,
    nextCursor: null,
  }),
  installSkillHubSkill: vi.fn(),
  listMarketplaceCategories: vi.fn().mockResolvedValue([]),
  listMarketplaceResources: vi.fn().mockResolvedValue([]),
  createMarketplaceDraft: vi.fn(),
  installMarketplaceResource: vi.fn(),
}));

vi.mock('@/features/phase2/skills/SkillListPage', () => ({
  default: () => <div data-testid="skill-list-page">我的技能</div>,
}));

vi.mock('@/features/phase2/skills/SkillEditorPage', () => ({
  default: () => <div data-testid="skill-editor-page">技能编辑</div>,
}));

vi.mock('@/features/phase2/mcp/McpListPage', () => ({
  default: () => <div data-testid="mcp-list-page">我的连接器</div>,
}));

vi.mock('@/features/phase2/mcp/McpEditorPage', () => ({
  default: () => <div data-testid="mcp-editor-page">连接器编辑</div>,
}));

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

class MockIntersectionObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

Object.defineProperty(window, 'IntersectionObserver', {
  writable: true,
  configurable: true,
  value: MockIntersectionObserver,
});

function renderMarketplace(path = '/app/marketplace') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/app/marketplace" element={<MarketplaceMount />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('MarketplaceMount', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  it('shows 智能体, 技能 and 连接器 without the old type filters', () => {
    renderMarketplace();
    expect(screen.getByText('智能体')).toBeTruthy();
    expect(screen.getByText('技能')).toBeTruthy();
    expect(screen.getByText('连接器')).toBeTruthy();
    expect(screen.queryByText('SkillHub')).toBeNull();
    expect(screen.queryByText('官方 MCP')).toBeNull();
    expect(screen.queryByText('全部')).toBeNull();
    expect(screen.queryByText('按分类筛选')).toBeNull();
    expect(screen.getByTestId('marketplace-my-skills')).toBeTruthy();
  });

  it('opens my skills in a closable overlay without leaving the catalog', async () => {
    renderMarketplace();
    fireEvent.click(screen.getByTestId('marketplace-my-skills'));
    expect(await screen.findByTestId('library-modal')).toBeTruthy();
    expect(screen.getByTestId('skill-list-page')).toBeTruthy();
    expect(screen.getByTestId('marketplace-search')).toBeTruthy();
    expect(screen.queryByTestId('settings-nav')).toBeNull();

    fireEvent.click(screen.getByTestId('library-modal-close'));
    await waitFor(() => {
      expect(screen.queryByTestId('skill-list-page')).toBeNull();
    });
    expect(screen.getByTestId('marketplace-search')).toBeTruthy();
  });

  it('opens my connectors from the connectors tab', async () => {
    renderMarketplace('/app/marketplace?tab=connectors');
    fireEvent.click(screen.getByTestId('marketplace-my-connectors'));
    expect(await screen.findByTestId('library-modal')).toBeTruthy();
    expect(screen.getByTestId('mcp-list-page')).toBeTruthy();
    expect(screen.getByTestId('marketplace-search')).toBeTruthy();
  });

  it('shows curated experts and teams on the agents tab', async () => {
    renderMarketplace('/app/marketplace?tab=agents');
    expect(await screen.findByTestId('curated-marketplace-page')).toBeTruthy();
    expect(screen.getByText('专家')).toBeTruthy();
    expect(screen.getByText('专家团队')).toBeTruthy();
    expect(screen.queryByText('SkillHub')).toBeNull();
    expect(screen.queryByText('官方 MCP')).toBeNull();
  });
});
