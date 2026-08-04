#!/usr/bin/env node
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv from 'ajv/dist/2020.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const uiRoot = resolve(__dirname, '..');
const repoRoot = resolve(uiRoot, '..');
const contractDir = join(repoRoot, 'docs/mvp-contract');
const uiContractsDir = join(uiRoot, 'src/contracts');
const backendContractDir = join(
  repoRoot,
  'genie-backend/src/main/java/com/jd/genie/platform/contract'
);
const docsDir = join(repoRoot, 'docs/document');
const phase1DocsDir = existsSync(join(docsDir, 'phase1'))
  ? join(docsDir, 'phase1')
  : docsDir;
const baselineDoc = join(phase1DocsDir, '00_JoyAgent_MVP开发前契约冻结基线_MVP-CONTRACT-002.md');
const chatTsPath = join(uiRoot, 'src/utils/chat.ts');
const phase2SchemaDir = join(contractDir, 'phase2/schema');
const phase2FixturesDir = join(uiRoot, 'src/mocks/phase2/fixtures');
const phase2ProtectedBaseline = join(contractDir, 'phase2/protected-baseline.sha256');
const phase2BackendDir = join(
  repoRoot,
  'genie-backend/src/main/java/com/jd/genie/platform/phase2contract'
);
const phase2TsDir = join(uiContractsDir, 'phase2');

const POSITIVE_SNAPSHOTS = [
  'react-success.json',
  'plan-success.json',
  'failed.json',
  'interrupted.json',
  'truncated.json',
];

const EXPECTED_CONFIG_VARS = [
  'GENIE_DB_URL',
  'GENIE_DB_USERNAME',
  'GENIE_DB_PASSWORD',
  'GENIE_BOOTSTRAP_ADMIN_USERNAME',
  'GENIE_BOOTSTRAP_ADMIN_PASSWORD',
  'GENIE_INTERNAL_AGENT_TOKEN',
  'GENIE_SESSION_TIMEOUT',
  'GENIE_STREAM_SNAPSHOT_MAX_BYTES',
  'GENIE_HISTORY_MAX_TURNS',
  'GENIE_HISTORY_MAX_CHARACTERS',
  'GENIE_SSE_TIMEOUT_MILLIS',
  'MVP_FAKE_AGENT_MODE',
  'MVP_FAKE_AGENT_EVENT_COUNT',
  'MVP_FAKE_AGENT_DELAY_MS',
  'MVP_ACCEPTANCE_USER_PASSWORD',
  'MVP_ACCEPTANCE_ADMIN_PASSWORD',
];

const EXPECTED_ERROR_CODES = [
  'VALIDATION_ERROR',
  'AUTH_REQUIRED',
  'AUTH_INVALID_CREDENTIALS',
  'INTERNAL_TOKEN_INVALID',
  'ACCESS_DENIED',
  'CSRF_INVALID',
  'RESOURCE_NOT_FOUND',
  'USER_ALREADY_EXISTS',
  'CONVERSATION_BUSY',
  'DUPLICATE_REQUEST',
  'MESSAGE_STATE_CONFLICT',
  'SNAPSHOT_TOO_LARGE',
  'AGENT_DOWNSTREAM_ERROR',
  'AGENT_NO_FINAL_EVENT',
  'INTERNAL_ERROR',
  'DATABASE_UNAVAILABLE',
  'CLIENT_DISCONNECTED',
  'SERVICE_RESTARTED',
  'AGENT_STREAM_INTERRUPTED',
  'SNAPSHOT_INVALID',
  'VERSION_CONFLICT',
  'AGENT_INVALID_STATE',
  'AGENT_OFFLINE',
  'AGENT_MUST_BE_OFFLINE',
  'SKILL_IN_USE',
  'MODEL_NOT_AVAILABLE',
  'PROMPT_INVALID',
  'TOOL_BINDING_INVALID',
  'MCP_URL_REJECTED',
  'MCP_AUTH_INVALID',
  'MCP_UNAVAILABLE',
  'MCP_DISCOVERY_INVALID',
  'TOOL_NOT_BOUND',
  'TOOL_INVALID_INPUT',
  'TOOL_TIMEOUT',
  'TOOL_INVALID_RESPONSE',
  'LOCAL_CONTEXT_INVALID',
  'LOCAL_CONTEXT_TOO_LARGE',
  'NO_SUITABLE_AGENT',
  'ORCHESTRATION_PLAN_INVALID',
  'AGENT_INVALID_RESULT',
  'CONTEXT_BUDGET_EXCEEDED',
  'MEMORY_ANALYSIS_FAILED',
  'SUMMARY_FAILED',
];

const MODULE_DOCS = [
  '01_数据库与身份安全模块_开发验收方案.md',
  '02_会话与消息后端模块_开发验收方案.md',
  '03_Agent流式持久化与上下文桥接模块_开发验收方案.md',
  '04_前端整合与全链路验收模块_开发验收方案.md',
];

const PHASE2_SCHEMA_FILES = [
  'phase2-gpt-query-v1.schema.json',
  'agent-capability-summary-v1.schema.json',
  'agent-runtime-profile-v1.schema.json',
  'tool-binding-view-v1.schema.json',
  'orchestration-event-v1.schema.json',
  'memory-patch-v1.schema.json',
  'management-api-v1.schema.json',
];

const PHASE2_PROGRESS_EVENT_TYPES = new Set([
  'ROUTE_SELECTED',
  'PLAN_CREATED',
  'STEP_STARTED',
  'STEP_COMPLETED',
  'STEP_FAILED',
  'STEP_SKIPPED',
  'REPLAN_STARTED',
  'SUMMARY_STARTED',
  'SUMMARY_COMPLETED',
  'SUMMARY_FALLBACK',
]);

const PHASE2_REQUIRED_FIXTURES = [
  'agents-list.json',
  'agent-detail.json',
  'agent-raw-detail.json',
  'skills-list.json',
  'skill-detail.json',
  'models.json',
  'mcp-server-detail.json',
  'mcp-tools.json',
  'memory-patch.json',
  'memory-summary.json',
  'memory-patch-invalid.json',
  'agent-version-conflict.json',
  'skill-in-use.json',
  'mcp-url-rejected.json',
  'mcp-auth-invalid.json',
  'mcp-discovery-invalid.json',
  'mcp-unavailable.json',
  'direct-success.ndjson',
  'direct-failure.ndjson',
  'orchestrated-success.ndjson',
  'orchestrated-replan.ndjson',
  'orchestrated-summary-fallback.ndjson',
  'snapshot-orchestrated-success.json',
  'snapshot-orchestrated-truncated.json',
  'snapshot-orchestrated-malformed.txt',
];

const PHASE2_MANAGEMENT_FIXTURES = [
  'agents-list.json',
  'agent-detail.json',
  'agent-raw-detail.json',
  'skills-list.json',
  'skill-detail.json',
  'models.json',
  'mcp-server-detail.json',
  'mcp-tools.json',
];

const PHASE2_ERROR_FIXTURES = {
  'agent-version-conflict.json': 'VERSION_CONFLICT',
  'skill-in-use.json': 'SKILL_IN_USE',
  'mcp-url-rejected.json': 'MCP_URL_REJECTED',
  'mcp-auth-invalid.json': 'MCP_AUTH_INVALID',
  'mcp-discovery-invalid.json': 'MCP_DISCOVERY_INVALID',
  'mcp-unavailable.json': 'MCP_UNAVAILABLE',
};

