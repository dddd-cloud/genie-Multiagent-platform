import { requestMvp } from '@/services/mvp';
import type { ExternalMarketplaceResource, ExternalMarketplaceSource, MarketplaceDraftResponse, MarketplaceInstallResponse, MarketplaceResource, MarketplaceResourceType } from './types';

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
    // A team install may first import several reviewed Skill packages, then
    // create and publish each member Agent.  The shared 10s API default can
    // expire while the server finishes successfully, which previously showed
    // a false "service unavailable" error to the user.
    timeout: 120_000,
  });
}

export async function searchExternalMarketplace(source: ExternalMarketplaceSource, query?: string, sort = 'stars') {
  return (await requestMvp<ExternalMarketplaceResource[]>({
    method: 'GET',
    url: '/api/v2/marketplace/external/resources',
    params: { source, q: query, sort },
  })) ?? [];
}

export function installSkillHubSkill(slug: string, version: string) {
  return requestMvp<{ id: string; name: string; status: string }>({
    method: 'POST',
    url: '/api/v2/marketplace/external/skillhub/install',
    data: { slug, version },
  });
}
