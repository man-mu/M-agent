import type { ChatStreamResponse } from '@/services/api/chat'

export interface ReportTocItem {
  key: string
  level: number
  title: string
  anchor: string
}

export interface ReportSourceLink {
  title: string
  url: string
  host: string
}

export type ReportStatusKind = 'not-started' | 'loading' | 'generated' | 'not-generated' | 'read-failed'

export interface ReportStatusView {
  kind: ReportStatusKind
  label: string
  color: string
  emptyDescription: string
}

export function cleanMarkdownHeading(value: string) {
  return value
    .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/<\/?[^>]+>/g, '')
    .replace(/[*_~`]/g, '')
    .trim()
}

export function createHeadingAnchor(title: string, occurrence: number) {
  const slug = title
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '-')
    .replace(/^-+|-+$/g, '')
  return `${slug || 'section'}-${occurrence + 1}`
}

export function deriveReportToc(markdown = ''): ReportTocItem[] {
  const items: ReportTocItem[] = []
  const occurrences = new Map<string, number>()
  let inFence = false

  markdown.split(/\r?\n/).forEach(line => {
    const trimmed = line.trim()
    if (/^(```|~~~)/.test(trimmed)) {
      inFence = !inFence
      return
    }
    if (inFence) {
      return
    }

    const match = /^(#{1,3})\s+(.+?)\s*#*$/.exec(trimmed)
    if (!match) {
      return
    }

    const title = cleanMarkdownHeading(match[2])
    if (!title) {
      return
    }

    const occurrence = occurrences.get(title) || 0
    occurrences.set(title, occurrence + 1)
    const anchor = createHeadingAnchor(title, occurrence)
    items.push({
      key: anchor,
      level: match[1].length,
      title,
      anchor,
    })
  })

  return items
}

export function deriveReportSources(events: ChatStreamResponse[] = []): ReportSourceLink[] {
  const links: ReportSourceLink[] = []
  const seen = new Set<string>()

  for (const event of events) {
    const sources = [
      ...normalizeSourceValue(event.siteInformation || event.site_information),
      ...normalizeSourceValue(readObjectField(event.payload, 'siteInformation') || readObjectField(event.payload, 'site_information')),
    ]

    for (const source of sources) {
      const key = source.url.toLowerCase()
      if (seen.has(key)) {
        continue
      }
      seen.add(key)
      links.push(source)
    }
  }

  return links
}

export function reportStatusView(params: {
  hasThread: boolean
  hasContent: boolean
  loading: boolean
  notGenerated?: boolean
  error: string
}): ReportStatusView {
  if (params.loading && !params.hasContent) {
    return {
      kind: 'loading',
      label: '读取中',
      color: 'processing',
      emptyDescription: '正在读取后端保存的研究报告。',
    }
  }
  if (params.hasContent) {
    return {
      kind: 'generated',
      label: '已生成',
      color: 'green',
      emptyDescription: '',
    }
  }
  if (params.notGenerated) {
    return {
      kind: 'not-generated',
      label: '尚未生成',
      color: 'default',
      emptyDescription: '报告尚未生成，研究完成后会自动显示。',
    }
  }
  if (params.error) {
    return {
      kind: 'read-failed',
      label: '读取失败',
      color: 'orange',
      emptyDescription: params.error,
    }
  }
  if (params.hasThread) {
    return {
      kind: 'not-generated',
      label: '尚未生成',
      color: 'default',
      emptyDescription: '报告尚未生成，研究完成后会自动显示。',
    }
  }
  return {
    kind: 'not-started',
    label: '等待研究',
    color: 'default',
    emptyDescription: '发送问题后会在这里显示研究报告。',
  }
}

function normalizeSourceValue(value: unknown): ReportSourceLink[] {
  if (!Array.isArray(value)) {
    return []
  }

  return value
    .flatMap(item => (Array.isArray(item) ? item : [item]))
    .flatMap(item => {
      if (!item || typeof item !== 'object') {
        return []
      }
      const candidate = item as Record<string, unknown>
      const url = stringField(candidate.url) || stringField(candidate.link) || stringField(candidate.href)
      if (!url) {
        return []
      }
      return [{
        title: stringField(candidate.title) || stringField(candidate.name) || url,
        url,
        host: hostFromUrl(url),
      }]
    })
}

function readObjectField(value: unknown, key: string) {
  if (!value || typeof value !== 'object') {
    return undefined
  }
  return (value as Record<string, unknown>)[key]
}

function stringField(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function hostFromUrl(value: string) {
  try {
    return new URL(value).hostname
  } catch {
    return value
  }
}