const PHASE2_SCHEMA_DTO_MIRRORS = [
  {
    schemaFile: 'agent-capability-summary-v1.schema.json',
    javaRecord: 'AgentCapabilitySummary',
    javaPath: 'dto/AgentCapabilitySummary.java',
    tsInterface: 'AgentCapabilitySummary',
    tsFile: 'runtime.ts',
  },
  {
    schemaFile: 'agent-runtime-profile-v1.schema.json',
    javaRecord: 'AgentRuntimeProfile',
    javaPath: 'dto/AgentRuntimeProfile.java',
    tsInterface: 'AgentRuntimeProfile',
    tsFile: 'runtime.ts',
  },
  {
    schemaFile: 'tool-binding-view-v1.schema.json',
    javaRecord: 'ToolBindingView',
    javaPath: 'dto/ToolBindingView.java',
    tsInterface: 'ToolBindingView',
    tsFile: 'runtime.ts',
  },
];

const PHASE2_SECRET_FIELD_NAMES = new Set([
  'authorization',
  'bearer',
  'token',
  'accesstoken',
  'refreshtoken',
  'cookie',
  'password',
  'apikey',
  'api_key',
  'clientsecret',
  'credential',
  'credentialenvelope',
  'x-genie-internal-token',
  'genie_session',
  'xsrf-token',
  'baseurl',
  'base_url',
  'header',
]);

const GPT_PROCESS_RESULT_FIELDS = [
  'status',
  'response',
  'responseAll',
  'finished',
  'useTimes',
  'useTokens',
  'resultMap',
  'responseType',
  'traceId',
  'reqId',
  'encrypted',
  'query',
  'messages',
  'packageType',
  'errorMsg',
];

const TASK_RESULT_TYPES = [
  'result',
  'task_summary',
  'tool_result',
  'browser',
  'code',
  'html',
  'file',
  'knowledge',
  'deep_search',
  'markdown',
  'ppt',
  'data_analysis',
];

const MESSAGE_FIELDS = [
  'id',
  'turnNo',
  'role',
  'status',
  'requestId',
  'content',
  'streamSnapshot',
  'payloadVersion',
  'deepThink',
  'outputStyle',
  'errorCode',
  'errorMessage',
  'createdAt',
  'updatedAt',
];

const SECRET_VALUE_PATTERNS = [
  /X-Genie-Internal-Token/i,
  /GENIE_SESSION=/i,
  /passwordHash/i,
];

let failures = 0;

function fail(message) {
  console.error(`FAIL: ${message}`);
  failures += 1;
}

function pass(message) {
  console.log(`PASS: ${message}`);
}

