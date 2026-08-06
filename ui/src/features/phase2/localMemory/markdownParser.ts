import {
  LONG_TERM_SECTION_NAMES,
  MEMORY_LIMITS,
  SUMMARY_SECTION_NAMES,
  codePointLength,
  type ConversationSummaryDoc,
  type LongTermMemoryDoc,
  type LongTermMemoryEntry,
  type LongTermSectionName,
  type SummarySectionName,
} from './types';

function hasControlChars(text: string): boolean {
  for (let i = 0; i < text.length; i += 1) {
    const code = text.charCodeAt(i);
    if (
      code === 0x7f ||
      (code <= 0x1f && code !== 0x09 && code !== 0x0a && code !== 0x0d)
    ) {
      return true;
    }
  }
  return false;
}

export type ParseSuccess<T> = { ok: true; doc: T };
export type ParseFailure = { ok: false; status: 'CORRUPTED'; reason: string };
export type ParseResult<T> = ParseSuccess<T> | ParseFailure;

function fail(reason: string): ParseFailure {
  return {
    ok: false,
    status: 'CORRUPTED',
    reason
  };
}

function splitFrontMatter(raw: string): {
  meta: Record<string, string>;
  body: string;
} | null {
  const normalized = raw.replace(/^\uFEFF/, '');
  if (!normalized.startsWith('---\n') && !normalized.startsWith('---\r\n')) {
    return null;
  }
  const end = normalized.indexOf('\n---', 3);
  if (end < 0) {
    return null;
  }
  const fmBlock = normalized.slice(4, end).replace(/\r/g, '');
  let bodyStart = end + '\n---'.length;
  if (normalized[bodyStart] === '\r') {
    bodyStart += 1;
  }
  if (normalized[bodyStart] === '\n') {
    bodyStart += 1;
  }
  const meta: Record<string, string> = {};
  for (const line of fmBlock.split('\n')) {
    if (line.trim() === '') {
      continue;
    }
    const idx = line.indexOf(':');
    if (idx <= 0) {
      return null;
    }
    const key = line.slice(0, idx).trim();
    const value = line.slice(idx + 1).trim();
    meta[key] = value;
  }
  return {
    meta,
    body: normalized.slice(bodyStart).replace(/\r/g, '')
  };
}

function parseSections(body: string): Map<string, string> | null {
  const lines = body.split('\n');
  const sections = new Map<string, string[]>();
  let current: string | null = null;

  for (const line of lines) {
    const heading = /^## (.+)$/.exec(line);
    if (heading) {
      current = heading[1].trim();
      if (!sections.has(current)) {
        sections.set(current, []);
      }
      continue;
    }
    if (current == null) {
      if (line.trim() === '') {
        continue;
      }
      return null;
    }
    sections.get(current)!.push(line);
  }

  const result = new Map<string, string>();
  for (const [name, contentLines] of sections) {
    // Trim a single trailing empty line group for stability.
    let end = contentLines.length;
    while (end > 0 && contentLines[end - 1] === '') {
      end -= 1;
    }
    let start = 0;
    while (start < end && contentLines[start] === '') {
      start += 1;
    }
    result.set(name, contentLines.slice(start, end).join('\n'));
  }
  return result;
}

function parseLongTermEntries(sectionBody: string): LongTermMemoryEntry[] | null {
  if (sectionBody.trim() === '') {
    return [];
  }
  const entries: LongTermMemoryEntry[] = [];
  const seen = new Set<string>();
  for (const line of sectionBody.split('\n')) {
    if (line.trim() === '') {
      continue;
    }
    const match = /^- (.+)$/.exec(line);
    if (!match) {
      return null;
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(match[1]);
    } catch {
      return null;
    }
    if (
      typeof parsed !== 'object' ||
      parsed === null ||
      Array.isArray(parsed) ||
      typeof (parsed as { key?: unknown }).key !== 'string' ||
      typeof (parsed as { value?: unknown }).value !== 'string'
    ) {
      return null;
    }
    const key = (parsed as { key: string }).key;
    const value = (parsed as { value: string }).value;
    if (seen.has(key)) {
      return null;
    }
    seen.add(key);
    entries.push({
      key,
      value
    });
  }
  return entries;
}

