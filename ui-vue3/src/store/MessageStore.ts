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

export type WorkflowStatus = 'running' | 'waiting' | 'completed' | 'failed' | 'stopped' | 'idle'

export interface SourceLink {
  title?: string
  url?: string
}

export interface WorkflowNodeView {
  key: string
  title: string
  status: WorkflowStatus
  statusLabel: string
  color: string
  eventType: string
  phase: string
  summary: string
  sources: SourceLink[]
  sequence?: number
  timestamp?: string
}

const nodeTitleMap: Record<string, string> = {
  __START__: '开始',
  __END__: '结束',
  coordinator: 'Coordinator',
  rewrite_multi_query: 'Query Rewrite',
  background_investigator: 'Background Investigation',
  user_file_rag: 'User File RAG',
  professional_kb_decision: 'Professional KB Decision',
  professional_kb_rag: 'Professional KB RAG',
  planner: '研究计划',
  plan_validator: 'Plan Validator',
  human_feedback: '人工反馈',
  research_team: '研究团队',
  parallel_executor: 'Parallel Executor',
  information: '信息检索',
  researcher: '研究执行',
  coder: 'Coder',
  reporter: '报告生成',
  runner: '运行异常',
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

function statusFromEvent(event: ChatStreamResponse): WorkflowStatus {
  const type = eventType(event)
  if (type === 'graph.failed' || type === 'node.failed' || event.phase === 'failed' || event.phase === 'error') {
    return 'failed'
  }
  if (type === 'graph.stopped' || event.phase === 'stopped') {
    return 'stopped'
  }
  if (isFeedbackWaiting(event)) {
    return 'waiting'
  }
  if (event.done || type === 'graph.completed' || type === 'node.completed' || type === 'plan.generated' || type === 'report.completed' || event.phase === 'completed') {
    return 'completed'
  }
  if (type === 'graph.started' || type === 'node.started' || event.phase === 'started') {
    return 'running'
  }
  return 'idle'
}

function statusLabel(status: WorkflowStatus) {
  if (status === 'waiting') return '等待确认'
  if (status === 'completed') return '已完成'
  if (status === 'failed') return '失败'
  if (status === 'stopped') return '已停止'
  if (status === 'running') return '运行中'
  return '事件'
}

function statusColor(status: WorkflowStatus) {
  if (status === 'waiting') return 'orange'
  if (status === 'completed') return 'green'
  if (status === 'failed') return 'red'
  if (status === 'stopped') return 'gray'
  if (status === 'running') return 'blue'
  return 'gray'
}

function workflowNodeKey(event: ChatStreamResponse) {
  const node = event.node_name || event.nodeName || event.node_type || ''
  const type = event.node_type || node
  if (type === 'researcher' || type === 'coder' || /^researcher_\d+$/.test(node) || /^coder_\d+$/.test(node)) {
    return event.step_id || `${type}-${event.executor_id ?? (node || '0')}`
  }
  return node || event.event_type || `event-${event.sequence ?? event.timestamp ?? 'unknown'}`
}

function workflowTitle(event: ChatStreamResponse) {
  const node = event.node_name || event.nodeName || ''
  const type = event.node_type || node
  const executor = event.executor_id
  if (event.displayTitle || event.display_title) {
    return String(event.displayTitle || event.display_title)
  }
  if (type === 'researcher' || /^researcher_\d+$/.test(node)) {
    return executor === undefined || executor === null ? '研究执行' : `研究执行 ${executor}`
  }
  if (type === 'coder' || /^coder_\d+$/.test(node)) {
    return executor === undefined || executor === null ? 'Coder' : `Coder ${executor}`
  }
  return nodeTitleMap[node] || nodeTitleMap[type] || node || '工作流事件'
}

function workflowSummary(event: ChatStreamResponse) {
  if (typeof event.content === 'string' && event.content.trim()) {
    return truncate(event.content.trim(), 220)
  }
  if (statusFromEvent(event) === 'failed') {
    return errorReason(event)
  }
  if (isFeedbackWaiting(event)) {
    return '计划已生成，等待确认或修改意见。'
  }
  if (Array.isArray(event.payload)) {
    return arraySummary(event.payload)
  }
  if (event.payload && typeof event.payload === 'object') {
    const payload = event.payload as Record<string, unknown>
    const payloadSummary = objectSummary(payload)
    if (payloadSummary) {
      return payloadSummary
    }
  }
  if (event.event_type === 'graph.stopped' || event.phase === 'stopped') {
    return '研究流程已停止。'
  }
  if (event.done || event.event_type === 'graph.completed') {
    return '研究流程完成。'
  }
  if (event.event_type === 'graph.started') {
    return '研究流程已启动。'
  }
  if (event.event_type === 'node.started' || event.phase === 'started') {
    return '节点已开始执行。'
  }
  return ''
}

function objectSummary(payload: Record<string, unknown>) {
  if (typeof payload.title === 'string' && payload.title.trim()) return truncate(payload.title.trim(), 220)
  if (typeof payload.query === 'string' && payload.query.trim()) return `查询：${truncate(payload.query.trim(), 200)}`
  if (typeof payload.route === 'string' && payload.route.trim()) return `路由：${payload.route}`
  if (typeof payload.next_route === 'string' && payload.next_route.trim()) return `路由：${payload.next_route}`
  if (typeof payload.nextRoute === 'string' && payload.nextRoute.trim()) return `路由：${payload.nextRoute}`
  if (payload.completedSteps != null && payload.totalSteps != null) {
    return `已完成 ${payload.completedSteps}/${payload.totalSteps} 个步骤。`
  }
  if (payload.step && typeof payload.step === 'object') {
    const step = payload.step as Record<string, unknown>
    if (typeof step.title === 'string' && step.title.trim()) return truncate(step.title.trim(), 220)
    if (typeof step.description === 'string' && step.description.trim()) return truncate(step.description.trim(), 220)
  }
  if (typeof payload.reason === 'string' && payload.reason.trim()) return truncate(payload.reason.trim(), 220)
  if (Array.isArray(payload.siteInformation)) return `返回 ${payload.siteInformation.length} 条来源。`
  if (Array.isArray(payload.site_information)) return `返回 ${payload.site_information.length} 条来源。`
  return ''
}

function arraySummary(items: unknown[]) {
  if (items.length === 0) {
    return ''
  }
  if (items.every(item => typeof item === 'string' || typeof item === 'number')) {
    return `命中：${truncate(items.join('、'), 220)}`
  }
  return `返回 ${items.length} 条结果。`
}

function sourceLinks(event: ChatStreamResponse): SourceLink[] {
  const direct = normalizeSources(event.siteInformation || event.site_information)
  if (direct.length) {
    return direct
  }
  if (event.payload && typeof event.payload === 'object') {
    const payload = event.payload as Record<string, unknown>
    const payloadSources = normalizeSources(payload.siteInformation || payload.site_information)
    if (payloadSources.length) {
      return payloadSources
    }
  }
  return []
}

function normalizeSources(value: unknown): SourceLink[] {
  if (!Array.isArray(value)) {
    return []
  }
  const links: SourceLink[] = []
  const seen = new Set<string>()
  for (const item of value.flatMap(entry => (Array.isArray(entry) ? entry : [entry]))) {
    if (!item || typeof item !== 'object') {
      continue
    }
    const candidate = item as Record<string, unknown>
    const url = typeof candidate.url === 'string' ? candidate.url.trim() : ''
    const title = typeof candidate.title === 'string' ? candidate.title.trim() : ''
    if (!url && !title) {
      continue
    }
    const key = url || title
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    links.push({ title, url })
  }
  return links.slice(0, 6)
}

function errorReason(event: ChatStreamResponse) {
  const content = event.content
  if (typeof content === 'string' && content.trim()) {
    return truncate(content.trim(), 220)
  }
  if (content && typeof content === 'object' && 'reason' in content) {
    const reason = (content as { reason?: unknown }).reason
    if (typeof reason === 'string' && reason.trim()) {
      return truncate(reason.trim(), 220)
    }
  }
  if (event.payload && typeof event.payload === 'object' && 'reason' in event.payload) {
    const reason = (event.payload as { reason?: unknown }).reason
    if (typeof reason === 'string' && reason.trim()) {
      return truncate(reason.trim(), 220)
    }
  }
  return '后端处理出错'
}

function truncate(value: string, maxLength: number) {
  return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value
}

export function deriveWorkflowNodes(events: ChatStreamResponse[]): WorkflowNodeView[] {
  const nodes = new Map<string, WorkflowNodeView>()
  events.forEach((event, index) => {
    const key = workflowNodeKey(event)
    const previous = nodes.get(key)
    const status = statusFromEvent(event)
    const eventSources = sourceLinks(event)
    const sequence = event.sequence ?? previous?.sequence ?? index + 1
    nodes.set(key, {
      key,
      title: workflowTitle(event),
      status,
      statusLabel: statusLabel(status),
      color: statusColor(status),
      eventType: event.event_type || '',
      phase: event.phase || event.status || '',
      summary: workflowSummary(event) || previous?.summary || '',
      sources: eventSources.length ? eventSources : previous?.sources || [],
      sequence,
      timestamp: event.timestamp || previous?.timestamp,
    })
  })
  return [...nodes.values()].sort((left, right) => (left.sequence ?? 0) - (right.sequence ?? 0))
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
    workflowNodes(state) {
      return deriveWorkflowNodes(state.events)
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