function readText(path) {
  return readFileSync(path, 'utf8');
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function assertType(value, expected, label) {
  if (expected === 'string|null') {
    if (value !== null && typeof value !== 'string') {
      fail(`${label}: expected string|null, got ${typeof value}`);
      return false;
    }
    return true;
  }
  if (expected === 'string[]|null') {
    if (value === null) return true;
    if (!Array.isArray(value) || value.some((item) => typeof item !== 'string')) {
      fail(`${label}: expected string[]|null`);
      return false;
    }
    return true;
  }
  if (expected === 'object|null') {
    if (value !== null && !isPlainObject(value)) {
      fail(`${label}: expected object|null`);
      return false;
    }
    return true;
  }
  if (typeof value !== expected) {
    fail(`${label}: expected ${expected}, got ${typeof value}`);
    return false;
  }
  return true;
}

function validateGptProcessResultEvent(event, sourceName, options = {}) {
  const allowHeartbeat = options.allowHeartbeat === true;
  if (!isPlainObject(event)) {
    fail(`${sourceName}: event must be a JSON object`);
    return false;
  }

  let ok = true;
  for (const field of GPT_PROCESS_RESULT_FIELDS) {
    if (!(field in event)) {
      fail(`${sourceName}: missing required field ${field}`);
      ok = false;
    }
  }
  if (!ok) return false;

  ok = assertType(event.status, 'string|null', `${sourceName}.status`) && ok;
  ok = assertType(event.response, 'string', `${sourceName}.response`) && ok;
  ok = assertType(event.responseAll, 'string', `${sourceName}.responseAll`) && ok;
  ok = assertType(event.finished, 'boolean', `${sourceName}.finished`) && ok;
  ok = assertType(event.useTimes, 'number', `${sourceName}.useTimes`) && ok;
  ok = assertType(event.useTokens, 'number', `${sourceName}.useTokens`) && ok;
  ok = assertType(event.resultMap, 'object|null', `${sourceName}.resultMap`) && ok;
  ok = assertType(event.responseType, 'string', `${sourceName}.responseType`) && ok;
  ok = assertType(event.traceId, 'string|null', `${sourceName}.traceId`) && ok;
  ok = assertType(event.reqId, 'string|null', `${sourceName}.reqId`) && ok;
  ok = assertType(event.encrypted, 'boolean', `${sourceName}.encrypted`) && ok;
  ok = assertType(event.query, 'string|null', `${sourceName}.query`) && ok;
  ok = assertType(event.messages, 'string[]|null', `${sourceName}.messages`) && ok;
  ok = assertType(event.packageType, 'string', `${sourceName}.packageType`) && ok;
  ok = assertType(event.errorMsg, 'string|null', `${sourceName}.errorMsg`) && ok;

  // heartbeat must be judged on parsed JSON field, never raw-text regex.
  if (event.packageType === 'heartbeat' && !allowHeartbeat) {
    fail(`${sourceName}: heartbeat event is not allowed in Snapshot fixtures`);
    ok = false;
  }

  checkSecretValues(event, sourceName);
  return ok;
}

function checkSecretValues(value, label, seen = new WeakSet()) {
  if (value === null || typeof value !== 'object') {
    if (typeof value === 'string') {
      for (const pattern of SECRET_VALUE_PATTERNS) {
        if (pattern.test(value)) {
          fail(`${label}: contains forbidden secret value pattern ${pattern}`);
        }
      }
    }
    return;
  }
  if (seen.has(value)) return;
  seen.add(value);
  if (Array.isArray(value)) {
    value.forEach((item, index) => checkSecretValues(item, `${label}[${index}]`, seen));
    return;
  }
  for (const [key, child] of Object.entries(value)) {
    checkSecretValues(child, `${label}.${key}`, seen);
  }
}

function extractFinalAnswer(events) {
  for (let i = events.length - 1; i >= 0; i -= 1) {
    const event = events[i];
    if (event.finished === true && typeof event.responseAll === 'string' && event.responseAll.trim()) {
      return event.responseAll.trim();
    }
  }
  for (let i = events.length - 1; i >= 0; i -= 1) {
    const event = events[i];
    if (event.finished === true && typeof event.response === 'string' && event.response.trim()) {
      return event.response.trim();
    }
  }
  for (let i = events.length - 1; i >= 0; i -= 1) {
    const eventData = eventDataOf(events[i]);
    if (!eventData) continue;
    const nested = eventData.resultMap;
    if (!isPlainObject(nested)) continue;
    for (const key of ['taskSummary', 'result']) {
      if (typeof nested[key] === 'string' && nested[key].trim()) {
        return nested[key].trim();
      }
    }
  }
  return null;
}

function eventDataOf(event) {
  if (!isPlainObject(event.resultMap)) return null;
  const eventData = event.resultMap.eventData;
  return isPlainObject(eventData) ? eventData : null;
}

function hasHeartbeat(events) {
  return events.some((event) => event.packageType === 'heartbeat');
}

function validateSnapshotEnvelope(data, fileName) {
  if (data.payloadVersion !== 1) {
    fail(`${fileName}: payloadVersion must be 1`);
    return;
  }
  if (typeof data.truncated !== 'boolean') {
    fail(`${fileName}: truncated must be boolean`);
    return;
  }
  if (!Array.isArray(data.events)) {
    fail(`${fileName}: events must be an array`);
    return;
  }
  if (hasHeartbeat(data.events)) {
    fail(`${fileName}: snapshot must not contain heartbeat events`);
  }
  data.events.forEach((event, index) => {
    validateGptProcessResultEvent(event, `${fileName} events[${index}]`);
  });
}

function validateReactSuccessSnapshot(data, fileName) {
  validateSnapshotEnvelope(data, fileName);
  if (data.truncated !== false) fail(`${fileName}: truncated must be false`);
  if (data.events.length < 2) fail(`${fileName}: requires at least 2 events`);
  if (!data.events.some((event) => event.finished === false)) {
    fail(`${fileName}: requires at least one non-finished event`);
  }
  if (!data.events.some((event) => event.finished === true)) {
    fail(`${fileName}: requires a finished final event`);
  }
  if (!extractFinalAnswer(data.events)) {
    fail(`${fileName}: final answer cannot be extracted`);
  } else {
    pass(`${fileName}: final answer extractable`);
  }
}

function validatePlanSuccessSnapshot(data, fileName, recognizedTypes) {
  validateSnapshotEnvelope(data, fileName);
  if (data.truncated !== false) fail(`${fileName}: truncated must be false`);
  if (data.events.length < 2) fail(`${fileName}: requires at least 2 events`);
  if (!data.events.some((event) => event.finished === false)) {
    fail(`${fileName}: requires at least one non-finished event`);
  }
  if (!data.events.some((event) => event.finished === true)) {
    fail(`${fileName}: requires a finished final event`);
  }
  const eventDatas = data.events.map(eventDataOf).filter(Boolean);
  if (eventDatas.length === 0) {
    fail(`${fileName}: requires resultMap.eventData objects`);
    return;
  }
  const messageTypes = new Set(eventDatas.map((item) => item.messageType).filter(Boolean));
  if (!['plan', 'plan_thought', 'task'].some((type) => messageTypes.has(type))) {
    fail(`${fileName}: requires plan/plan_thought/task eventData.messageType`);
  }
  let hasRecognizedTaskResult = false;
  for (const eventData of eventDatas) {
    if (eventData.messageType === 'plan' && !isPlainObject(eventData.resultMap)) {
      fail(`${fileName}: plan eventData.resultMap must be object`);
    }
    if (eventData.messageType === 'task') {
      if (!eventData.taskId) fail(`${fileName}: task eventData.taskId required`);
      if (!isPlainObject(eventData.resultMap)) {
        fail(`${fileName}: task eventData.resultMap required`);
      } else if (!recognizedTypes.has(eventData.resultMap.messageType)) {
        fail(`${fileName}: task result type ${eventData.resultMap.messageType} not recognized by chat.ts`);
      } else {
        hasRecognizedTaskResult = true;
      }
    }
  }
  if (!hasRecognizedTaskResult) {
    fail(`${fileName}: requires at least one handleTaskData-recognized task result type`);
  }
  if (!extractFinalAnswer(data.events)) {
    fail(`${fileName}: final answer cannot be extracted`);
  } else {
    pass(`${fileName}: plan/task path and final answer validated`);
  }
}

function validateFailedSnapshot(data, fileName) {
  validateSnapshotEnvelope(data, fileName);
  const failedFinal = data.events.find((event) => event.finished === true && event.status === 'failed');
  if (!failedFinal) fail(`${fileName}: requires finished failed event`);
  if (!failedFinal?.errorMsg || !String(failedFinal.errorMsg).trim()) {
    fail(`${fileName}: failed event requires non-empty errorMsg`);
  }
  if (extractFinalAnswer(data.events)) {
    fail(`${fileName}: must not contain extractable success final answer`);
  } else {
    pass(`${fileName}: failed semantics validated`);
  }
}

function validateInterruptedSnapshot(data, fileName) {
  validateSnapshotEnvelope(data, fileName);
  if (data.events.some((event) => event.finished === true && event.status === 'success')) {
    fail(`${fileName}: must not contain successful final event`);
  }
  if (extractFinalAnswer(data.events)) {
    fail(`${fileName}: must not contain extractable success final answer`);
  } else {
    pass(`${fileName}: interrupted semantics validated`);
  }
}

function validateTruncatedSnapshot(data, fileName) {
  validateSnapshotEnvelope(data, fileName);
  if (data.truncated !== true) fail(`${fileName}: truncated must be true`);
  if (!data.events.some((event) => event.finished === true)) {
    fail(`${fileName}: must retain a finished final event`);
  }
  if (!extractFinalAnswer(data.events)) {
    fail(`${fileName}: final answer must remain extractable`);
  } else {
    pass(`${fileName}: truncated semantics validated`);
  }
}

function loadSchema() {
  return JSON.parse(readText(join(contractDir, 'schema/stream-snapshot-v1.schema.json')));
}

function validateSnapshots(ajv) {
  const validate = ajv.compile(loadSchema());
  const snapshotDir = join(contractDir, 'fixtures/snapshot');
  const chatTs = readText(chatTsPath);
  const recognizedTypes = extractRecognizedTaskResultTypes(chatTs);

  for (const name of POSITIVE_SNAPSHOTS) {
    const filePath = join(snapshotDir, name);
    const data = JSON.parse(readText(filePath));
    if (!validate(data)) {
      fail(`Snapshot ${name} failed schema: ${JSON.stringify(validate.errors)}`);
      continue;
    }
    pass(`Snapshot ${name} passes schema`);

    if (name === 'react-success.json') validateReactSuccessSnapshot(data, name);
    if (name === 'plan-success.json') validatePlanSuccessSnapshot(data, name, recognizedTypes);
    if (name === 'failed.json') validateFailedSnapshot(data, name);
    if (name === 'interrupted.json') validateInterruptedSnapshot(data, name);
    if (name === 'truncated.json') validateTruncatedSnapshot(data, name);
  }

  const invalidVersion = JSON.parse(readText(join(snapshotDir, 'invalid-version.json')));
  if (validate(invalidVersion)) {
    fail('invalid-version.json should be rejected by schema');
  } else {
    pass('invalid-version.json rejected by schema');
  }

  try {
    JSON.parse(readText(join(snapshotDir, 'malformed-json.txt')));
    fail('malformed-json.txt should not parse as JSON');
  } catch {
    pass('malformed-json.txt cannot be parsed');
  }
}

function extractRecognizedTaskResultTypes(chatTs) {
  const match = chatTs.match(/switch \(messageType\) \{([\s\S]*?)default:/);
  const block = match ? match[1] : '';
  const types = new Set([...block.matchAll(/case\s+"([^"]+)":/g)].map((m) => m[1]));
  for (const type of TASK_RESULT_TYPES) types.add(type);
  return types;
}

function validateChatExports() {
  const chatTs = readText(chatTsPath);
  for (const symbol of ['combineData', 'handleTaskData']) {
    if (!new RegExp(`export\\s+(const|function)\\s+${symbol}\\b`).test(chatTs)) {
      fail(`chat.ts must export ${symbol}`);
    }
  }
  for (const type of ['plan', 'plan_thought', 'task']) {
    if (!new RegExp(`case\\s+"${type}"`).test(chatTs)) {
      fail(`combineData must recognize messageType ${type}`);
    }
  }
  pass('chat.ts exports and combineData message types validated');
}

function parseSseFile(fileName) {
  const content = readText(join(contractDir, 'fixtures/sse', fileName));
  return content.split('\n').map((line) => line.trim()).filter(Boolean);
}

function validateSseFixtures() {
  const scenarios = {
    'success-react.ndjson': (events) => {
      if (events.length < 2) fail('success-react.ndjson requires at least 2 events');
      if (!extractFinalAnswer(events)) fail('success-react.ndjson final answer missing');
      else pass('success-react.ndjson scenario semantics validated');
    },
    'success-plan.ndjson': (events) => {
      if (!events.some((event) => eventDataOf(event))) {
        fail('success-plan.ndjson requires resultMap.eventData');
      }
      const types = events.map(eventDataOf).filter(Boolean).map((item) => item.messageType);
      if (!types.some((type) => ['plan', 'plan_thought', 'task'].includes(type))) {
        fail('success-plan.ndjson requires plan/plan_thought/task path');
      }
      if (!extractFinalAnswer(events)) fail('success-plan.ndjson final answer missing');
      else pass('success-plan.ndjson scenario semantics validated');
    },
    'client-visible-failure.ndjson': (events) => {
      const last = events[events.length - 1];
      if (!last || last.finished !== true || last.status !== 'failed') {
        fail('client-visible-failure.ndjson requires final failed event');
      }
      if (!last?.errorMsg || !String(last.errorMsg).trim()) {
        fail('client-visible-failure.ndjson requires non-empty errorMsg');
      }
      if (extractFinalAnswer(events)) {
        fail('client-visible-failure.ndjson must not contain success final answer');
      } else {
        pass('client-visible-failure.ndjson scenario semantics validated');
      }
    },
    'slow-stream.ndjson': (events) => {
      if (events.length < 2) fail('slow-stream.ndjson requires at least 2 events');
      else pass('slow-stream.ndjson ordered multi-event stream validated');
    },
  };

  for (const [fileName, semanticCheck] of Object.entries(scenarios)) {
    const events = parseSseFile(fileName).map((line, index) => {
      const event = JSON.parse(line);
      // SSE may include heartbeat when the scenario needs it; Snapshot must not.
      validateGptProcessResultEvent(event, `${fileName} line ${index + 1}`, {
        allowHeartbeat: true,
      });
      return event;
    });
    semanticCheck(events.filter((event) => event.packageType !== 'heartbeat'));
    pass(`SSE ${fileName} event structure validated`);
  }
}

function assertApiResponseTop(data, fileName) {
  if (typeof data.code !== 'string') fail(`${fileName}: code must be string`);
  if (typeof data.message !== 'string') fail(`${fileName}: message must be string`);
  if (!('data' in data)) fail(`${fileName}: missing data field`);
}

function validateMessageItem(message, fileName, index) {
  const label = `${fileName} messages[${index}]`;
  for (const field of MESSAGE_FIELDS) {
    if (!(field in message)) fail(`${label}: missing ${field}`);
  }
  if (!['USER', 'ASSISTANT'].includes(message.role)) {
    fail(`${label}: invalid role ${message.role}`);
  }
  if (!['PENDING', 'STREAMING', 'COMPLETED', 'FAILED', 'INTERRUPTED'].includes(message.status)) {
    fail(`${label}: invalid status ${message.status}`);
  }
  if (message.streamSnapshot !== null && typeof message.streamSnapshot !== 'string') {
    fail(`${label}: streamSnapshot must be string|null`);
  }
  if (message.streamSnapshot) {
    try {
      const snapshot = JSON.parse(message.streamSnapshot);
      if (snapshot.payloadVersion !== 1) fail(`${label}: streamSnapshot payloadVersion must be 1`);
      if (hasHeartbeat(snapshot.events ?? [])) fail(`${label}: streamSnapshot must not contain heartbeat`);
    } catch {
      fail(`${label}: streamSnapshot must parse as JSON`);
    }
  }
  if (message.payloadVersion !== 1) fail(`${label}: payloadVersion must be 1`);
}

function validateMswFixtures() {
  const mswDir = join(contractDir, 'fixtures/msw');

  const authSuccess = JSON.parse(readText(join(mswDir, 'auth-success.json')));
  assertApiResponseTop(authSuccess, 'auth-success.json');
  if (authSuccess.code !== 'OK') fail('auth-success.json code must be OK');
  if (!isPlainObject(authSuccess.data)) fail('auth-success.json data must be object');
  for (const field of ['id', 'username', 'displayName', 'role']) {
    if (typeof authSuccess.data[field] !== 'string') {
      fail(`auth-success.json data.${field} must be string`);
    }
  }
  if (!['ADMIN', 'USER'].includes(authSuccess.data.role)) {
    fail('auth-success.json data.role invalid');
  }
  for (const forbidden of ['tenantId', 'passwordHash', 'sessionId']) {
    if (forbidden in authSuccess.data) fail(`auth-success.json must not expose ${forbidden}`);
  }
  pass('MSW auth-success.json validated');

  const auth401 = JSON.parse(readText(join(mswDir, 'auth-401.json')));
  assertApiResponseTop(auth401, 'auth-401.json');
  if (!['AUTH_REQUIRED', 'AUTH_INVALID_CREDENTIALS'].includes(auth401.code)) {
    fail('auth-401.json code invalid');
  }
  if (auth401.data !== null) fail('auth-401.json data must be null');
  pass('MSW auth-401.json validated');

  const emptyConversations = JSON.parse(readText(join(mswDir, 'empty-conversations.json')));
  assertApiResponseTop(emptyConversations, 'empty-conversations.json');
  if (emptyConversations.code !== 'OK') fail('empty-conversations.json code must be OK');
  const page = emptyConversations.data;
  if (!Array.isArray(page.items) || page.items.length !== 0) {
    fail('empty-conversations.json items must be empty array');
  }
  if (typeof page.page !== 'number') fail('empty-conversations.json data.page must be number');
  if (typeof page.pageSize !== 'number') fail('empty-conversations.json data.pageSize must be number');
  if (typeof page.hasMore !== 'boolean') fail('empty-conversations.json data.hasMore must be boolean');
  if ('total' in page || 'totalPages' in page) {
    fail('empty-conversations.json must not contain total/totalPages');
  }
  pass('MSW empty-conversations.json validated');

  const busy = JSON.parse(readText(join(mswDir, 'conversation-busy.json')));
  assertApiResponseTop(busy, 'conversation-busy.json');
  if (busy.code !== 'CONVERSATION_BUSY' || busy.data !== null) {
    fail('conversation-busy.json invalid');
  }
  pass('MSW conversation-busy.json validated');

  const isolation = JSON.parse(readText(join(mswDir, 'user-isolation-404.json')));
  assertApiResponseTop(isolation, 'user-isolation-404.json');
  if (isolation.code !== 'RESOURCE_NOT_FOUND' || isolation.data !== null) {
    fail('user-isolation-404.json invalid');
  }
  pass('MSW user-isolation-404.json validated');

  const historyFiles = {
    'conversation-with-react-history.json': null,
    'conversation-with-plan-history.json': null,
    'conversation-failed.json': 'FAILED',
    'conversation-interrupted.json': 'INTERRUPTED',
  };

  for (const [fileName, expectedAssistantStatus] of Object.entries(historyFiles)) {
    const payload = JSON.parse(readText(join(mswDir, fileName)));
    assertApiResponseTop(payload, fileName);
    if (payload.code !== 'OK') fail(`${fileName}: code must be OK`);
    if (!Array.isArray(payload.data?.messages)) fail(`${fileName}: data.messages must be array`);
    payload.data.messages.forEach((message, index) => validateMessageItem(message, fileName, index));
    if (expectedAssistantStatus) {
      const assistant = payload.data.messages.find((message) => message.role === 'ASSISTANT');
      if (!assistant || assistant.status !== expectedAssistantStatus) {
        fail(`${fileName}: ASSISTANT.status must be ${expectedAssistantStatus}`);
      }
    }
    pass(`MSW ${fileName} validated`);
  }
}

function extractJavaRecordComponents(javaSource, recordName) {
  const headerMatch = javaSource.match(
    new RegExp(`(?:public\\s+)?record\\s+${recordName}(?:<[^>]+>)?\\s*\\(`)
  );
  if (!headerMatch) return null;

  const start = headerMatch.index + headerMatch[0].length;
  let parenDepth = 1;
  let params = '';
  for (let i = start; i < javaSource.length; i += 1) {
    const ch = javaSource[i];
    if (ch === '(') parenDepth += 1;
    else if (ch === ')') {
      parenDepth -= 1;
      if (parenDepth === 0) break;
    }
    params += ch;
  }

  const components = [];
  let genericDepth = 0;
  let current = '';
  for (let i = 0; i < params.length; i += 1) {
    const ch = params[i];
    if (ch === '<') genericDepth += 1;
    else if (ch === '>') genericDepth -= 1;
    else if (ch === ',' && genericDepth === 0) {
      const name = current.trim().split(/\s+/).pop();
      if (name) components.push(name);
      current = '';
      continue;
    }
    current += ch;
  }
  const name = current.trim().split(/\s+/).pop();
  if (name) components.push(name);
  return components;
}

function extractTsInterfaceFields(tsSource, interfaceName) {
  const regex = new RegExp(
    `export interface ${interfaceName}(?:<[^>]+>)?\\s*\\{([\\s\\S]*?)\\n\\}`,
    'm'
  );
  const match = tsSource.match(regex);
  if (!match) return null;
  return [...match[1].matchAll(/^\s*([A-Za-z_][\w]*)\??:/gm)].map((m) => m[1]);
}

function extractJavaEnumValues(source) {
  const values = [];
  for (const line of source.split('\n')) {
    const trimmed = line.trim();
    if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) continue;
    const match = trimmed.match(/^([A-Z_]+),?$/);
    if (match) values.push(match[1]);
  }
  return values;
}

function extractTsConstArray(source, constName) {
  const regex = new RegExp(
    `export const ${constName}\\s*=\\s*\\[([\\s\\S]*?)\\] as const`,
    'm'
  );
  const match = source.match(regex);
  if (!match) return null;
  return [...match[1].matchAll(/'([A-Z_]+)'/g)].map((m) => m[1]);
}

function validateMirrorFields() {
  const apiJava = readText(join(backendContractDir, 'ApiResponse.java'));
  const pageJava = readText(join(backendContractDir, 'PageResponse.java'));
  const snapshotJava = readText(join(backendContractDir, 'StreamSnapshotEnvelope.java'));
  const roleJava = readText(join(backendContractDir, 'ConversationMessageRole.java'));
  const statusJava = readText(join(backendContractDir, 'ConversationMessageStatus.java'));
  const errorJava = readText(join(backendContractDir, 'MvpErrorCode.java'));

  const apiTs = readText(join(uiContractsDir, 'api.ts'));
  const snapshotTs = readText(join(uiContractsDir, 'snapshot.ts'));
  const messageTs = readText(join(uiContractsDir, 'message.ts'));
  const errorsTs = readText(join(uiContractsDir, 'errors.ts'));
  const agentTs = readText(join(uiContractsDir, 'agent.ts'));

  const mirrors = [
    ['ApiResponse', ['code', 'message', 'data'], extractJavaRecordComponents(apiJava, 'ApiResponse'), extractTsInterfaceFields(apiTs, 'ApiResponse')],
    ['PageResponse', ['items', 'page', 'pageSize', 'hasMore'], extractJavaRecordComponents(pageJava, 'PageResponse'), extractTsInterfaceFields(apiTs, 'PageResponse')],
    ['StreamSnapshotEnvelope', ['payloadVersion', 'truncated', 'events'], extractJavaRecordComponents(snapshotJava, 'StreamSnapshotEnvelope'), extractTsInterfaceFields(snapshotTs, 'StreamSnapshotEnvelope')],
    ['ConversationMessageRole', ['USER', 'ASSISTANT'], extractJavaEnumValues(roleJava), extractTsConstArray(messageTs, 'CONVERSATION_MESSAGE_ROLES')],
    ['ConversationMessageStatus', ['PENDING', 'STREAMING', 'COMPLETED', 'FAILED', 'INTERRUPTED'], extractJavaEnumValues(statusJava), extractTsConstArray(messageTs, 'CONVERSATION_MESSAGE_STATUSES')],
    ['MvpErrorCode', EXPECTED_ERROR_CODES, extractJavaEnumValues(errorJava), extractTsConstArray(errorsTs, 'MVP_ERROR_CODES')],
  ];

  for (const [name, expected, javaValues, tsValues] of mirrors) {
    if (JSON.stringify(javaValues) !== JSON.stringify(expected)) {
      fail(`${name} Java mirror mismatch: ${JSON.stringify(javaValues)}`);
    }
    if (JSON.stringify(tsValues) !== JSON.stringify(expected)) {
      fail(`${name} TypeScript mirror mismatch: ${JSON.stringify(tsValues)}`);
    }
  }

  if (!apiTs.includes('data: T | null')) {
    fail('ApiResponse TypeScript data must include null');
  }
  if (pageJava.includes('total') || apiTs.includes('total:')) {
    fail('PageResponse must not contain total');
  }

  const queryBody = agentTs.match(/export interface QueryAgentStreamRequest\s*\{([\s\S]*?)\}/)?.[1] ?? '';
  for (const forbidden of ['tenantId', 'ownerId', 'userId', 'user', 'traceId', 'history', 'historyMessages', 'internalToken']) {
    if (new RegExp(`\\b${forbidden}\\??:`).test(queryBody)) {
      fail(`QueryAgentStreamRequest must not contain ${forbidden}`);
    }
  }

  pass('Java/TS mirror extraction validated');
}

function parseConfigTable() {
  const configDoc = readText(join(contractDir, 'configuration.md'));
  const rows = [...configDoc.matchAll(/^\| (GENIE_[A-Z_]+|MVP_[A-Z_]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$/gm)];
  return rows.map((row) => ({
    name: row[1].trim(),
    profiles: row[5].split(',').map((item) => item.trim()).filter(Boolean),
  }));
}

function validateConfigNames() {
  const rows = parseConfigTable();
  const found = rows.map((row) => row.name);
  if (new Set(found).size !== found.length) fail('Duplicate configuration names in configuration.md');
  if (JSON.stringify(found) !== JSON.stringify(EXPECTED_CONFIG_VARS)) {
    fail(`Configuration names mismatch: ${JSON.stringify(found)}`);
  } else {
    pass('Configuration names match expected set and order');
  }

  for (const row of rows) {
    if (row.name === 'MVP_ACCEPTANCE_USER_PASSWORD' || row.name === 'MVP_ACCEPTANCE_ADMIN_PASSWORD') {
      if (JSON.stringify(row.profiles) !== JSON.stringify(['mvp-acceptance'])) {
        fail(`${row.name} profiles must be mvp-acceptance only`);
      }
    }
    if (row.name === 'GENIE_BOOTSTRAP_ADMIN_USERNAME' || row.name === 'GENIE_BOOTSTRAP_ADMIN_PASSWORD') {
      for (const profile of ['local', 'test', 'prod', 'mvp-acceptance']) {
        if (!row.profiles.includes(profile)) {
          fail(`${row.name} must allow profile ${profile}`);
        }
      }
    }
  }
  pass('Configuration profile rules validated');
}

function validateErrorCodes() {
  const javaCodes = extractJavaEnumValues(readText(join(backendContractDir, 'MvpErrorCode.java')));
  const tsCodes = extractTsConstArray(readText(join(uiContractsDir, 'errors.ts')), 'MVP_ERROR_CODES');
  if (JSON.stringify(javaCodes) !== JSON.stringify(EXPECTED_ERROR_CODES)) {
    fail(`Java MvpErrorCode mismatch: ${JSON.stringify(javaCodes)}`);
  } else {
    pass('Java MvpErrorCode matches expected set and order');
  }
  if (JSON.stringify(tsCodes) !== JSON.stringify(EXPECTED_ERROR_CODES)) {
    fail(`TypeScript MVP_ERROR_CODES mismatch: ${JSON.stringify(tsCodes)}`);
  } else {
    pass('TypeScript MVP_ERROR_CODES matches expected set and order');
  }
}

function looksLikeOwnershipPath(line) {
  const trimmed = line.trim();
  if (!trimmed) return false;
  if (/^仅允许|^禁止|^不得|^新 Mapper|^注意/.test(trimmed)) return false;
  return (
    trimmed.includes('/') ||
    trimmed.includes('*') ||
    /\.(java|yml|yaml|sql|sh|tsx?|jsx?|json|md)$/i.test(trimmed)
  );
}

function normalizeOwnershipPath(path) {
  return path
    .trim()
    .replace(/（[^）]*除外[^）]*）/g, '')
    .replace(/（[^）]*冻结除外[^）]*）/g, '')
    .replace(/（.*?）/g, '')
    .replace(/\(.*?\)/g, '')
    .replace(/\\/g, '/')
    .replace(/^\.\//, '')
    .replace(/\s+$/g, '');
}

function extractOwnershipBlocks(source) {
  const sectionRegex = /(冻结|A 独占|B 独占|C 独占|D 独占)：\s*```text\s*([\s\S]*?)```/g;
  const result = { frozen: [], A: [], B: [], C: [], D: [] };
  let match;
  while ((match = sectionRegex.exec(source)) !== null) {
    const label = match[1] === '冻结' ? 'frozen' : match[1].charAt(0);
    result[label] = match[2]
      .split('\n')
      .filter(looksLikeOwnershipPath)
      .map((line) => normalizeOwnershipPath(line))
      .filter(Boolean);
  }
  return result;
}

function stripGlob(path) {
  return path.replace(/\/\*\*$/, '/').replace(/\*\*/g, '').replace(/\*$/, '');
}

function pathsConflict(a, b) {
  if (a === b) return true;
  const baseA = a.split('/').pop();
  const baseB = b.split('/').pop();
  if (baseA && baseB && !baseA.includes('*') && !baseB.includes('*') && baseA === baseB && baseA.includes('.')) {
    return true;
  }
  const left = stripGlob(a);
  const right = stripGlob(b);
  if (!left || !right) return false;
  return left === right || left.startsWith(right) || right.startsWith(left);
}

function validateOwnershipFromBaseline() {
  const baseline = readText(baselineDoc);
  const ownership = extractOwnershipBlocks(baseline);

  if (
    ownership.frozen.length === 0 ||
    ownership.A.length === 0 ||
    ownership.B.length === 0 ||
    ownership.C.length === 0 ||
    ownership.D.length === 0
  ) {
    fail('Failed to parse ownership blocks from MVP-CONTRACT-002 section 16');
  }

  const moduleOwners = { A: ownership.A, B: ownership.B, C: ownership.C, D: ownership.D };
  const entries = [];
  for (const [module, paths] of Object.entries(moduleOwners)) {
    for (const path of paths) entries.push({ module, path });
  }

  for (let i = 0; i < entries.length; i += 1) {
    for (let j = i + 1; j < entries.length; j += 1) {
      if (entries[i].module === entries[j].module) continue;
      if (pathsConflict(entries[i].path, entries[j].path)) {
        fail(
          `Ownership conflict between ${entries[i].module} and ${entries[j].module} on ${entries[i].path} vs ${entries[j].path}`
        );
      }
    }
  }

  for (const frozenPath of ownership.frozen) {
    for (const [module, paths] of Object.entries(moduleOwners)) {
      if (paths.some((path) => pathsConflict(path, frozenPath) && path !== 'ui/**')) {
        fail(`Frozen path ${frozenPath} must not appear in ${module} exclusive ownership`);
      }
    }
  }

  const moduleDocOwners = {
    A: '01_数据库与身份安全模块_开发验收方案.md',
    B: '02_会话与消息后端模块_开发验收方案.md',
    C: '03_Agent流式持久化与上下文桥接模块_开发验收方案.md',
    D: '04_前端整合与全链路验收模块_开发验收方案.md',
  };

  for (const [module, doc] of Object.entries(moduleDocOwners)) {
    const content = readText(join(phase1DocsDir, doc));
    if (!content.includes('MVP-CONTRACT-002')) fail(`${doc} does not reference MVP-CONTRACT-002`);
    else pass(`${doc} references MVP-CONTRACT-002`);

    const claimed = extractOwnershipBlocks(content)[module] || [];
    for (const path of claimed) {
      for (const frozenPath of ownership.frozen) {
        if (pathsConflict(path, frozenPath) && path !== 'ui/**') {
          fail(`${doc} claims frozen path ${path} overlapping ${frozenPath}`);
        }
      }
      for (const [otherModule, otherPaths] of Object.entries(moduleOwners)) {
        if (otherModule === module) continue;
        for (const otherPath of otherPaths) {
          if (pathsConflict(path, otherPath)) {
            fail(`${doc} claims other module ${otherModule} path ${path} overlapping ${otherPath}`);
          }
        }
      }
    }
  }

  pass('Ownership parsed from MVP-CONTRACT-002 section 16 without conflicts');
}

function collectFiles(dir, predicate) {
  if (!existsSync(dir)) return [];
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...collectFiles(full, predicate));
    else if (predicate(entry.name, full)) out.push(full);
  }
  return out;
}

function assertNoSecretFields(value, label, seen = new WeakSet()) {
  if (value === null || typeof value !== 'object') {
    if (typeof value === 'string') {
      for (const pattern of SECRET_VALUE_PATTERNS) {
        if (pattern.test(value)) fail(`${label}: contains forbidden secret value pattern ${pattern}`);
      }
    }
    return;
  }
  if (seen.has(value)) return;
  seen.add(value);
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoSecretFields(item, `${label}[${index}]`, seen));
    return;
  }
  for (const [key, child] of Object.entries(value)) {
    if (PHASE2_SECRET_FIELD_NAMES.has(key.toLowerCase())) {
      fail(`${label}: forbidden secret field name ${key}`);
    }
    assertNoSecretFields(child, `${label}.${key}`, seen);
  }
}

