import type { RagUploadResult } from '@/services/api/rag'

export const RAG_UPLOAD_MAX_BYTES = 10 * 1024 * 1024
export const RAG_UPLOAD_FORMAT_HINT = '支持文本、Markdown、PDF、Word 等可解析文档'

export type RagUploadStatus = 'uploading' | 'success' | 'error'

export interface RagUploadItem {
  id: string
  fileName: string
  size: number
  status: RagUploadStatus
  sessionId: string
  typeLabel: string
  chunks?: number
  error?: string
  uploadedAt?: string
}

export interface RagUploadValidation {
  valid: boolean
  error?: string
}

export function ragUploadAvailabilityText(ragEnabled: boolean, loading = false, error = '') {
  if (loading) return '正在读取 RAG 状态'
  if (error) return 'RAG 状态读取失败'
  if (!ragEnabled) return 'RAG 未启用'
  return RAG_UPLOAD_FORMAT_HINT
}

export function ragUploadDisabledReason(ragEnabled: boolean, loading = false, error = '') {
  if (loading) return '正在读取 RAG 状态'
  if (error) return '无法确认 RAG 状态，请稍后重试'
  if (!ragEnabled) return 'RAG 未启用'
  return ''
}

export function ragUploadStatusLabel(item: Pick<RagUploadItem, 'status' | 'chunks'>) {
  if (item.status === 'uploading') return '上传中'
  if (item.status === 'success') return `已切块 ${item.chunks ?? 0}`
  return '上传失败'
}

export function ragUploadStatusColor(status: RagUploadStatus) {
  if (status === 'success') return 'green'
  if (status === 'error') return 'red'
  return 'blue'
}

export interface RagUploadSessionOptions {
  routeSessionId?: string | string[]
  currentSessionId?: string
  draftTitle?: string
  createSession: (title: string) => string
}

export interface RagUploadSessionResolution {
  sessionId: string
  created: boolean
  title: string
}

export function resolveRagUploadSession(options: RagUploadSessionOptions): RagUploadSessionResolution {
  const routeSessionId = firstText(options.routeSessionId)
  if (routeSessionId) {
    return { sessionId: routeSessionId, created: false, title: '' }
  }

  const currentSessionId = options.currentSessionId?.trim()
  if (currentSessionId) {
    return { sessionId: currentSessionId, created: false, title: '' }
  }

  const title = options.draftTitle?.trim() || '上传资料'
  const sessionId = options.createSession(title).trim()
  if (!sessionId) {
    throw new Error('上传前需要先绑定当前会话')
  }
  return { sessionId, created: true, title }
}

export function validateRagUploadFile(file: File, maxBytes = RAG_UPLOAD_MAX_BYTES): RagUploadValidation {
  if (file.size <= 0) {
    return { valid: false, error: '文件为空，请选择包含内容的文档。' }
  }
  if (file.size > maxBytes) {
    return {
      valid: false,
      error: `文件不能超过 ${formatFileSize(maxBytes)}，当前文件为 ${formatFileSize(file.size)}。`,
    }
  }
  return { valid: true }
}

export function createRagUploadItem(
  file: File,
  sessionId: string,
  now = new Date(),
  id = createUploadId(file, now),
): RagUploadItem {
  return {
    id,
    fileName: safeFileName(file.name),
    size: file.size,
    status: 'uploading',
    sessionId,
    typeLabel: ragFileTypeLabel(file),
    uploadedAt: now.toISOString(),
  }
}

export function completeRagUploadItem(
  item: RagUploadItem,
  result: RagUploadResult,
  now = new Date(),
): RagUploadItem {
  return {
    ...item,
    fileName: safeFileName(result.fileName || item.fileName),
    status: 'success',
    sessionId: result.sessionId || item.sessionId,
    chunks: result.chunks,
    error: undefined,
    uploadedAt: now.toISOString(),
  }
}

