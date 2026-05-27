import { defineStore } from 'pinia'
import { v4 as uuid } from 'uuid'
import { conversationService, reportService } from '@/services'
import type { ResearchSessionHistory } from '@/services/api/conversation'
import { isNotFoundError, userMessageFromError } from '@/utils/errors'

export type ConversationStatusKind = 'draft' | 'saved' | 'completed' | 'stopped' | 'failed' | 'running' | 'waiting' | 'unknown'

export interface ConversationItem {
  key: string
  title: string
  messageCount?: number
  lastMessageAt?: string
  local?: boolean
  status?: ConversationStatusKind
  reportAvailable?: boolean
  reportChecked?: boolean
  latestThreadId?: string
}

export const useConversationStore = defineStore('conversationStore', {
  state: () => ({
    currentKey: '',
    conversations: [] as ConversationItem[],
    lastError: '',
  }),
  getters: {
    curConvKey: state => state.currentKey,
  },
  actions: {
    async loadFromBackend() {
      try {
        const summaries = await conversationService.getConversations()
        this.lastError = ''
        const backendItems = summaries.map(item => {
          const existing = this.conversations.find(conv => conv.key === item.session_id)
          return {
            key: item.session_id,
            title: item.title || '未命名会话',
            messageCount: item.message_count,
            lastMessageAt: item.last_message_at,
            local: false,
            status: existing?.status && existing.status !== 'draft' ? existing.status : 'saved',
            reportAvailable: existing?.reportAvailable,
            reportChecked: existing?.reportChecked,
            latestThreadId: existing?.latestThreadId,
          }
        })
        const backendKeys = new Set(backendItems.map(item => item.key))
        const localActiveItems = this.conversations.filter(item =>
          item.local && !backendKeys.has(item.key) && (item.messageCount || 0) > 0,
        )
        this.conversations = [...localActiveItems, ...backendItems]
        if (this.currentKey && !this.contains(this.currentKey)) {
          this.currentKey = ''
        }
      } catch (error) {
        this.lastError = userMessageFromError(error, '会话列表加载失败，请确认后端服务已启动。')
      }
    },
    startDraft() {
      this.currentKey = ''
    },
    newOne(title = '新研究', messageCount = 0) {
      const item: ConversationItem = {
        key: uuid(),
        title,
        messageCount,
        local: true,
        status: 'draft',
      }
      this.conversations = [item, ...this.conversations]
      this.currentKey = item.key
      return item
    },
    activate(key: string) {
      this.currentKey = key
    },
    contains(key: string) {
      return this.conversations.some(item => item.key === key)
    },
    upsert(key: string, title?: string) {
      const existing = this.conversations.find(item => item.key === key)
      if (existing) {
        if (title && existing.title === '新研究') {
          existing.title = title
        }
        this.currentKey = key
        return existing
      }
      const item: ConversationItem = {
        key,
        title: title || '新研究',
        messageCount: 0,
        local: true,
        status: 'draft',
      }
      this.conversations = [item, ...this.conversations]
      this.currentKey = key
      return item
    },
    updateTitle(key: string, title: string) {
      const item = this.conversations.find(conv => conv.key === key)
      if (item && (!item.title || item.title === '新研究' || item.title === 'Unnamed conversation')) {
        item.title = title.length > 50 ? `${title.slice(0, 50)}...` : title
      }
    },
    markLocalMessage(key: string) {
      const item = this.conversations.find(conv => conv.key === key)
      if (item) {
        item.messageCount = Math.max(item.messageCount || 0, 1)
        item.local = true
        item.status = 'draft'
      }
    },
    async refreshConversationState(sessionId: string, threadId?: string) {
      if (!sessionId) {
        return null
      }
      try {
        const histories = await conversationService.getSessionHistory(sessionId)
        const latest = latestHistory(histories, threadId)
        const item = this.conversations.find(conv => conv.key === sessionId)
        const latestThreadId = latest?.thread_id || threadId || item?.latestThreadId || ''
        let reportAvailable = Boolean(latest?.report_thread_id || item?.reportAvailable)
        let reportChecked = Boolean(item?.reportChecked)
        if (latestThreadId) {
          reportAvailable = await reportExists(latestThreadId)
          reportChecked = true
        }
        if (item) {
          item.status = statusFromHistory(latest) || (item.local ? 'draft' : 'saved')
          item.local = item.local && !histories.length
          item.latestThreadId = latestThreadId
          item.reportAvailable = reportAvailable
          item.reportChecked = reportChecked
        }
        return {
          status: statusFromHistory(latest),
          reportAvailable,
          latestThreadId,
        }
      } catch (error) {
        const item = this.conversations.find(conv => conv.key === sessionId)
        if (item) {
          item.status = item.local ? 'draft' : item.status || 'unknown'
        }
        return null
      }
    },
    async delete(key: string) {
      this.conversations = this.conversations.filter(item => item.key !== key)
      if (this.currentKey === key) {
        this.currentKey = ''
      }
      try {
        await conversationService.deleteConversation(key)
      } catch {
        // Local sessions may not exist on backend yet.
      }
    },
    async clearAll() {
      const keys = this.conversations.map(item => item.key)
      this.conversations = []
      this.currentKey = ''
      await Promise.all(keys.map(key => conversationService.deleteConversation(key).catch(() => undefined)))
    },
  },
  persist: true,
})

function latestHistory(histories: ResearchSessionHistory[], preferredThreadId = '') {
  if (!histories.length) {
    return null
  }
  if (preferredThreadId) {
    const preferred = histories.find(item => item.thread_id === preferredThreadId)
    if (preferred) {
      return preferred
    }
  }
  return [...histories].sort((left, right) => timestamp(right.updated_at || right.created_at) - timestamp(left.updated_at || left.created_at))[0]
}

function statusFromHistory(history: ResearchSessionHistory | null): ConversationStatusKind | '' {
  const status = history?.status?.toUpperCase()
  if (status === 'COMPLETED') return 'completed'
  if (status === 'STOPPED') return 'stopped'
  if (status === 'FAILED') return 'failed'
  if (status === 'RUNNING') return 'running'
  if (status === 'PAUSED') return 'waiting'
  return ''
}

async function reportExists(threadId: string) {
  try {
    return await reportService.exists(threadId)
  } catch (error) {
    if (isNotFoundError(error)) {
      return false
    }
    return false
  }
}

function timestamp(value = '') {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}
