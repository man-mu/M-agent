export interface StreamEvent<T = unknown> {
  id?: string
  event: string
  data: T
  raw: string
}

interface StreamRequestOptions {
  method?: 'POST' | 'GET'
  headers?: Record<string, string>
  body?: unknown
  signal?: AbortSignal
}

function buildUrl(url: string) {
  const baseURL = import.meta.env.VITE_BASE_URL || ''
  if (url.startsWith('http')) {
    return url
  }
  return `${baseURL}${url.startsWith('/') ? url : `/${url}`}`
}

function parseEventBlock(block: string): StreamEvent | null {
  const lines = block.split(/\r?\n/)
  const dataLines: string[] = []
  let event = 'message'
  let id: string | undefined

  for (const line of lines) {
    if (!line || line.startsWith(':')) {
      continue
    }
    const separator = line.indexOf(':')
    const field = separator >= 0 ? line.slice(0, separator) : line
    const value = separator >= 0 ? line.slice(separator + 1).replace(/^ /, '') : ''

    if (field === 'event') {
      event = value || 'message'
    } else if (field === 'id') {
      id = value
    } else if (field === 'data') {
      dataLines.push(value)
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  const raw = dataLines.join('\n')
  let data: unknown = raw
  try {
    data = JSON.parse(raw)
  } catch {
    // Keep non-JSON chunks as text.
  }

  return { id, event, data, raw }
}

export async function readSseStream<T = unknown>(
  url: string,
  options: StreamRequestOptions,
  onEvent: (event: StreamEvent<T>) => void,
) {
  const headers = {
    Accept: 'text/event-stream',
    ...(options.headers || {}),
  }

  const response = await fetch(buildUrl(url), {
    method: options.method || 'POST',
    headers,
    body: options.body == null ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`请求失败：HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const events: StreamEvent<T>[] = []

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''

    for (const block of blocks) {
      const parsed = parseEventBlock(block)
      if (parsed) {
        events.push(parsed as StreamEvent<T>)
        onEvent(parsed as StreamEvent<T>)
      }
    }
  }

  buffer += decoder.decode()
  const tail = parseEventBlock(buffer)
  if (tail) {
    events.push(tail as StreamEvent<T>)
    onEvent(tail as StreamEvent<T>)
  }

  return events
}
