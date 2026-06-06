import { apiRequest } from '@/utils/request'

export interface RagUploadResult {
  fileName: string
  chunks: number
  sessionId: string
}

export interface RagUploadResponsePayload {
  file_name?: unknown
  fileName?: unknown
  chunks?: unknown
  session_id?: unknown
  sessionId?: unknown
}

interface RagUploadFallback {
  fileName: string
  sessionId: string
}

export function normalizeRagUploadResult(
  payload: RagUploadResponsePayload | null | undefined,
  fallback: RagUploadFallback,
): RagUploadResult {
  const source = payload || {}
  return {
    fileName: textValue(source.file_name ?? source.fileName) || fallback.fileName,
    chunks: numberValue(source.chunks),
    sessionId: textValue(source.session_id ?? source.sessionId) || fallback.sessionId,
  }
}

class RagService {
  async uploadDocument(file: File, sessionId: string, userId?: string): Promise<RagUploadResult> {
    const boundSessionId = sessionId?.trim()
    if (!boundSessionId) {
      throw new Error('上传前需要先绑定当前会话')
    }

    const data = new FormData()
    data.append('file', file, file.name)
    data.append('session_id', boundSessionId)
    if (userId?.trim()) {
      data.append('user_id', userId.trim())
    }

    const payload = await apiRequest<RagUploadResponsePayload>({
      method: 'POST',
      url: '/api/rag/upload',
      data,
    })
    return normalizeRagUploadResult(payload, {
      fileName: file.name,
      sessionId: boundSessionId,
    })
  }
}

function textValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function numberValue(value: unknown) {
  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric) || numeric < 0) {
    return 0
  }
  return Math.trunc(numeric)
}

export default new RagService()
