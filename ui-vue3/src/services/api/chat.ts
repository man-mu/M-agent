import { readSseStream, type StreamEvent } from '@/utils/stream'
import { post } from '@/utils/request'

export interface GraphId {
  session_id: string
  thread_id: string
}

export interface ChatStreamRequest {
  query: string
  session_id: string
  thread_id?: string
  enable_deepresearch?: boolean
  auto_accepted_plan?: boolean
  max_step_num?: number
  optimize_query_num?: number
}

export interface ChatResumeRequest {
  session_id: string
  thread_id: string
  feedback: boolean
  feedback_content?: string
}

export interface ChatStopRequest {
  session_id: string
  thread_id?: string
}

export interface ChatStreamResponse {
  nodeName?: string
  node_name?: string
  graphId?: GraphId
  graph_id?: GraphId
  displayTitle?: string
  display_title?: string
  content?: unknown
  payload?: unknown
  siteInformation?: unknown
  site_information?: unknown
  sequence?: number
  event_type?: string
  node_type?: string
  executor_id?: string
  step_id?: string
  phase?: string
  status?: string
  done?: boolean
  timestamp?: string
}

export type ChatStreamEvent = StreamEvent<ChatStreamResponse>

class ChatService {
  stream(data: ChatStreamRequest, onEvent: (event: ChatStreamEvent) => void) {
    return readSseStream<ChatStreamResponse>(
      '/chat/stream',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: data,
      },
      onEvent,
    )
  }

  resume(data: ChatResumeRequest, onEvent: (event: ChatStreamEvent) => void) {
    return readSseStream<ChatStreamResponse>(
      '/chat/resume',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: data,
      },
      onEvent,
    )
  }

  async stop(data: ChatStopRequest): Promise<string | null> {
    return post<string | null>('/chat/stop', data)
  }
}

export default new ChatService()
