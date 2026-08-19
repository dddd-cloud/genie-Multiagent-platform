# Marketplace feature boundary

This feature owns the independent marketplace page and its API client. The
application shell owns routing and navigation integration. The feature does
not mutate existing Phase2 Agent/Team/Skill/MCP management components.

Curated Skill entries may carry reviewed package resources. “添加并启用” sends
the package through the existing Skill import service, so the resulting Skill
is owned by the current user and can be bound to an existing Agent. Curated
Agent and Team entries are materialised only through the existing public
configuration services; MCP credentials are never included in marketplace
entries.
