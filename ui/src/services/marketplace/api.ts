import { requestMvp } from '@/services/mvp';
import type { MarketplaceDraftResponse, MarketplaceInstallResponse, MarketplaceResource, MarketplaceResourceType } from './types';

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
