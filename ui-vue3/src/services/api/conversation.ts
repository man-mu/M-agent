import { apiRequest, del } from '@/utils/request'

export interface ConversationSummary {
  session_id: string
  title: string
  message_count: number
  last_message_at: string
}

export interface ConversationMessage {
  session_id: string
  thread_id: string
  role: string
  content: string
  created_at: string
}

export interface ConversationDetail {
  session_id: string
  title: string
  message_count: number
  messages: ConversationMessage[]
}

export interface ResearchSessionHistory {
  thread_id: string
  session_id: string
  query: string
  status: string
  report_thread_id?: string
  error_message?: string
  created_at?: string
  updated_at?: string
  completed_at?: string
  stopped_at?: string
}

class ConversationService {
  getConversations(): Promise<ConversationSummary[]> {
    return apiRequest<ConversationSummary[]>({
      method: 'GET',
      url: '/api/conversations',
      silentError: true,
    })
  }

  getMessages(sessionId: string): Promise<ConversationDetail> {
    return apiRequest<ConversationDetail>({
      method: 'GET',
      url: `/api/conversations/${sessionId}`,
      silentError: true,
    })
  }

  getSessionHistory(sessionId: string): Promise<ResearchSessionHistory[]> {
    return apiRequest<ResearchSessionHistory[]>({
      method: 'GET',
      url: `/api/sessions/${sessionId}/history`,
      silentError: true,
    })
  }

  getThreadHistory(sessionId: string, threadId: string): Promise<ResearchSessionHistory> {
    return apiRequest<ResearchSessionHistory>({
      method: 'GET',
      url: `/api/sessions/${sessionId}/threads/${threadId}`,
      silentError: true,
    })
  }

  deleteConversation(sessionId: string): Promise<void> {
    return del<void>(`/api/conversations/${sessionId}`)
  }
}

export default new ConversationService()