function extractJavaEnumValuesFromText(source) {
  const body = source.match(/enum\s+\w+\s*\{([\s\S]*?)\}/)?.[1] ?? '';
  return [...body.matchAll(/\b([A-Z][A-Z0-9_]*)\b/g)].map((m) => m[1]);
}

function validatePhase2RequiredFixtures() {
  for (const name of PHASE2_REQUIRED_FIXTURES) {
    const filePath = join(phase2FixturesDir, name);
    if (!existsSync(filePath)) {
      fail(`Missing required Phase2 fixture ${name}`);
    } else {
      pass(`Required Phase2 fixture present: ${name}`);
    }
  }
}

function validatePhase2ErrorFixtures() {
  for (const [name, expectedCode] of Object.entries(PHASE2_ERROR_FIXTURES)) {
    const filePath = join(phase2FixturesDir, name);
    let data;
    try {
      data = JSON.parse(readText(filePath));
    } catch (error) {
      fail(`${name} is not valid JSON: ${error.message}`);
      continue;
    }
    if (data.code !== expectedCode) {
      fail(`${name}: code must be ${expectedCode}, got ${data.code}`);
    }
    if (typeof data.message !== 'string' || !data.message.trim()) {
      fail(`${name}: message must be a non-empty string`);
    }
    if (data.data !== null) {
      fail(`${name}: data must be null`);
    }
    assertNoSecretFields(data, name);
    pass(`${name}: error fixture shape ok`);
  }
}

