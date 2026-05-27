import { defineStore } from 'pinia'
import type { ChatStreamResponse, ResearchPlan } from '@/services/api/chat'
import { conversationService } from '@/services'
import { userMessageFromError } from '@/utils/errors'

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
  resuming: boolean
  loading: boolean
  lastError: string
  plan: ResearchPlan | null
  planWaiting: boolean
  events: ChatStreamResponse[]
  messages: ChatMessage[]
}

export function initialMessageState(): MessageState {
  return {
    convId: '',
    threadId: '',
    deepResearch: true,
    running: false,
    resuming: false,
    loading: false,
    lastError: '',
    plan: null,
    planWaiting: false,
    events: [],
    messages: [],
  }
}

function graphId(event: ChatStreamResponse) {
  return event.graphId || event.graph_id
}

function nodeName(event: ChatStreamResponse) {
  return event.node_name || event.nodeName || ''
}

function eventType(event: ChatStreamResponse) {
  return event.event_type || ''
}

function isResearchPlan(value: unknown): value is ResearchPlan {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as ResearchPlan
  return Array.isArray(candidate.steps) || typeof candidate.title === 'string' || typeof candidate.thought === 'string'
}

function eventPlan(event: ChatStreamResponse) {
  if (isResearchPlan(event.payload)) {
    return event.payload
  }
  if (isResearchPlan(event.content)) {
    return event.content
  }
  return null
}

function isPlanGenerated(event: ChatStreamResponse) {
  return eventType(event) === 'plan.generated' || (nodeName(event) === 'planner' && event.phase === 'completed')
}

function isFeedbackWaiting(event: ChatStreamResponse) {
  return eventType(event) === 'human_feedback.waiting'
    || (nodeName(event) === 'human_feedback' && event.phase === 'waiting')
}

function isFeedbackDecision(event: ChatStreamResponse) {
  const type = eventType(event)
  return type === 'human_feedback.accepted'
    || type === 'human_feedback.rejected'
    || (nodeName(event) === 'human_feedback' && event.phase === 'decision')
}

function isTerminalEvent(event: ChatStreamResponse) {
  const type = eventType(event)
  return event.done || type === 'graph.completed' || type === 'graph.failed' || type === 'graph.stopped'
}

export const useMessageStore = defineStore('messageStore', {
  state: initialMessageState,
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
      this.resuming = false
      this.loading = false
      this.lastError = ''
      this.plan = null
      this.planWaiting = false
      this.events = []
      this.messages = []
      this.deepResearch = true
    },
    prepareLocalSession(sessionId: string) {
      this.convId = sessionId
      this.threadId = ''
      this.running = false
      this.resuming = false
      this.loading = false
      this.lastError = ''
      this.plan = null
      this.planWaiting = false
      this.events = []
      this.messages = []
    },
    async init(sessionId: string) {
      this.reset(sessionId)
      this.loading = true
      try {
        const detail = await conversationService.getMessages(sessionId)
        const messages = detail.messages || []
        if (messages.length === 0) {
          this.lastError = '这条会话暂无消息，可能尚未保存或已被删除。'
          return
        }
        this.messages = messages.map((item, index) => ({
          id: `${sessionId}-${index}`,
          role: item.role.toUpperCase() === 'USER' ? 'user' : 'assistant',
          content: item.content,
          createdAt: item.created_at,
          threadId: item.thread_id,
        }))
        const lastThread = [...messages].reverse().find(item => item.thread_id)?.thread_id
        if (lastThread) {
          this.threadId = lastThread
        }
      } catch (error: any) {
        this.messages = []
        this.lastError = userMessageFromError(error, '会话加载失败，请确认后端服务已启动。')
      } finally {
        this.loading = false
      }
    },
    addUserMessage(content: string) {
      this.lastError = ''
      this.messages.push({
        id: `${Date.now()}-user`,
        role: 'user',
        content,
        createdAt: new Date().toISOString(),
      })
    },
    addAssistantMessage(content: string, threadId?: string) {
      this.lastError = ''
      this.planWaiting = false
      this.resuming = false
      this.messages.push({
        id: `${Date.now()}-assistant`,
        role: 'assistant',
        content,
        createdAt: new Date().toISOString(),
        threadId,
      })
    },
    addEvent(event: ChatStreamResponse) {
      this.lastError = ''
      const id = graphId(event)
      if (id?.thread_id) {
        this.threadId = id.thread_id
      }
      const plan = eventPlan(event)
      if (isPlanGenerated(event) && plan) {
        this.plan = plan
      }
      if (isFeedbackWaiting(event)) {
        if (plan) {
          this.plan = plan
        }
        this.planWaiting = true
        this.running = false
      } else if (isFeedbackDecision(event) || isTerminalEvent(event)) {
        this.planWaiting = false
      }
      this.events.push(event)
    },
    clearPlanGate() {
      this.plan = null
      this.planWaiting = false
      this.resuming = false
    },
  },
})
