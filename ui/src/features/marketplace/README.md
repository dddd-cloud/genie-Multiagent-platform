# Marketplace feature boundary

This feature owns the independent marketplace page and its API client. The
application shell owns routing and navigation integration. The feature does
not mutate existing Phase2 Agent/Team/Skill/MCP management components.

Curated Agent, Team, and Skill entries are installed through `/install` into
the current user's existing configuration services, then stay on the
marketplace page with a small success toast. Skill packages go through the
existing Skill import service. MCP credentials are never included in
marketplace entries; those still need a user-owned URL and secret.
