import { requestMvp } from '@/services/mvp';
import type { ExternalMarketplacePage, ExternalMarketplaceSource, MarketplaceDraftResponse, MarketplaceInstallResponse, MarketplaceResource, MarketplaceResourceType } from './types';

export async function listMarketplaceResources(filters: {
  type?: MarketplaceResourceType;
  category?: string;
  query?: string;
} = {}) {
  return (await requestMvp<MarketplaceResource[]>({
    method: 'GET',
    url: '/api/v2/marketplace/resources',
    params: {
      type: filters.type,
      category: filters.category,
      q: filters.query,
    },
  })) ?? [];
}

export async function listMarketplaceCategories() {
  return (await requestMvp<string[]>({
    method: 'GET',
    url: '/api/v2/marketplace/categories',
  })) ?? [];
}

export function getMarketplaceResource(id: string) {
  return requestMvp<MarketplaceResource>({
    method: 'GET',
    url: `/api/v2/marketplace/resources/${id}`,
  });
}

export function createMarketplaceDraft(id: string) {
  return requestMvp<MarketplaceDraftResponse>({
    method: 'POST',
    url: `/api/v2/marketplace/resources/${id}/draft`,
  });
}

export function installMarketplaceResource(id: string) {
  return requestMvp<MarketplaceInstallResponse>({
    method: 'POST',
    url: `/api/v2/marketplace/resources/${id}/install`,
  });
}

export const MARKETPLACE_PAGE_SIZE = 12;

export async function searchExternalMarketplace(
  source: ExternalMarketplaceSource,
  query?: string,
  options: { sort?: string; limit?: number; cursor?: string } = {},
) {
  return (
    (await requestMvp<ExternalMarketplacePage>({
      method: 'GET',
      url: '/api/v2/marketplace/external/resources',
      params: {
        source,
        q: query,
        sort: options.sort ?? 'stars',
        limit: options.limit ?? MARKETPLACE_PAGE_SIZE,
        cursor: options.cursor,
      },
    })) ?? { items: [], hasMore: false, nextCursor: null }
  );
}

export function installSkillHubSkill(slug: string, version: string) {
  return requestMvp<{ id: string; name: string; status: string }>({
    method: 'POST',
    url: '/api/v2/marketplace/external/skillhub/install',
    data: { slug, version },
  });
}
