import { defineStore } from 'pinia'
import { v4 as uuid } from 'uuid'
import { conversationService } from '@/services'

export interface ConversationItem {
  key: string
  title: string
  messageCount?: number
  lastMessageAt?: string
  local?: boolean
}

export const useConversationStore = defineStore('conversationStore', {
  state: () => ({
    currentKey: '',
    conversations: [] as ConversationItem[],
  }),
  getters: {
    curConvKey: state => state.currentKey,
  },
  actions: {
    async loadFromBackend() {
      try {
        const summaries = await conversationService.getConversations()
        const backendItems = summaries.map(item => ({
          key: item.session_id,
          title: item.title || '未命名会话',
          messageCount: item.message_count,
          lastMessageAt: item.last_message_at,
        }))
        const backendKeys = new Set(backendItems.map(item => item.key))
        const localActiveItems = this.conversations.filter(item =>
          item.local && !backendKeys.has(item.key) && (item.messageCount || 0) > 0,
        )
        this.conversations = [...localActiveItems, ...backendItems]
        if (this.currentKey && !this.contains(this.currentKey)) {
          this.currentKey = ''
        }
      } catch {
        // Keep local persisted sessions when backend is not reachable.
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
