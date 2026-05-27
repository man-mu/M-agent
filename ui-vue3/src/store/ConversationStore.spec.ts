import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConversationStore } from './ConversationStore'

const service = vi.hoisted(() => ({
  getConversations: vi.fn(),
  deleteConversation: vi.fn(),
}))

vi.mock('@/services', () => ({
  conversationService: service,
}))

describe('ConversationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    service.getConversations.mockReset()
    service.deleteConversation.mockReset()
  })

  it('keeps active local sessions while merging backend conversations', async () => {
    service.getConversations.mockResolvedValue([
      {
        session_id: 'backend-1',
        title: 'Backend session',
        message_count: 2,
        last_message_at: '2026-05-27T00:00:00Z',
      },
    ])

    const store = useConversationStore()
    store.conversations = [
      { key: 'local-active', title: 'Local active', local: true, messageCount: 1 },
      { key: 'local-empty', title: 'Local empty', local: true, messageCount: 0 },
    ]
    store.currentKey = 'local-active'

    await store.loadFromBackend()

    expect(store.lastError).toBe('')
    expect(store.conversations.map(item => item.key)).toEqual(['local-active', 'backend-1'])
    expect(store.currentKey).toBe('local-active')
  })

  it('clears current key when backend no longer contains it', async () => {
    service.getConversations.mockResolvedValue([])

    const store = useConversationStore()
    store.conversations = [{ key: 'old', title: 'Old', messageCount: 0 }]
    store.currentKey = 'old'

    await store.loadFromBackend()

    expect(store.conversations).toEqual([])
    expect(store.currentKey).toBe('')
  })

  it('keeps existing conversations and stores visible load errors', async () => {
    service.getConversations.mockRejectedValue(new Error('backend down'))

    const store = useConversationStore()
    store.conversations = [{ key: 'local-active', title: 'Local active', local: true, messageCount: 1 }]

    await store.loadFromBackend()

    expect(store.conversations).toHaveLength(1)
    expect(store.lastError).toContain('backend down')
  })
})
