# Marketplace catalog

This is the Phase3-B curated template catalog. It is intentionally a read-only,
code-reviewed discovery layer; it is not a user publishing or billing system.

Every entry must be safe to expose as public metadata. Do not add credentials,
cookies, tenant IDs, owner IDs, private prompts, or executable tool results.
Agent, Team and reviewed Skill packages can be installed through the existing
public resource services. An MCP entry is directly installable only when it uses
the currently supported SSE transport, needs no credential, and declares a
reviewed read-only tool allowlist. Other transports and authenticated MCP entries
remain configuration templates: credentials are always supplied by the user in
the MCP settings flow and are never stored in this catalog.
