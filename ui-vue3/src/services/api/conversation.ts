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

  deleteConversation(sessionId: string): Promise<void> {
    return del<void>(`/api/conversations/${sessionId}`)
  }
}

export default new ConversationService()
