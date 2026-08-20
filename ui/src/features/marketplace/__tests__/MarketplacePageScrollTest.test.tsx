import { describe, expect, it, vi, beforeEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import MarketplacePage from '../MarketplacePage';
import { searchExternalMarketplace } from '@/services/marketplace';
import type { ExternalMarketplacePage, ExternalMarketplaceResource } from '@/services/marketplace';

vi.mock('@/services/marketplace', async () => {
  const actual = await vi.importActual<typeof import('@/services/marketplace')>(
    '@/services/marketplace',
  );
  return {
    ...actual,
    searchExternalMarketplace: vi.fn(),
    installSkillHubSkill: vi.fn(),
  };
});

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

let ioCallback: IntersectionObserverCallback | null = null;

class MockIntersectionObserver {
  constructor(callback: IntersectionObserverCallback) {
    ioCallback = callback;
  }
  observe() {}
  unobserve() {}
  disconnect() {}
}

Object.defineProperty(window, 'IntersectionObserver', {
  writable: true,
  configurable: true,
  value: MockIntersectionObserver,
});

function makeItems(count: number, start = 0): ExternalMarketplaceResource[] {
  return Array.from({ length: count }, (_, index) => ({
    source: 'SKILLHUB',
    type: 'SKILL',
    slug: `skill-${start + index}`,
    version: '1.0.0',
    name: `Skill ${start + index}`,
    description: 'desc',
    category: 'demo',
    tags: [],
    stars: 0,
    downloads: 0,
    sourceUrl: '',
    repositoryUrl: '',
    remoteUrl: '',
    transport: '',
    requiresCredential: false,
    compatibility: '',
  }));
}

describe('MarketplacePage infinite scroll', () => {
  beforeEach(() => {
    ioCallback = null;
    vi.mocked(searchExternalMarketplace).mockReset();
  });

  it('loads 12 items first, then another 12 at the bottom with a spinner', async () => {
    let resolveMore: ((value: ExternalMarketplacePage) => void) | undefined;
    vi.mocked(searchExternalMarketplace)
      .mockResolvedValueOnce({
        items: makeItems(12),
        hasMore: true,
        nextCursor: '12',
      })
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveMore = resolve;
          }),
      );

    render(<MarketplacePage source="SKILLHUB" query="" />);

    expect(await screen.findByText('Skill 0')).toBeTruthy();
    expect(screen.getByText('Skill 11')).toBeTruthy();
    expect(screen.queryByText('Skill 12')).toBeNull();
    expect(searchExternalMarketplace).toHaveBeenCalledWith('SKILLHUB', undefined, {
      limit: 12,
    });

    await act(async () => {
      ioCallback?.(
        [{ isIntersecting: true } as IntersectionObserverEntry],
        {} as IntersectionObserver,
      );
    });

    expect(screen.getByTestId('marketplace-load-more-spinner')).toBeTruthy();
    expect(searchExternalMarketplace).toHaveBeenCalledWith('SKILLHUB', undefined, {
      limit: 12,
      cursor: '12',
    });

    await act(async () => {
      resolveMore?.({
        items: makeItems(12, 12),
        hasMore: true,
        nextCursor: '24',
      });
    });

    expect(await screen.findByText('Skill 12')).toBeTruthy();
    expect(screen.queryByTestId('marketplace-load-more-spinner')).toBeNull();
  });
});
