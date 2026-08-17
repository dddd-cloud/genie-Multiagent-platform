---
schemaVersion: 1
name: joyagent-mcp-connector
description: Add remote MCP servers that pass JoyAgent McpUrlPolicy and HttpMcpClientAdapter. Adapted from anthropics/skills mcp-builder. Use when wiring GitMCP, Context7, or other HTTPS MCP endpoints into Phase2.
version: 1.0.0
entrypoints:
  - name: main
    runtime: pyodide
    script: scripts/run.py
    description: Validate an MCP server URL against JoyAgent URL policy
    packages: []
---

# JoyAgent MCP Connector

Adapted from [anthropics/skills mcp-builder](https://github.com/anthropics/skills/tree/main/skills/mcp-builder).
JoyAgent does not run a local stdio MCP process. Phase2 stores user MCP servers in MySQL and talks HTTPS.

## Product constraints (from source)

`McpUrlPolicy.java`:

- Scheme must be `https` (http only in `test` profile)
- Host required; `localhost` and raw numeric IPs rejected
- Userinfo and fragment rejected
- After DNS, private/loopback/link-local/CGNAT ranges are forbidden (`DnsAddressPolicy`)

`HttpMcpClientAdapter.java`:

- URL containing `/sse` or ending with `sse` is forwarded to `genie-client` (`AUTOBOTS_AUTOAGENT_MCP_CLIENT_URL`)
- Other URLs get a JSON-RPC POST `tools/list` / `tools/call`
- Bearer over the genie-client SSE path is not supported yet

## Add flow in this product

1. `POST /api/v2/mcp-servers` `{name, serverUrl, authType: NONE|BEARER_TOKEN|QUERY_PARAM, authName, credential}`
2. `POST /api/v2/mcp-servers/{id}/test`
3. `POST /api/v2/mcp-servers/{id}/refresh-tools`
4. `POST /api/v2/mcp-servers/{id}/enable?version=`
5. Bind tools onto an agent as `capabilityKeys: ["mcp:<toolId>"]` — the UUID from `mcp_tool.id`, not the runtime name.

## Public servers that fit a code team

- GitMCP for this origin repo: `https://gitmcp.io/jd-opensource/joyagent-jdgenie` ([idosal/git-mcp](https://github.com/idosal/git-mcp))
- GitMCP any-repo: `https://gitmcp.io/docs`
- Context7 library docs: `https://mcp.context7.com/mcp`

## Script input

```json
{"url": "https://gitmcp.io/jd-opensource/joyagent-jdgenie"}
```
