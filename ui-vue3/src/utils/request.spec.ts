import { describe, expect, it } from 'vitest'
import { AppError } from './errors'
import { unwrapApiEnvelope } from './request'

describe('request response unwrap', () => {
  it('unwraps standard ApiResponse data', () => {
    expect(unwrapApiEnvelope({ code: 200, data: { ok: true } })).toEqual({ ok: true })
  })

  it('unwraps report responses', () => {
    expect(unwrapApiEnvelope({ status: 'success', report_information: 'report body' })).toBe('report body')
  })

  it('unwraps conversation history responses', () => {
    const history = { messages: [{ role: 'USER', content: 'hi' }] }
    expect(unwrapApiEnvelope({ status: 'success', session_history: history })).toBe(history)
  })

  it('keeps plain objects unchanged', () => {
    const payload = { providerId: 'deepseek', configured: true }
    expect(unwrapApiEnvelope(payload)).toBe(payload)
  })

  it('throws typed not-found errors', () => {
    expect(() => unwrapApiEnvelope({ status: 'notfound', message: 'Report not found' }))
      .toThrow(AppError)

    try {
      unwrapApiEnvelope({ status: 'notfound', message: 'Report not found' })
    } catch (error) {
      expect(error).toMatchObject({
        kind: 'notFound',
        message: 'Report not found',
      })
    }
  })
})