function validatePhase2MemoryPatchFixtures(memoryValidate) {
  if (!memoryValidate) {
    fail('memory-patch schema validator unavailable');
    return;
  }

  const valid = JSON.parse(readText(join(phase2FixturesDir, 'memory-patch.json')));
  if (!isPlainObject(valid.data) || !memoryValidate(valid.data)) {
    fail(`memory-patch.json must pass memory-patch schema: ${JSON.stringify(memoryValidate.errors)}`);
  } else {
    pass('memory-patch.json passes memory-patch schema');
  }

  const invalid = JSON.parse(readText(join(phase2FixturesDir, 'memory-patch-invalid.json')));
  if (!isPlainObject(invalid.data)) {
    fail('memory-patch-invalid.json must contain object data for negative schema test');
  } else if (memoryValidate(invalid.data)) {
    fail('memory-patch-invalid.json must fail memory-patch schema');
  } else {
    pass('memory-patch-invalid.json rejected by memory-patch schema');
  }
}

function validatePhase2SchemaPropertyMirrors() {
  for (const mirror of PHASE2_SCHEMA_DTO_MIRRORS) {
    const schemaPath = join(phase2SchemaDir, mirror.schemaFile);
    const schema = JSON.parse(readText(schemaPath));
    if ('schemaVersion' in (schema.properties ?? {})) {
      fail(`${mirror.schemaFile} must not define schemaVersion`);
      continue;
    }
    const schemaProps = Object.keys(schema.properties ?? {}).sort();
    const javaSource = readText(join(phase2BackendDir, mirror.javaPath));
    const tsSource = readText(join(phase2TsDir, mirror.tsFile));
    const javaFields = extractJavaRecordComponents(javaSource, mirror.javaRecord)?.sort() ?? [];
    const tsFields = extractTsInterfaceFields(tsSource, mirror.tsInterface)?.sort() ?? [];
    if (JSON.stringify(schemaProps) !== JSON.stringify(javaFields)) {
      fail(
        `${mirror.schemaFile} properties mismatch Java ${mirror.javaRecord}: schema=${JSON.stringify(schemaProps)} java=${JSON.stringify(javaFields)}`
      );
    } else if (JSON.stringify(schemaProps) !== JSON.stringify(tsFields)) {
      fail(
        `${mirror.schemaFile} properties mismatch TS ${mirror.tsInterface}: schema=${JSON.stringify(schemaProps)} ts=${JSON.stringify(tsFields)}`
      );
    } else {
      pass(`${mirror.schemaFile} properties mirror Java/TS ${mirror.javaRecord}`);
    }
  }
}

