import { del, get } from '@/utils/request'

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
    return get<ConversationSummary[]>('/api/conversations')
  }

  getMessages(sessionId: string): Promise<ConversationDetail> {
    return get<ConversationDetail>(`/api/conversations/${sessionId}`)
  }

  deleteConversation(sessionId: string): Promise<void> {
    return del<void>(`/api/conversations/${sessionId}`)
  }
}

export default new ConversationService()
