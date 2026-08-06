import type {
  Phase2McpServerResponse,
  Phase2McpToolResponse,
} from '@/contracts/phase2';
import {
  phase2DeleteWithParams,
  phase2Get,
  phase2Post,
  phase2PostWithParams,
  phase2Put,
} from './client';
import type {
  McpServerCreateRequest,
  McpServerUpdateRequest,
  Phase2VersionBody,
  SetToolEnabledRequest,
} from './internalTypes';

const MCP_BASE = '/api/v2/mcp-servers';

export function listMcpServers(signal?: AbortSignal) {
  return phase2Get<Phase2McpServerResponse[]>(MCP_BASE, undefined, signal);
}

export function getMcpServer(id: string, signal?: AbortSignal) {
  return phase2Get<Phase2McpServerResponse>(`${MCP_BASE}/${id}`, undefined, signal);
}

export function createMcpServer(
  body: McpServerCreateRequest,
  signal?: AbortSignal,
) {
  return phase2Post<Phase2McpServerResponse>(MCP_BASE, body, signal);
}

export function updateMcpServer(
  id: string,
  body: McpServerUpdateRequest,
  signal?: AbortSignal,
) {
  return phase2Put<Phase2McpServerResponse>(`${MCP_BASE}/${id}`, body, signal);
}

export function deleteMcpServer(
  id: string,
  body: Phase2VersionBody,
  signal?: AbortSignal,
) {
  return phase2DeleteWithParams<null>(
    `${MCP_BASE}/${id}`,
    { version: body.version },
    signal,
  );
}

/** Test connectivity — no version query/body required. */
export function testMcpServer(id: string, signal?: AbortSignal) {
  return phase2Post<Phase2McpServerResponse>(
    `${MCP_BASE}/${id}/test`,
    undefined,
    signal,
  );
}

/** Refresh tool discovery — no version query/body required. */
export function refreshMcpTools(id: string, signal?: AbortSignal) {
  return phase2Post<Phase2McpToolResponse[]>(
    `${MCP_BASE}/${id}/refresh-tools`,
    undefined,
    signal,
  );
}

export function enableMcpServer(
  id: string,
  body: Phase2VersionBody,
  signal?: AbortSignal,
) {
  return phase2PostWithParams<Phase2McpServerResponse>(
    `${MCP_BASE}/${id}/enable`,
    { version: body.version },
    undefined,
    signal,
  );
}

export function disableMcpServer(
  id: string,
  body: Phase2VersionBody,
  signal?: AbortSignal,
) {
  return phase2PostWithParams<Phase2McpServerResponse>(
    `${MCP_BASE}/${id}/disable`,
    { version: body.version },
    undefined,
    signal,
  );
}

export function listMcpTools(serverId: string, signal?: AbortSignal) {
  return phase2Get<Phase2McpToolResponse[]>(
    `${MCP_BASE}/${serverId}/tools`,
    undefined,
    signal,
  );
}

export function setMcpToolEnabled(
  serverId: string,
  toolId: string,
  body: SetToolEnabledRequest,
  signal?: AbortSignal,
) {
  return phase2Put<Phase2McpToolResponse>(
    `${MCP_BASE}/${serverId}/tools/${toolId}/enabled`,
    body,
    signal,
  );
}
