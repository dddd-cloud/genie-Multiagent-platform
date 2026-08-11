import type { MvpErrorCode } from '@/contracts/errors';

const PHASE2_ERROR_MESSAGES: Record<MvpErrorCode | string, string> = {
  VALIDATION_ERROR: '请求参数无效，请检查后重试',
  AUTH_REQUIRED: '未登录或登录已过期，请重新登录',
  AUTH_INVALID_CREDENTIALS: '用户名或密码错误',
  INTERNAL_TOKEN_INVALID: '内部认证失败',
  ACCESS_DENIED: '没有权限执行此操作',
  CSRF_INVALID: '安全校验失败，请刷新页面后重试',
  RESOURCE_NOT_FOUND: '资源不存在或无权访问',
  USER_ALREADY_EXISTS: '用户已存在',
  CONVERSATION_BUSY: '会话正在处理中，请稍后再试',
  DUPLICATE_REQUEST: '重复请求，请勿重复提交',
  MESSAGE_STATE_CONFLICT: '消息状态冲突，请刷新后重试',
  SNAPSHOT_TOO_LARGE: '快照过大，无法保存',
  AGENT_DOWNSTREAM_ERROR: 'Agent 下游服务异常，请稍后重试',
  AGENT_NO_FINAL_EVENT: 'Agent 未返回最终结果',
  INTERNAL_ERROR: '服务暂时不可用，请稍后重试',
  DATABASE_UNAVAILABLE: '数据库暂不可用，请稍后重试',
  CLIENT_DISCONNECTED: '连接已断开',
  SERVICE_RESTARTED: '服务已重启，请刷新后重试',
  AGENT_STREAM_INTERRUPTED: 'Agent 流式输出中断',
  SNAPSHOT_INVALID: '快照无效，无法恢复',
  VERSION_CONFLICT: '数据已被他人更新，请刷新后重试',
  AGENT_INVALID_STATE: 'Agent 当前状态不允许此操作',
  AGENT_OFFLINE: 'Agent 已离线，无法执行',
  AGENT_MUST_BE_OFFLINE: '请先将 Agent 下线后再执行此操作',
  SKILL_IN_USE: '该 Skill 正被 Agent 引用，无法删除',
  MODEL_NOT_AVAILABLE: '所选模型不可用，请更换模型',
  PROMPT_INVALID: 'Prompt 配置无效，请检查后重试',
  TOOL_BINDING_INVALID: '工具绑定无效，请检查能力配置',
  MCP_URL_REJECTED: 'MCP 服务地址不被允许',
  MCP_AUTH_INVALID: 'MCP 认证配置无效',
  MCP_UNAVAILABLE: 'MCP 服务暂不可用',
  MCP_DISCOVERY_INVALID: 'MCP 工具发现结果无效',
  TOOL_NOT_BOUND: '工具未绑定或不可用',
  TOOL_INVALID_INPUT: '工具输入参数无效',
  TOOL_TIMEOUT: '工具调用超时',
  TOOL_INVALID_RESPONSE: '工具返回结果无效',
  LOCAL_CONTEXT_INVALID: '本地记忆上下文无效',
  LOCAL_CONTEXT_TOO_LARGE: '本地记忆上下文过大',
  NO_SUITABLE_AGENT: '没有可用的合适 Agent',
  ORCHESTRATION_PLAN_INVALID: '编排计划无效',
  AGENT_INVALID_RESULT: 'Agent 返回结果无效',
  CONTEXT_BUDGET_EXCEEDED: '上下文预算已超限',
  MEMORY_ANALYSIS_FAILED: '记忆分析失败，请稍后重试',
  SUMMARY_FAILED: '会话摘要生成失败，请稍后重试',
  SKILL_PACKAGE_INVALID: 'Skill 包无效或格式不合法',
  SKILL_RESOURCE_NOT_FOUND: 'Skill 资源不存在',
  SKILL_ENTRYPOINT_NOT_FOUND: 'Skill 入口不存在',
  SKILL_EXECUTION_FAILED: 'Skill 执行失败',
  SKILL_EXECUTION_TIMEOUT: 'Skill 执行超时',
};

const DEFAULT_MESSAGE = '操作失败，请稍后重试';

export function getPhase2ErrorMessage(code: string, fallback?: string): string {
  if (code && PHASE2_ERROR_MESSAGES[code]) {
    return PHASE2_ERROR_MESSAGES[code];
  }
  if (fallback && fallback.trim()) {
    return fallback;
  }
  return DEFAULT_MESSAGE;
}
