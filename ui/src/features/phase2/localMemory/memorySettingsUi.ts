import type { ConversationSummaryDoc, LongTermSectionName } from './types';

export const LONG_TERM_SECTION_UI: Record<
  LongTermSectionName,
  {
    title: string;
    hint: string;
    keyPlaceholder: string;
    valuePlaceholder: string;
  }
> = {
  基本信息: {
    title: '关于你',
    hint: '你明确说过的姓名、年龄等信息',
    keyPlaceholder: '名称，例如 姓名',
    valuePlaceholder: '内容，例如 李四',
  },
  回答偏好: {
    title: '回答偏好',
    hint: '希望我怎样回答',
    keyPlaceholder: '名称，例如 语气',
    valuePlaceholder: '内容，例如 简洁、幽默',
  },
  长期目标: {
    title: '长期目标',
    hint: '你持续在做的事',
    keyPlaceholder: '名称，例如 学习',
    valuePlaceholder: '内容，例如 把英语口语练起来',
  },
  长期约束: {
    title: '长期约束',
    hint: '需要避开或不要做的事',
    keyPlaceholder: '名称，例如 不要',
    valuePlaceholder: '内容，例如 不要主动提起薪资',
  },
};

const DOCKER_MEMORY_DIR = '/var/lib/joyagent/memory';

export function formatHostMemoryPath(diskRootPath: string | null): string {
  if (!diskRootPath) {
    return '尚未创建数据目录';
  }
  const normalized = diskRootPath.replace(/\\/g, '/').replace(/\/+$/, '');
  if (
    normalized === DOCKER_MEMORY_DIR ||
    normalized.endsWith(DOCKER_MEMORY_DIR)
  ) {
    return '项目目录 / data / memory';
  }
  return diskRootPath;
}

export function formatRelativeUpdate(iso: string, now = new Date()): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '最近更新';
  }
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startOfThat = new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
  );
  const diffDays = Math.round(
    (startOfToday.getTime() - startOfThat.getTime()) / 86_400_000,
  );
  if (diffDays <= 0) {
    return '今天更新';
  }
  if (diffDays === 1) {
    return '昨天更新';
  }
  if (diffDays < 7) {
    return `${diffDays}天前更新`;
  }
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日更新`;
}

export function conversationDisplayTitle(title: string | null | undefined): string {
  const trimmed = title?.trim();
  return trimmed ? trimmed : '未命名对话';
}

export function summaryPreview(doc: ConversationSummaryDoc): string {
  const goal = doc.sections.当前目标.trim();
  const facts = doc.sections.已确认事实.trim();
  const parts = [goal, facts].filter((part) => part.length > 0);
  if (parts.length === 0) {
    return '暂无摘要内容';
  }
  const text = parts.join(' · ');
  return [...text].length > 72 ? `${[...text].slice(0, 72).join('')}…` : text;
}
