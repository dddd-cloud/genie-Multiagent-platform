import type {
  OrchestrationRoute,
  StepMode,
} from '@/contracts';
import type {
  OrchestrationUiState,
  StepUiStatus,
  SubTaskUiStatus,
} from './types';

const PROTOCOL_TOKEN =
  /\b(ORCHESTRATED|DIRECT|MAIN_ONLY|SINGLE_AGENT|PARALLEL_AGENTS|RESOURCE_CREATION_REQUEST|FORCED_BY_REQUEST|ROUTER_FALLBACK|MULTI_STEP|MULTI_AGENT_DETECTED|ROUTE_SELECTED|PLAN_CREATED|STEP_STARTED|STEP_COMPLETED|STEP_FAILED|SUBTASK_STARTED|SUBTASK_COMPLETED|SUMMARY_STARTED|SUMMARY_COMPLETED|FINAL_RESPONSE|SUCCESS|PARTIAL|INTERRUPTED|IDLE|RUNNING|COMPLETED|FAILED|PLANNED|SKIPPED|DEGRADED)\b/;

export function humanRouteTitle(route: OrchestrationRoute | null): string {
  if (route === 'DIRECT') {
    return '由主规划直接作答';
  }
  return '已选择编排执行';
}

export function humanRouteReason(reasonCode: string | null | undefined): string {
  switch ((reasonCode || '').trim()) {
    case 'RESOURCE_CREATION_REQUEST':
      return '正在创建所需资源';
    case 'FORCED_BY_REQUEST':
      return '按你选择的协作模式执行';
    case 'MULTI_STEP':
    case 'MULTI_AGENT':
    case 'MULTI_AGENT_DETECTED':
      return '这个问题需要多位专家一起完成';
    case 'ONLY_ONE_CANDIDATE':
    case 'SINGLE_CAPABILITY':
      return '已匹配到合适的专家';
    case 'SOLO_AGENT':
      return '已把对话交给所选专家';
    case 'AUTO_SINGLE_AGENT':
    case 'MATCHED_SPECIALIST':
      return '一位专家就能完成，主规划已退出';
    case 'AUTO_TEAM':
    case 'ONLY_ONE_TEAM':
    case 'EXPLICIT_TEAM':
      return '需要团队协作，主规划已把对话交给团队主规划';
    default:
      return '';
  }
}

export function humanRouteSubtitle(state: OrchestrationUiState): string {
  const title = humanRouteTitle(state.route);
  const reason = humanRouteReason(state.routeReasonCode);
  return reason ? `${title}，${reason}` : title;
}

export function humanStepMode(mode: StepMode | null | undefined): string | null {
  switch (mode) {
    case 'PARALLEL_AGENTS':
      return '并行协作';
    case 'MAIN_ONLY':
      return '主规划亲自执行';
    case 'SINGLE_AGENT':
      return '专家执行';
    default:
      return null;
  }
}

export function humanStepStatus(status: StepUiStatus | SubTaskUiStatus | undefined): string {
  switch (status) {
    case 'RUNNING':
      return '进行中';
    case 'COMPLETED':
      return '已完成';
    case 'FAILED':
      return '未完成';
    case 'SKIPPED':
      return '已跳过';
    case 'DEGRADED':
      return '部分完成';
    case 'PLANNED':
      return '等待中';
    default:
      return '';
  }
}

export function looksLikeResultJsonFragment(text: string): boolean {
  const trimmed = (text || '').trim();
  if (!trimmed) {
    return false;
  }
  if (/"errorCode"|"retryable"/.test(trimmed)) {
    return true;
  }
  if (/"status"\s*:\s*"(SUCCESS|FAILURE)"/.test(trimmed)) {
    return true;
  }
  return /errorCode/.test(trimmed) && /retryable|":"|","/.test(trimmed);
}

export function looksLikeInternalStatus(text: string): boolean {
  const trimmed = (text || '').trim();
  if (!trimmed) {
    return true;
  }
  if (/^开始执行[：:]/.test(trimmed)) {
    return true;
  }
  if (/^已将/.test(trimmed) && /交给/.test(trimmed)) {
    return true;
  }
  if (/^(规划中|由主规划直接作答)$/.test(trimmed)) {
    return true;
  }
  if (/^已选择编排执行/.test(trimmed)) {
    return true;
  }
  if (/^主规划(已安排|正在安排)/.test(trimmed)) {
    return true;
  }
  if (/^(准备读写文件|正在判断需要哪些资料)$/.test(trimmed)) {
    return true;
  }
  return false;
}

export function looksLikeProtocolDump(text: string): boolean {
  const trimmed = (text || '').trim();
  if (!trimmed) {
    return true;
  }
  if (looksLikeResultJsonFragment(trimmed)) {
    return true;
  }
  if (/路由决策/.test(trimmed) && PROTOCOL_TOKEN.test(trimmed)) {
    return true;
  }
  if (/^任务安排：/.test(trimmed)) {
    return true;
  }
  if (/^\s*-\s*\[[^\]]+\]/.test(trimmed)) {
    return true;
  }
  if (PROTOCOL_TOKEN.test(trimmed) && !/[\u4e00-\u9fff]/.test(trimmed)) {
    return true;
  }
  return false;
}

export function stripProtocolTokens(text: string): string {
  return (text || '')
    .replace(PROTOCOL_TOKEN, '')
    .replace(/[（(]\s*[）)]/g, '')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

const ERROR_COPY: Record<string, string> = {
  AGENT_INVALID_RESULT: '这次没能形成可用结论，已把过程回报给主规划',
  EXECUTION_ERROR: '执行中断，已回报主规划',
  TOOL_TIMEOUT: '工具超时，已回报主规划',
  TOOL_UNAVAILABLE: '工具暂不可用，已回报主规划',
  AGENT_OFFLINE: '专家当前不可用，已回报主规划',
  CONTEXT_BUDGET_EXCEEDED: '上下文过长，已回报主规划',
};

export function humanErrorMessage(code: string | null | undefined): string {
  const trimmed = (code || '').trim();
  if (!trimmed) {
    return '出现了一点问题，已回报主规划';
  }
  if (ERROR_COPY[trimmed]) {
    return ERROR_COPY[trimmed];
  }
  if (/^[A-Z][A-Z0-9_]+$/.test(trimmed)) {
    return '这次没能完成，已回报主规划';
  }
  return trimmed;
}

export function looksLikeUuid(value: string | null | undefined): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    (value || '').trim(),
  );
}

export function displayAgentName(step: {
  agentName?: string | null;
  agentId?: string | null;
  fallback?: string;
}): string {
  const fallback = step.fallback || '专家';
  const name = (step.agentName || '').trim();
  if (name && !looksLikeUuid(name)) {
    return name;
  }
  const id = (step.agentId || '').trim();
  if (id && !looksLikeUuid(id)) {
    return id;
  }
  return fallback;
}
