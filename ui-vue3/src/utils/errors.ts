export type AppErrorKind =
  | 'abort'
  | 'badRequest'
  | 'moduleDisabled'
  | 'network'
  | 'notFound'
  | 'server'
  | 'sse'
  | 'unknown'

interface AppErrorOptions {
  kind: AppErrorKind
  message: string
  status?: number
  detail?: string
  endpoint?: string
  cause?: unknown
}

interface NormalizeOptions {
  endpoint?: string
  fallback?: string
}

export class AppError extends Error {
  kind: AppErrorKind
  status?: number
  detail?: string
  endpoint?: string
  cause?: unknown
  response?: unknown
  config?: unknown

  constructor(options: AppErrorOptions) {
    super(options.message)
    this.name = 'AppError'
    this.kind = options.kind
    this.status = options.status
    this.detail = options.detail
    this.endpoint = options.endpoint
    this.cause = options.cause
  }
}

export function createAppError(options: AppErrorOptions) {
  return new AppError(options)
}

export function isAbortError(error: unknown) {
  const candidate = error as { kind?: string; name?: string; code?: string } | undefined
  return candidate?.kind === 'abort'
    || candidate?.name === 'AbortError'
    || candidate?.code === 'ERR_CANCELED'
}

export function isNotFoundError(error: unknown) {
  const candidate = error as { kind?: string; status?: number; response?: { status?: number } } | undefined
  return candidate?.kind === 'notFound'
    || candidate?.kind === 'moduleDisabled'
    || candidate?.status === 404
    || candidate?.response?.status === 404
}

export function toAppError(error: unknown, options: NormalizeOptions = {}) {
  if (error instanceof AppError) {
    return error
  }

  const candidate = error as {
    code?: string
    config?: { url?: string }
    isAxiosError?: boolean
    message?: string
    name?: string
    response?: { status?: number; data?: unknown }
    status?: number
  } | undefined

  const endpoint = options.endpoint || candidate?.config?.url

  if (isAbortError(error)) {
    return createAppError({
      kind: 'abort',
      message: '请求已停止。',
      endpoint,
      cause: error,
    })
  }

  const status = candidate?.response?.status ?? candidate?.status
  const responseDetail = payloadMessage(candidate?.response?.data)
  const detail = responseDetail || candidate?.message || ''

  if (status) {
    const appError = createAppError({
      kind: kindForStatus(status, endpoint),
      status,
      detail,
      endpoint,
      message: messageForStatus(status, endpoint, responseDetail, options.fallback, Boolean(candidate?.isAxiosError)),
      cause: error,
    })
    appError.response = candidate?.response
    appError.config = candidate?.config
    return appError
  }

  if (candidate?.isAxiosError
    || candidate?.message === 'Network Error'
    || candidate?.message === 'Failed to fetch'
    || candidate?.message === 'Load failed'
    || candidate?.code === 'ERR_NETWORK'
  ) {
    const appError = createAppError({
      kind: 'network',
      message: '无法连接后端服务，请确认后端已启动，并检查 API Base URL 或开发代理配置。',
      endpoint,
      cause: error,
    })
    appError.config = candidate?.config
    return appError
  }

  return createAppError({
    kind: 'unknown',
    message: candidate?.message || options.fallback || '请求失败，请稍后重试。',
    detail,
    endpoint,
    cause: error,
  })
}

export function userMessageFromError(error: unknown, fallback = '请求失败，请稍后重试。') {
  return toAppError(error, { fallback }).message || fallback
}

export function streamEventErrorMessage(data: unknown) {
  const message = payloadMessage(data)
  return message || '研究流程失败，后端未返回详细原因。'
}

function kindForStatus(status: number, endpoint = ''): AppErrorKind {
  if (status === 404 && endpoint.includes('/api/skills')) {
    return 'moduleDisabled'
  }
  if (status === 404) {
    return 'notFound'
  }
  if (status === 400 || status === 422) {
    return 'badRequest'
  }
  if (status >= 500) {
    return 'server'
  }
  return 'unknown'
}

function messageForStatus(
  status: number,
  endpoint = '',
  detail = '',
  fallback?: string,
  axiosError = false,
) {
  if (status === 404 && endpoint.includes('/api/skills')) {
    return 'Skill 模块未启用，当前后端未开放 /api/skills。'
  }
  if (status === 404 && endpoint.includes('/api/reports')) {
    return '报告不存在或尚未生成。'
  }
  if (status === 404 && endpoint.includes('/api/conversations')) {
    return '没有找到这条会话记录。'
  }
  if (status === 404) {
    return withDetail('接口不存在或资源不可用（HTTP 404）。', detail)
  }
  if (status === 400 || status === 422) {
    return withDetail('请求参数不符合后端要求。', detail || fallback)
  }
  if (status >= 500) {
    if (isBackendUnavailableDetail(detail) || (!detail && axiosError)) {
      return '无法连接后端服务，请确认后端已启动，并检查 VITE_BACKEND_PROXY 或 VITE_BASE_URL 配置。'
    }
    return withDetail(`后端服务返回 ${status}，请查看后端日志。`, detail || fallback)
  }
  return withDetail(fallback || `请求失败：HTTP ${status}`, detail)
}

function withDetail(message: string, detail = '') {
  if (!detail || message.includes(detail)) {
    return message
  }
  return `${message} ${detail}`
}

function isBackendUnavailableDetail(detail = '') {
  const normalized = detail.toLowerCase()
  return normalized.includes('proxy')
    || normalized.includes('econnrefused')
    || normalized.includes('connection refused')
    || normalized.includes('connectex')
}

function payloadMessage(payload: unknown): string {
  if (!payload) {
    return ''
  }
  if (typeof payload === 'string') {
    return payload
  }
  if (typeof payload !== 'object') {
    return String(payload)
  }

  const data = payload as Record<string, unknown>
  const fields = ['message', 'error', 'reason', 'detail']
  for (const field of fields) {
    const value = data[field]
    if (typeof value === 'string' && value.trim()) {
      return value
    }
  }

  const content = data.content
  if (typeof content === 'string' && content.trim()) {
    return content
  }
  if (content && typeof content === 'object') {
    const nested = payloadMessage(content)
    if (nested) {
      return nested
    }
  }

  const errors = data.errors
  if (Array.isArray(errors) && errors.length) {
    return errors.map(item => payloadMessage(item)).filter(Boolean).join('；')
  }

  return ''
}
