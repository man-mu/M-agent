import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConversationStore } from './ConversationStore'

const service = vi.hoisted(() => ({
  getConversations: vi.fn(),
  getSessionHistory: vi.fn(),
  deleteConversation: vi.fn(),
  exists: vi.fn(),
}))

vi.mock('@/services', () => ({
  conversationService: {
    getConversations: service.getConversations,
    getSessionHistory: service.getSessionHistory,
    deleteConversation: service.deleteConversation,
  },
  reportService: {
    exists: service.exists,
  },
}))

describe('ConversationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    service.getConversations.mockReset()
    service.getSessionHistory.mockReset()
    service.deleteConversation.mockReset()
    service.exists.mockReset()
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

  it('keeps backend status while merging local and backend conversations', async () => {
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
      {
        key: 'backend-1',
        title: 'Backend session',
        messageCount: 2,
        status: 'completed',
        reportAvailable: true,
        reportChecked: true,
        latestThreadId: 'thread-1',
      },
    ]

    await store.loadFromBackend()

    expect(store.conversations[0]).toMatchObject({
      key: 'backend-1',
      status: 'completed',
      reportAvailable: true,
      latestThreadId: 'thread-1',
      local: false,
    })
  })

  it('refreshes the opened conversation status and report availability only on demand', async () => {
    service.getSessionHistory.mockResolvedValue([
      {
        thread_id: 'thread-old',
        session_id: 'session-1',
        query: 'old',
        status: 'STOPPED',
        updated_at: '2026-05-26T00:00:00Z',
      },
      {
        thread_id: 'thread-1',
        session_id: 'session-1',
        query: 'current',
        status: 'COMPLETED',
        report_thread_id: 'thread-1',
        updated_at: '2026-05-27T00:00:00Z',
      },
    ])
    service.exists.mockResolvedValue(true)

    const store = useConversationStore()
    store.conversations = [{ key: 'session-1', title: 'Session', messageCount: 2, status: 'saved' }]

    await store.refreshConversationState('session-1', 'thread-1')

    expect(service.getSessionHistory).toHaveBeenCalledTimes(1)
    expect(service.exists).toHaveBeenCalledWith('thread-1')
    expect(store.conversations[0]).toMatchObject({
      status: 'completed',
      reportAvailable: true,
      reportChecked: true,
      latestThreadId: 'thread-1',
    })
  })

  it('keeps missing history safe without inventing terminal status', async () => {
    service.getSessionHistory.mockResolvedValue([])

    const store = useConversationStore()
    store.conversations = [{ key: 'session-1', title: 'Session', local: false, messageCount: 1 }]

    await store.refreshConversationState('session-1')

    expect(store.conversations[0]).toMatchObject({
      status: 'saved',
    })
    expect(store.conversations[0].reportAvailable).toBe(false)
    expect(store.conversations[0].reportChecked).toBe(false)
    expect(service.exists).not.toHaveBeenCalled()
  })
})
