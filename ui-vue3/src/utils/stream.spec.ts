import { describe, expect, it, vi } from 'vitest'
import { AppError } from './errors'
import { parseEventBlock, readSseStream } from './stream'

function streamFrom(chunks: string[]) {
  const encoder = new TextEncoder()
  return new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk))
      }
      controller.close()
    },
  })
}

describe('SSE stream utilities', () => {
  it('parses multi-line JSON data blocks', () => {
    const event = parseEventBlock([
      'id: 42',
      'event: progress',
      'data: {"content":"line one"',
      'data: ,"done":false}',
    ].join('\n'))

    expect(event).toMatchObject({
      id: '42',
      event: 'progress',
      data: {
        content: 'line one',
        done: false,
      },
    })
  })

  it('keeps non-JSON event data as text', () => {
    const event = parseEventBlock('event: note\ndata: plain text')

    expect(event?.event).toBe('note')
    expect(event?.data).toBe('plain text')
  })

  it('returns parsed events from a successful stream', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(streamFrom([
      'event: progress\ndata: {"content":"working"}\n\n',
      'event: message\ndata: {"done":true,"content":"finished"}\n\n',
    ]), {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    })))

    const seen: unknown[] = []
    const events = await readSseStream('/chat/stream', { body: { query: 'hello' } }, event => seen.push(event.data))

    expect(events).toHaveLength(2)
    expect(seen).toEqual([{ content: 'working' }, { done: true, content: 'finished' }])
  })

  it('throws an AppError when backend emits an error event', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(streamFrom([
      'event: error\ndata: {"reason":"model failed"}\n\n',
    ]), {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    })))

    await expect(readSseStream('/chat/stream', {}, () => undefined))
      .rejects
      .toMatchObject<AppError>({
        kind: 'sse',
        message: 'model failed',
      })
  })
})
