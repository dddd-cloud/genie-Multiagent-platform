export type MarketplaceResourceType = 'AGENT' | 'TEAM' | 'SKILL' | 'MCP';

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