function validatePhase2OrchestrationSequence(events, label) {
  const orchestrationEvents = events
    .map((event) => event?.resultMap?.orchestrationEvent)
    .filter(isPlainObject);
  if (orchestrationEvents.length === 0) {
    return;
  }

  let previousSequence = 0;
  let expectedRequestId = null;
  let expectedRunId = null;

  for (const [index, orchestrationEvent] of orchestrationEvents.entries()) {
    const eventLabel = `${label} orchestrationEvent[${index}]`;
    if (expectedRequestId === null) {
      expectedRequestId = orchestrationEvent.requestId;
      expectedRunId = orchestrationEvent.runId;
    } else {
      if (orchestrationEvent.requestId !== expectedRequestId) {
        fail(`${eventLabel}: requestId must remain ${expectedRequestId}, got ${orchestrationEvent.requestId}`);
      }
      if (orchestrationEvent.runId !== expectedRunId) {
        fail(`${eventLabel}: runId must remain ${expectedRunId}, got ${orchestrationEvent.runId}`);
      }
    }
    if (typeof orchestrationEvent.sequence !== 'number' || orchestrationEvent.sequence <= previousSequence) {
      fail(`${eventLabel}: sequence must strictly increase, got ${orchestrationEvent.sequence} after ${previousSequence}`);
    }
    const expectedEventId = `${orchestrationEvent.requestId}:${orchestrationEvent.sequence}`;
    if (orchestrationEvent.eventId !== expectedEventId) {
      fail(`${eventLabel}: eventId must be ${expectedEventId}, got ${orchestrationEvent.eventId}`);
    }
    previousSequence = orchestrationEvent.sequence;
  }
  pass(`${label}: orchestration sequence consistency ok`);
}