export function failRagUploadItem(item: RagUploadItem, error: unknown): RagUploadItem {
  return {
    ...item,
    status: 'error',
    error: cleanRagUploadError(error),
  }
}

export function ragFileTypeLabel(file: Pick<File, 'name' | 'type'>) {
  const name = file.name.toLowerCase()
  const type = file.type.toLowerCase()
  if (type.includes('pdf') || name.endsWith('.pdf')) return 'PDF'
  if (type.includes('markdown') || name.endsWith('.md') || name.endsWith('.markdown')) return 'Markdown'
  if (type.includes('word') || name.endsWith('.doc') || name.endsWith('.docx')) return 'Word'
  if (type.startsWith('text/') || name.endsWith('.txt') || name.endsWith('.csv')) return '文本'
  return '文档'
}

export function sessionBindingText(sessionId: string) {
  const trimmed = sessionId.trim()
  if (!trimmed) {
    return '上传前会先创建并绑定当前会话。'
  }
  return `资料仅绑定当前会话 ${trimmed}，后续同会话提问可用于 RAG 检索。`
}

export function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${formatNumber(kb)} KB`
  return `${formatNumber(kb / 1024)} MB`
}

export function cleanRagUploadError(error: unknown) {
  const status = httpStatus(error)
  const rawMessage = messageFromUnknown(error)
  const detail = sanitizeErrorDetail(rawMessage)

  if (status === 413) {
    return '文件超过后端上传限制，请选择更小的文件。'
  }
  if (status === 404) {
    return 'RAG 上传接口不可用，请确认后端已启用 RAG。'
  }
  if (status === 400 || status === 422) {
    return detail || '文件为空、无法解析或请求参数不正确。'
  }
  if (status && status >= 500) {
    if (/dashscope|embedding|api key|apikey|model|模型|凭证/i.test(rawMessage)) {
      return 'RAG 模型或 embedding 服务暂不可用，请检查模型供应商配置。'
    }
    return detail ? `上传失败：${detail}` : '上传失败，后端服务处理异常。'
  }
  if (errorKind(error) === 'network') {
    return '无法连接后端服务，请确认后端已启动。'
  }
  return detail ? `上传失败：${detail}` : '上传失败，请稍后重试。'
}

function createUploadId(file: Pick<File, 'name' | 'size'>, now: Date) {
  return `${now.getTime()}-${safeFileName(file.name)}-${file.size}`
}

function firstText(value: string | string[] | undefined) {
  const text = Array.isArray(value) ? value[0] : value
  return text?.trim() || ''
}

function safeFileName(name: string) {
  const normalized = name.split(/[\\/]/).filter(Boolean).pop() || '未命名文档'
  return normalized.trim() || '未命名文档'
}

function formatNumber(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

function httpStatus(error: unknown) {
  const candidate = error as { status?: number; response?: { status?: number } } | undefined
  return candidate?.status ?? candidate?.response?.status
}

function errorKind(error: unknown) {
  return (error as { kind?: string } | undefined)?.kind || ''
}

function messageFromUnknown(value: unknown): string {
  if (!value) return ''
  if (typeof value === 'string') return value
  if (value instanceof Error) return value.message
  if (typeof value !== 'object') return String(value)

  const data = value as Record<string, unknown>
  for (const key of ['message', 'error', 'reason', 'detail']) {
    const field = data[key]
    if (typeof field === 'string' && field.trim()) {
      return field.trim()
    }
  }
  if (data.response && typeof data.response === 'object') {
    return messageFromUnknown((data.response as Record<string, unknown>).data)
  }
  return ''
}

function sanitizeErrorDetail(value: string) {
  return value
    .replace(/[A-Za-z]:\\[^\s"'<>]+/g, '[本地路径]')
    .replace(/\b(?:sk|ak)-[A-Za-z0-9_-]{8,}\b/g, '[已隐藏凭证]')
    .replace(/\b[A-Za-z0-9_-]{32,}\b/g, '[已隐藏敏感值]')
    .trim()
    .slice(0, 180)
}
