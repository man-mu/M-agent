import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useMessageStore } from './MessageStore'

describe('MessageStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('extracts report content from final graph output', () => {
    const store = useMessageStore()
    store.events = [
      { content: 'draft' },
      { done: true, content: { output: 'final report' } },
    ]

    expect(store.reportContent).toBe('final report')
  })

  it('falls back to completed reporter content', () => {
    const store = useMessageStore()
    store.events = [
      { node_name: 'reporter', phase: 'completed', content: 'reporter report' },
    ]

    expect(store.reportContent).toBe('reporter report')
  })

  it('stores plan and waits when human feedback event arrives', () => {
    const store = useMessageStore()

    store.addEvent({
      event_type: 'human_feedback.waiting',
      payload: {
        title: 'Plan',
        steps: [{ id: 's1', title: 'Step one' }],
      },
    })

    expect(store.planWaiting).toBe(true)
    expect(store.running).toBe(false)
    expect(store.plan?.title).toBe('Plan')
  })

  it('clears plan waiting state on terminal events', () => {
    const store = useMessageStore()
    store.planWaiting = true

    store.addEvent({ event_type: 'graph.completed', done: true })

    expect(store.planWaiting).toBe(false)
  })
})