function validatePhase2ProtectedBaselineContent() {
  const content = readText(phase2ProtectedBaseline);
  if (content.includes('\r')) {
    fail('Phase2 protected baseline must not contain CR line endings');
    return;
  }
  const lines = content.split('\n').filter((line) => line.trim().length > 0);
  if (lines.length !== 18) {
    fail(`Phase2 protected baseline must contain exactly 18 entries, found ${lines.length}`);
  }
  const paths = [];
  for (const line of lines) {
    const match = line.match(/^([a-f0-9A-F]{64}) {2}(.+)$/);
    if (!match) {
      fail(`Phase2 protected baseline invalid line format: ${line}`);
      continue;
    }
    paths.push(match[2]);
  }
  if (new Set(paths).size !== paths.length) {
    fail('Phase2 protected baseline contains duplicate paths');
  } else {
    pass('Phase2 protected baseline content validated (18 entries, LF, unique paths)');
  }
}

function validatePhase2SchemasAndFixtures(ajv) {
  if (!existsSync(phase2SchemaDir)) {
    fail('Phase2 schema directory missing');
    return;
  }
  const validators = {};
  for (const name of PHASE2_SCHEMA_FILES) {
    const schemaPath = join(phase2SchemaDir, name);
    if (!existsSync(schemaPath)) {
      fail(`Missing Phase2 schema ${name}`);
      continue;
    }
    try {
      validators[name] = ajv.compile(JSON.parse(readText(schemaPath)));
      pass(`Phase2 schema compiled: ${name}`);
    } catch (error) {
      fail(`Phase2 schema compile failed ${name}: ${error.message}`);
    }
  }

  validatePhase2RequiredFixtures();

  const jsonFixtures = collectFiles(phase2FixturesDir, (name) => name.endsWith('.json'));
  const ndjsonFixtures = collectFiles(phase2FixturesDir, (name) => name.endsWith('.ndjson'));
  const orchestrationValidate = validators['orchestration-event-v1.schema.json'];
  const memoryValidate = validators['memory-patch-v1.schema.json'];
  const managementValidate = validators['management-api-v1.schema.json'];
  const snapshotValidate = ajv.compile(loadSchema());

  validatePhase2MemoryPatchFixtures(memoryValidate);
  validatePhase2ErrorFixtures();

  for (const filePath of jsonFixtures) {
    const name = filePath.split(/[\\/]/).pop();
    let data;
    try {
      data = JSON.parse(readText(filePath));
    } catch (error) {
      fail(`Phase2 fixture ${name} is not valid JSON: ${error.message}`);
      continue;
    }
    assertNoSecretFields(data, name);

    if (name.startsWith('snapshot-') && name.endsWith('.json')) {
      if (!snapshotValidate(data)) {
        fail(`${name} failed snapshot schema: ${JSON.stringify(snapshotValidate.errors)}`);
      } else if (data.payloadVersion !== 1) {
        fail(`${name}: payloadVersion must remain 1`);
      } else {
        pass(`${name}: snapshot payloadVersion=1`);
      }
      if (Array.isArray(data.events)) {
        data.events.forEach((event, index) => {
          validateGptProcessResultEvent(event, `${name} events[${index}]`);
          validatePhase2StreamEvent(event, `${name} events[${index}]`, orchestrationValidate);
        });
        validatePhase2OrchestrationSequence(data.events, name);
      }
      continue;
    }

    if (name.startsWith('memory-patch')) {
      continue;
    }

    if (PHASE2_MANAGEMENT_FIXTURES.includes(name)) {
      if (!managementValidate(data)) {
        fail(`${name} failed management-api schema: ${JSON.stringify(managementValidate.errors)}`);
      } else {
        pass(`${name}: management-api schema ok`);
      }
    }
  }

  for (const filePath of ndjsonFixtures) {
    const name = filePath.split(/[\\/]/).pop();
    const lines = readText(filePath).split(/\r?\n/).filter((line) => line.trim().length > 0);
    const parsedEvents = [];
    lines.forEach((line, index) => {
      let event;
      try {
        event = JSON.parse(line);
      } catch (error) {
        fail(`${name}:${index + 1} invalid JSON: ${error.message}`);
        return;
      }
      parsedEvents.push(event);
      validateGptProcessResultEvent(event, `${name}:${index + 1}`);
      validatePhase2StreamEvent(event, `${name}:${index + 1}`, orchestrationValidate);
      assertNoSecretFields(event, `${name}:${index + 1}`);
    });
    validatePhase2OrchestrationSequence(parsedEvents, name);
    pass(`${name}: ndjson fixture validated`);
  }

  const malformed = join(phase2FixturesDir, 'snapshot-orchestrated-malformed.txt');
  if (existsSync(malformed)) {
    try {
      JSON.parse(readText(malformed));
      fail('snapshot-orchestrated-malformed.txt should not parse');
    } catch {
      pass('snapshot-orchestrated-malformed.txt cannot be parsed');
    }
  }
}