export function parseLongTermMemory(raw: string): ParseResult<LongTermMemoryDoc> {
  if (hasControlChars(raw)) {
    return fail('control characters');
  }
  if (codePointLength(raw) > MEMORY_LIMITS.LTM_MAX_CODEPOINTS) {
    return fail('long-term memory too large');
  }

  const split = splitFrontMatter(raw);
  if (!split) {
    return fail('missing front matter');
  }
  const { meta, body } = split;
  if (meta.schemaVersion !== '1') {
    return fail('invalid schemaVersion');
  }
  if (!meta.updatedAt || Number.isNaN(Date.parse(meta.updatedAt))) {
    return fail('invalid updatedAt');
  }

  const sectionsMap = parseSections(body);
  if (!sectionsMap) {
    return fail('invalid sections layout');
  }
  if (sectionsMap.size !== LONG_TERM_SECTION_NAMES.length) {
    return fail('section count mismatch');
  }
  for (const name of LONG_TERM_SECTION_NAMES) {
    if (!sectionsMap.has(name)) {
      return fail(`missing section ${name}`);
    }
  }
  for (const name of sectionsMap.keys()) {
    if (!(LONG_TERM_SECTION_NAMES as readonly string[]).includes(name)) {
      return fail(`unexpected section ${name}`);
    }
  }

  const sections = {} as LongTermMemoryDoc['sections'];
  for (const name of LONG_TERM_SECTION_NAMES) {
    const entries = parseLongTermEntries(sectionsMap.get(name) ?? '');
    if (!entries) {
      return fail(`invalid entries in ${name}`);
    }
    sections[name as LongTermSectionName] = entries;
  }

  return {
    ok: true,
    doc: {
      schemaVersion: 1,
      updatedAt: meta.updatedAt,
      sections,
    },
  };
}

export function parseConversationSummary(
  raw: string,
): ParseResult<ConversationSummaryDoc> {
  if (hasControlChars(raw)) {
    return fail('control characters');
  }
  if (codePointLength(raw) > MEMORY_LIMITS.SUMMARY_MAX_CODEPOINTS) {
    return fail('summary too large');
  }

  const split = splitFrontMatter(raw);
  if (!split) {
    return fail('missing front matter');
  }
  const { meta, body } = split;
  if (meta.schemaVersion !== '1') {
    return fail('invalid schemaVersion');
  }
  if (!meta.conversationId) {
    return fail('missing conversationId');
  }
  const turnNo = Number(meta.lastSummarizedTurnNo);
  if (!Number.isInteger(turnNo) || turnNo < 0) {
    return fail('invalid lastSummarizedTurnNo');
  }
  if (!meta.updatedAt || Number.isNaN(Date.parse(meta.updatedAt))) {
    return fail('invalid updatedAt');
  }

  const sectionsMap = parseSections(body);
  if (!sectionsMap) {
    return fail('invalid sections layout');
  }
  if (sectionsMap.size !== SUMMARY_SECTION_NAMES.length) {
    return fail('section count mismatch');
  }
  for (const name of SUMMARY_SECTION_NAMES) {
    if (!sectionsMap.has(name)) {
      return fail(`missing section ${name}`);
    }
  }
  for (const name of sectionsMap.keys()) {
    if (!(SUMMARY_SECTION_NAMES as readonly string[]).includes(name)) {
      return fail(`unexpected section ${name}`);
    }
  }

  const sections = {} as ConversationSummaryDoc['sections'];
  for (const name of SUMMARY_SECTION_NAMES) {
    const text = sectionsMap.get(name) ?? '';
    if (codePointLength(text) > MEMORY_LIMITS.SUMMARY_SECTION_MAX_CODEPOINTS) {
      return fail(`section too large: ${name}`);
    }
    sections[name as SummarySectionName] = text;
  }

  return {
    ok: true,
    doc: {
      schemaVersion: 1,
      conversationId: meta.conversationId,
      lastSummarizedTurnNo: turnNo,
      updatedAt: meta.updatedAt,
      sections,
    },
  };
}

/** Parse summary markdown body returned by summarize API (no front matter). */
export function parseSummarySectionsFromMarkdown(
  markdown: string,
): ParseResult<ConversationSummaryDoc['sections']> {
  if (hasControlChars(markdown)) {
    return fail('control characters');
  }
  const sectionsMap = parseSections(markdown.replace(/\r/g, ''));
  if (!sectionsMap) {
    return fail('invalid sections layout');
  }
  if (sectionsMap.size !== SUMMARY_SECTION_NAMES.length) {
    return fail('section count mismatch');
  }
  for (const name of SUMMARY_SECTION_NAMES) {
    if (!sectionsMap.has(name)) {
      return fail(`missing section ${name}`);
    }
  }
  for (const name of sectionsMap.keys()) {
    if (!(SUMMARY_SECTION_NAMES as readonly string[]).includes(name)) {
      return fail(`unexpected section ${name}`);
    }
  }
  const sections = {} as ConversationSummaryDoc['sections'];
  for (const name of SUMMARY_SECTION_NAMES) {
    const text = sectionsMap.get(name) ?? '';
    if (codePointLength(text) > MEMORY_LIMITS.SUMMARY_SECTION_MAX_CODEPOINTS) {
      return fail(`section too large: ${name}`);
    }
    sections[name as SummarySectionName] = text;
  }
  return {
    ok: true,
    doc: sections
  };
}
