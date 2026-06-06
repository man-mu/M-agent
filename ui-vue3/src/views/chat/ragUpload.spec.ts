import { describe, expect, it } from 'vitest'
import { normalizeRagUploadResult } from '@/services/api/rag'
import {
  cleanRagUploadError,
  completeRagUploadItem,
  createRagUploadItem,
  ragFileTypeLabel,
  sessionBindingText,
  validateRagUploadFile,
} from './ragUpload'

describe('chat RAG upload helpers', () => {
  it('validates empty and oversized files', () => {
    expect(validateRagUploadFile(new File([], 'empty.txt')).valid).toBe(false)

    const oversized = new File([new Uint8Array(11 * 1024 * 1024)], 'large.pdf', {
      type: 'application/pdf',
    })
    const result = validateRagUploadFile(oversized)
    expect(result.valid).toBe(false)
    expect(result.error).toContain('10 MB')
  })

  it('normalizes backend upload responses', () => {
    expect(normalizeRagUploadResult(
      { file_name: 'guide.md', chunks: '3', session_id: 'session-1' },
      { fileName: 'fallback.txt', sessionId: 'fallback-session' },
    )).toEqual({
      fileName: 'guide.md',
      chunks: 3,
      sessionId: 'session-1',
    })

    expect(normalizeRagUploadResult({}, {
      fileName: 'fallback.txt',
      sessionId: 'fallback-session',
    })).toEqual({
      fileName: 'fallback.txt',
      chunks: 0,
      sessionId: 'fallback-session',
    })
  })

  it('builds upload items and marks successful chunk counts', () => {
    const now = new Date('2026-06-06T05:00:00.000Z')
    const item = createRagUploadItem(
      new File(['hello'], 'C:\\fakepath\\notes.md', { type: 'text/markdown' }),
      'session-1',
      now,
      'upload-1',
    )

    expect(item).toMatchObject({
      id: 'upload-1',
      fileName: 'notes.md',
      sessionId: 'session-1',
      status: 'uploading',
      typeLabel: 'Markdown',
    })

    expect(completeRagUploadItem(item, {
      fileName: 'notes.md',
      chunks: 2,
      sessionId: 'session-1',
    }, now)).toMatchObject({
      fileName: 'notes.md',
      chunks: 2,
      status: 'success',
    })
  })

  it('cleans upload errors for user-facing display', () => {
    expect(cleanRagUploadError({ status: 413, message: 'too large' })).toContain('超过后端上传限制')
    expect(cleanRagUploadError({ status: 404, message: 'Not Found' })).toContain('RAG 上传接口不可用')
    expect(cleanRagUploadError({
      status: 500,
      message: 'DashScope API key missing: sk-1234567890abcdef',
    })).toBe('RAG 模型或 embedding 服务暂不可用，请检查模型供应商配置。')
    expect(cleanRagUploadError({
      status: 400,
      message: 'Failed at C:\\Users\\me\\secret\\file.txt with token abcdefghijklmnopqrstuvwxyz123456',
    })).not.toContain('C:\\Users\\me')
  })

  it('labels supported file types and explains session binding', () => {
    expect(ragFileTypeLabel(new File(['# hi'], 'guide.md', { type: 'text/markdown' }))).toBe('Markdown')
    expect(ragFileTypeLabel(new File(['pdf'], 'guide.pdf', { type: 'application/pdf' }))).toBe('PDF')
    expect(sessionBindingText('session-1')).toContain('session-1')
    expect(sessionBindingText('')).toContain('上传前')
  })
})