function validatePhase2StreamEvent(event, label, orchestrationValidate) {
  const orchestrationEvent = event?.resultMap?.orchestrationEvent;
  if (!isPlainObject(orchestrationEvent)) {
    return;
  }
  if (orchestrationValidate && !orchestrationValidate(orchestrationEvent)) {
    fail(`${label}: orchestrationEvent schema failed ${JSON.stringify(orchestrationValidate.errors)}`);
  }
  const eventType = orchestrationEvent.eventType;
  if (PHASE2_PROGRESS_EVENT_TYPES.has(eventType)) {
    if (event.packageType !== 'orchestration') {
      fail(`${label}: progress event ${eventType} packageType must be orchestration`);
    }
    if (event.responseType !== 'json') {
      fail(`${label}: progress event ${eventType} responseType must be json`);
    }
    if (event.finished === true) {
      fail(`${label}: progress event ${eventType} must have finished=false`);
    }
  }
  if (eventType === 'FINAL_RESPONSE') {
    if (event.packageType !== 'result') fail(`${label}: FINAL_RESPONSE packageType must be result`);
    if (event.responseType !== 'markdown') fail(`${label}: FINAL_RESPONSE responseType must be markdown`);
    if (event.finished !== true) fail(`${label}: FINAL_RESPONSE must be finished=true`);
    if (event.status !== 'success') fail(`${label}: FINAL_RESPONSE status must be success`);
    if (!event.response || !String(event.response).trim()) {
      fail(`${label}: FINAL_RESPONSE response must be non-empty`);
    }
    if (event.response !== event.responseAll) {
      fail(`${label}: FINAL_RESPONSE response must equal responseAll`);
    }
  }
}

function validatePhase2EnumMirrors() {
  const pairs = [
    ['ExecutionMode', 'EXECUTION_MODES', 'enums/ExecutionMode.java'],
    ['OrchestrationRoute', 'ORCHESTRATION_ROUTES', 'enums/OrchestrationRoute.java'],
    ['OrchestrationEventType', 'ORCHESTRATION_EVENT_TYPES', 'enums/OrchestrationEventType.java'],
    ['AgentTaskErrorCode', 'AGENT_TASK_ERROR_CODES', 'enums/AgentTaskErrorCode.java'],
    ['OrchestrationCompletionStatus', 'ORCHESTRATION_COMPLETION_STATUSES', 'enums/OrchestrationCompletionStatus.java'],
  ];
  for (const [name, tsConst, javaRel] of pairs) {
    const javaValues = extractJavaEnumValuesFromText(readText(join(phase2BackendDir, javaRel)));
    const tsFile = name.includes('Memory') ? 'memory.ts'
      : name.startsWith('Execution') ? 'runtime.ts'
        : 'orchestration.ts';
    const tsValues = extractTsConstArray(readText(join(phase2TsDir, tsFile)), tsConst);
    if (JSON.stringify(javaValues) !== JSON.stringify(tsValues)) {
      fail(`${name} Java/TS mismatch: java=${JSON.stringify(javaValues)} ts=${JSON.stringify(tsValues)}`);
    } else {
      pass(`${name} Java/TS enum mirror ok`);
    }
  }
}

function validatePhase2UniqueTypes() {
  const uniqueNames = [
    'AgentRuntimeCatalogPort',
    'ToolBindingPort',
    'RuntimeToolCollectionPort',
    'AgentRuntimeProfile',
    'ToolBindingView',
    'OrchestrationEvent',
  ];
  for (const typeName of uniqueNames) {
    const matches = collectFiles(join(repoRoot, 'genie-backend/src/main/java'), (name) => name === `${typeName}.java`);
    if (matches.length !== 1) {
      fail(`${typeName} must have exactly one main definition, found ${matches.length}`);
    } else {
      pass(`${typeName} unique definition ok`);
    }
  }
}

function validatePhase2ProtectedBaselineExists() {
  if (!existsSync(phase2ProtectedBaseline)) {
    fail('docs/mvp-contract/phase2/protected-baseline.sha256 missing');
  } else {
    pass('Phase2 protected baseline file exists');
    validatePhase2ProtectedBaselineContent();
  }
}

function validatePhase2Contract(ajv) {
  console.log('\nValidating MVP-CONTRACT-004 Phase2 seams...\n');
  validatePhase2SchemasAndFixtures(ajv);
  validatePhase2SchemaPropertyMirrors();
  validatePhase2EnumMirrors();
  validatePhase2UniqueTypes();
  validatePhase2ProtectedBaselineExists();
}

function main() {
  console.log('Validating MVP-CONTRACT-002 foundation...\n');
  const ajv = new Ajv({ allErrors: true, strict: false });

  validateChatExports();
  validateSnapshots(ajv);
  validateSseFixtures();
  validateMswFixtures();
  validateErrorCodes();
  validateConfigNames();
  validateMirrorFields();
  validateOwnershipFromBaseline();
  validatePhase2Contract(ajv);

  console.log('');
  if (failures > 0) {
    console.error(`Contract validation failed with ${failures} error(s).`);
    process.exit(1);
  }
  console.log('Contract validation passed.');
}

main();
