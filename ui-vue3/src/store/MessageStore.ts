import { defineStore } from 'pinia'
import type { ChatStreamResponse } from '@/services/api/chat'
import { conversationService } from '@/services'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: string
  threadId?: string
}

export interface MessageState {
  convId: string
  threadId: string
  deepResearch: boolean
  running: boolean
  events: ChatStreamResponse[]
  messages: ChatMessage[]
}

function initialState(): MessageState {
  return {
    convId: '',
    threadId: '',
    deepResearch: true,
    running: false,
    events: [],
    messages: [],
  }
}

function graphId(event: ChatStreamResponse) {
  return event.graphId || event.graph_id
}

export const useMessageStore = defineStore('messageStore', {
  state: initialState,
  getters: {
    reportContent(state) {
      const done = [...state.events].reverse().find(event => event.done)
      const content = done?.content
      if (content && typeof content === 'object' && 'output' in content) {
        return String((content as { output?: unknown }).output || '')
      }
      if (typeof content === 'string') {
        return content
      }
      const reporter = [...state.events]
        .reverse()
        .find(event => (event.nodeName || event.node_name) === 'reporter' && event.phase === 'completed')
      if (typeof reporter?.content === 'string') {
        return reporter.content
      }
      return ''
    },
  },
  actions: {
    reset(sessionId = '') {
      this.convId = sessionId
      this.threadId = ''
      this.running = false
      this.events = []
      this.messages = []
      this.deepResearch = true
    },
    async init(sessionId: string) {
      this.reset(sessionId)
      try {
        const detail = await conversationService.getMessages(sessionId)
        this.messages = (detail.messages || []).map((item, index) => ({
          id: `${sessionId}-${index}`,
          role: item.role.toUpperCase() === 'USER' ? 'user' : 'assistant',
          content: item.content,
          createdAt: item.created_at,
          threadId: item.thread_id,
        }))
        const lastThread = [...detail.messages].reverse().find(item => item.thread_id)?.thread_id
        if (lastThread) {
          this.threadId = lastThread
        }
      } catch {
        this.messages = []
      }
    },
    addUserMessage(content: string) {
      this.messages.push({
        id: `${Date.now()}-user`,
        role: 'user',
        content,
        createdAt: new Date().toISOString(),
      })
    },
    addAssistantMessage(content: string, threadId?: string) {
      this.messages.push({
        id: `${Date.now()}-assistant`,
        role: 'assistant',
        content,
        createdAt: new Date().toISOString(),
        threadId,
      })
    },
    addEvent(event: ChatStreamResponse) {
      const id = graphId(event)
      if (id?.thread_id) {
        this.threadId = id.thread_id
      }
      this.events.push(event)
    },
  },
})
