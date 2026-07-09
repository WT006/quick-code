export const STATUS_PREFIX = '[状态] '

const WORKFLOW_STEP_PATTERN = /^(HTML代码生成中|CSS代码生成中|JavaScript代码生成中|代码生成中|代码文件已生成)$/
const TOOL_LINE_PATTERN = /^(\[选择工具\] |STEP \d+：|正在读取目录：|正在读取：|正在修改：|正在删除：|正在操作：)/
const TOOL_WRITE_PATTERN = /^STEP \d+：/
const TOOL_READ_PATTERN = /^(正在读取目录：|正在读取：|\[选择工具\] 读取)/
const TOOL_MODIFY_PATTERN = /^(正在修改：|\[选择工具\] 修改文件)/
const TOOL_DELETE_PATTERN = /^(正在删除：|\[选择工具\] 删除文件)/

export interface MessageSegment {
  type: 'text' | 'tool' | 'status'
  value: string
}

export interface ParsedChatMessage {
  statuses: string[]
  segments: MessageSegment[]
  /** 兼容字段：所有 text 段合并 */
  content: string
  /** 兼容字段：所有 tool 段 */
  tools: string[]
}

/**
 * 解析入库的对话文本，按时间顺序还原：状态 → 开头文字 → 工具步骤 → 结尾状态/文字。
 */
export function parseChatHistoryMessage(raw: string): ParsedChatMessage {
  const statuses: string[] = []
  const segments: MessageSegment[] = []
  const textBuffer: string[] = []

  const flushText = () => {
    if (textBuffer.length === 0) {
      return
    }
    segments.push({ type: 'text', value: textBuffer.join('\n') })
    textBuffer.length = 0
  }

  const pushStatus = (status: string) => {
    flushText()
    segments.push({ type: 'status', value: status })
    if (!statuses.includes(status)) {
      statuses.push(status)
    }
  }

  if (!raw?.trim()) {
    return { statuses, segments, content: '', tools: [] }
  }

  for (const line of raw.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed) {
      continue
    }
    if (trimmed.startsWith(STATUS_PREFIX)) {
      pushStatus(trimmed.slice(STATUS_PREFIX.length))
      continue
    }
    if (WORKFLOW_STEP_PATTERN.test(trimmed)) {
      pushStatus(trimmed)
      continue
    }
    if (TOOL_LINE_PATTERN.test(trimmed)) {
      flushText()
      segments.push({ type: 'tool', value: trimmed })
      continue
    }
    textBuffer.push(trimmed)
  }
  flushText()

  const tools = segments.filter((s) => s.type === 'tool').map((s) => s.value)
  const content = segments
    .filter((s) => s.type === 'text')
    .map((s) => s.value)
    .join('\n\n')

  return { statuses, segments, content, tools }
}

export function appendStatusSegment(
  segments: MessageSegment[] | undefined,
  chunk: string,
): MessageSegment[] {
  const list = segments ? [...segments] : []
  const trimmed = chunk?.trim()
  if (!trimmed) {
    return list
  }
  list.push({ type: 'status', value: trimmed })
  return list
}

export function appendContentSegment(
  segments: MessageSegment[] | undefined,
  chunk: string,
): MessageSegment[] {
  const list = segments ? [...segments] : []
  if (!chunk) {
    return list
  }
  const last = list[list.length - 1]
  if (last?.type === 'text') {
    last.value += chunk
  } else {
    list.push({ type: 'text', value: chunk })
  }
  return list
}

export function appendToolSegment(
  segments: MessageSegment[] | undefined,
  chunk: string,
): MessageSegment[] {
  const list = segments ? [...segments] : []
  const trimmed = chunk?.trim()
  if (!trimmed) {
    return list
  }
  list.push({ type: 'tool', value: trimmed })
  return list
}

/** 按工具类型返回不同样式：写入=绿、读取=紫、修改=橙、删除=红 */
export function getToolSegmentClass(value: string): string {
  const trimmed = value.trim()
  if (TOOL_WRITE_PATTERN.test(trimmed) || trimmed === '[选择工具] 写入文件') {
    return 'tool-call-item tool-write'
  }
  if (TOOL_READ_PATTERN.test(trimmed)) {
    return 'tool-call-item tool-read'
  }
  if (TOOL_MODIFY_PATTERN.test(trimmed)) {
    return 'tool-call-item tool-modify'
  }
  if (TOOL_DELETE_PATTERN.test(trimmed)) {
    return 'tool-call-item tool-delete'
  }
  return 'tool-call-item tool-other'
}
