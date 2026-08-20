export type MarketplaceResourceType = 'AGENT' | 'TEAM' | 'SKILL' | 'MCP';
export type ExternalMarketplaceSource = 'SKILLHUB' | 'MCP_REGISTRY';

export interface ExternalMarketplacePage {
  items: ExternalMarketplaceResource[];
  hasMore: boolean;
  nextCursor?: string | null;
}

export interface ExternalMarketplaceResource {
  source: ExternalMarketplaceSource;
  type: Extract<MarketplaceResourceType, 'SKILL' | 'MCP'>;
  slug: string;
  version: string;
  name: string;
  description: string;
  category: string;
  tags: string[];
  stars: number;
  downloads: number;
  sourceUrl: string;
  repositoryUrl: string;
  remoteUrl: string;
  transport: string;
  requiresCredential: boolean;
  compatibility: string;
}

export interface MarketplaceResource {
  id: string;
  type: MarketplaceResourceType;
  slug: string;
  name: string;
  tagline: string;
  description: string;
  category: string;
  tags: string[];
  sourceType: string;
  sourceUrl: string;
  license: string;
  trustTier: string;
  capabilities: string[];
  setup: string[];
  installMode: 'INSTALL' | 'CONFIGURE';
}

export interface MarketplaceDraftResponse {
  resourceId: string;
  type: MarketplaceResourceType;
  name: string;
  ownerUserId?: string;
  draft: Record<string, unknown>;
  warnings: string[];
  status: 'READY' | 'NEEDS_CONFIGURATION' | 'INVALID';
  missingFields: string[];
}

export interface MarketplaceInstallResponse {
  marketplaceResourceId: string;
  resourceType: MarketplaceResourceType;
  primaryResourceId?: string;
  createdAgentIds: string[];
  createdSkillIds: string[];
  createdTeamId?: string;
  status: 'INSTALLED';
  enabled: boolean;
  warnings: string[];
}
