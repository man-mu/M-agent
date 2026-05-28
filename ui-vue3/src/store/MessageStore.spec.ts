import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { deriveWorkflowNodes, useMessageStore } from './MessageStore'

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

  it('derives stable workflow nodes from representative SSE events', () => {
    const nodes = deriveWorkflowNodes([
      {
        event_type: 'graph.started',
        node_name: '__START__',
        phase: 'started',
        sequence: 1,
        timestamp: '2026-05-27T10:00:00Z',
      },
      {
        event_type: 'node.started',
        node_name: 'planner',
        node_type: 'planner',
        phase: 'started',
        sequence: 2,
      },
      {
        event_type: 'plan.generated',
        node_name: 'planner',
        phase: 'completed',
        payload: { title: 'Research plan' },
        sequence: 3,
      },
      {
        event_type: 'human_feedback.waiting',
        node_name: 'human_feedback',
        phase: 'waiting',
        payload: { title: 'Review plan' },
        sequence: 4,
      },
      {
        event_type: 'node.completed',
        node_name: 'information',
        phase: 'completed',
        content: 'Information gathering completed',
        siteInformation: [{ title: 'Official doc', url: 'https://example.com/doc' }],
        sequence: 5,
      },
      {
        event_type: 'graph.completed',
        node_name: '__END__',
        done: true,
        sequence: 6,
      },
    ])

    expect(nodes.map(node => [node.key, node.status])).toEqual([
      ['__START__', 'running'],
      ['planner', 'completed'],
      ['human_feedback', 'waiting'],
      ['information', 'completed'],
      ['__END__', 'completed'],
    ])
    expect(nodes.find(node => node.key === 'planner')?.summary).toBe('Research plan')
    expect(nodes.find(node => node.key === 'information')?.sources).toEqual([
      { title: 'Official doc', url: 'https://example.com/doc' },
    ])
  })

  it('handles missing optional SSE fields without fake content', () => {
    const nodes = deriveWorkflowNodes([
      { event_type: 'node.delta' },
      { event_type: 'node.failed', node_name: 'runner', payload: { reason: 'Provider timeout' } },
      { event_type: 'graph.stopped', node_name: '__END__', phase: 'stopped', done: true },
    ])

    expect(nodes).toHaveLength(3)
    expect(nodes[0]).toMatchObject({
      title: '工作流事件',
      status: 'idle',
      summary: '',
      sources: [],
    })
    expect(nodes[1]).toMatchObject({
      key: 'runner',
      status: 'failed',
      summary: 'Provider timeout',
    })
    expect(nodes[2]).toMatchObject({
      key: '__END__',
      status: 'stopped',
      summary: '研究流程已停止。',
    })
  })

  it('hides backend executor names from workflow titles and route summaries', () => {
    const nodes = deriveWorkflowNodes([
      {
        event_type: 'node.completed',
        node_name: 'research_team',
        node_type: 'research_team',
        phase: 'decision',
        payload: { nextRoute: 'PARALLEL_EXECUTOR' },
        sequence: 1,
      },
      {
        event_type: 'node.completed',
        node_name: 'parallel_executor',
        node_type: 'parallel_executor',
        displayTitle: 'Parallel Executor',
        phase: 'step.assigned',
        payload: { assigned_node: 'coder_0' },
        sequence: 2,
      },
      {
        event_type: 'node.started',
        node_name: 'coder_0',
        node_type: 'coder',
        executor_id: 0,
        displayTitle: 'Coder 0',
        phase: 'started',
        sequence: 3,
      },
      {
        event_type: 'node.started',
        node_name: 'researcher_1',
        node_type: 'researcher',
        executor_id: 1,
        display_title: '研究执行 1',
        phase: 'started',
        sequence: 4,
      },
    ])

    expect(nodes.map(node => node.title)).toEqual(['安排研究步骤', '任务分配', '内容整理', '资料分析'])
    expect(nodes[0].summary).toBe('下一步：分配任务')
    expect(nodes.map(node => `${node.title} ${node.summary}`)).not.toContain('Parallel Executor')
    expect(nodes.map(node => `${node.title} ${node.summary}`).join(' ')).not.toContain('Coder 0')
  })

  it('exposes workflow nodes through the store getter', () => {
    const store = useMessageStore()

    store.addEvent({ event_type: 'human_feedback.waiting', node_name: 'human_feedback', phase: 'waiting' })

    expect(store.workflowNodes).toHaveLength(1)
    expect(store.workflowNodes[0]).toMatchObject({
      key: 'human_feedback',
      status: 'waiting',
      statusLabel: '等待确认',
    })
    expect(store.planWaiting).toBe(true)
  })
})
