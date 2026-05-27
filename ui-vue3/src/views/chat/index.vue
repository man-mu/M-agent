<template>
  <div class="chat-page">
    <section class="chat-main">
      <div class="chat-header">
        <div>
          <div class="eyebrow">DeepResearch Workflow</div>
          <h1>研究对话</h1>
        </div>
        <a-space>
          <a-tooltip title="模型设置">
            <a-button @click="router.push('/settings')">
              <SettingOutlined />
            </a-button>
          </a-tooltip>
          <a-button :disabled="!messageStore.threadId" @click="reportVisible = !reportVisible">
            <FileTextOutlined />
            报告
          </a-button>
        </a-space>
      </div>

      <div ref="scrollRef" class="message-area">
        <div v-if="messageStore.loading" class="welcome">
          <a-spin />
        </div>

        <div v-else-if="messageStore.lastError" class="welcome">
          <h2>会话暂时无法加载。</h2>
          <p>{{ messageStore.lastError }}</p>
          <a-space>
            <a-button @click="retryLoad">重试</a-button>
            <a-button type="primary" @click="startDraft">新研究</a-button>
          </a-space>
        </div>

        <div v-else-if="messageStore.messages.length === 0 && messageStore.events.length === 0" class="welcome">
          <h2>输入一个问题，启动真实模型研究流程。</h2>
          <p>前端会直接消费后端 SSE，展示节点进度、来源信息和最终报告。</p>
        </div>

        <article
          v-for="item in messageStore.messages"
          :key="item.id"
          class="message"
          :class="item.role"
        >
          <div class="avatar">{{ item.role === 'user' ? '你' : 'AI' }}</div>
          <div class="bubble">
            <MD v-if="item.role === 'assistant'" :content="item.content" />
            <p v-else>{{ item.content }}</p>
          </div>
        </article>

        <div v-if="activePlan" class="plan-review" data-testid="plan-review">
          <div class="plan-review-header">
            <div>
              <span class="plan-label">计划确认</span>
              <h2>{{ activePlan.title || '研究计划' }}</h2>
            </div>
            <a-tag color="orange">等待确认</a-tag>
          </div>

          <p v-if="activePlan.thought" class="plan-thought">{{ activePlan.thought }}</p>

          <ol v-if="planSteps.length" class="plan-steps">
            <li
              v-for="(step, index) in planSteps"
              :key="step.id || `${step.title}-${index}`"
            >
              <div class="step-title">
                <strong>{{ step.title || `步骤 ${index + 1}` }}</strong>
                <a-tag v-if="step.need_web_search" color="blue">搜索</a-tag>
                <a-tag v-if="step.step_type">{{ step.step_type }}</a-tag>
              </div>
              <p v-if="step.description">{{ step.description }}</p>
            </li>
          </ol>

          <a-textarea
            v-if="feedbackVisible"
            v-model:value="feedbackDraft"
            :auto-size="{ minRows: 2, maxRows: 4 }"
            :disabled="messageStore.resuming || messageStore.running"
            class="plan-feedback"
            data-testid="plan-feedback"
            placeholder="写下你希望调整的方向..."
          />

          <div class="plan-actions">
            <a-button
              :disabled="messageStore.resuming || messageStore.running"
              data-testid="modify-plan"
              @click="feedbackVisible = !feedbackVisible"
            >
              <EditOutlined />
              修改计划
            </a-button>
            <a-button
              v-if="feedbackVisible"
              :disabled="!feedbackDraft.trim() || messageStore.resuming || messageStore.running"
              :loading="pendingResumeDecision === 'feedback'"
              data-testid="submit-plan-feedback"
              @click="resumePlan(false)"
            >
              <SendOutlined />
              提交修改意见
            </a-button>
            <a-button
              type="primary"
              :disabled="messageStore.resuming || messageStore.running"
              :loading="pendingResumeDecision === 'accept'"
              data-testid="accept-plan"
              @click="resumePlan(true)"
            >
              <CheckCircleOutlined />
              接受计划
            </a-button>
          </div>
        </div>

        <div v-if="messageStore.events.length" class="event-card">
          <div class="event-card-header">
            <span>工作流进度</span>
            <a-tag v-if="messageStore.running" color="processing">运行中</a-tag>
            <a-tag v-else-if="messageStore.planWaiting" color="orange">等待确认</a-tag>
            <a-tag v-else color="default">已结束</a-tag>
          </div>

          <a-timeline>
            <a-timeline-item
              v-for="node in workflowNodes"
              :key="node.key"
              :color="node.color"
            >
              <div class="event-line">
                <strong>{{ node.title }}</strong>
                <a-tag>{{ node.tag }}</a-tag>
              </div>
              <p v-if="node.summary" class="event-summary">{{ node.summary }}</p>
              <div v-if="node.sources.length" class="sources">
                <a
                  v-for="source in node.sources"
                  :key="source.url || source.title"
                  :href="source.url"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {{ source.title || source.url }}
                </a>
              </div>
            </a-timeline-item>
          </a-timeline>
        </div>
      </div>

      <div class="composer">
        <div class="composer-options">
          <a-switch v-model:checked="messageStore.deepResearch" />
          <span>{{ messageStore.deepResearch ? '深度研究' : '快速回答' }}</span>
          <template v-if="messageStore.deepResearch">
            <a-divider type="vertical" />
            <a-switch
              v-model:checked="autoExecutePlan"
              :disabled="messageStore.running || messageStore.planWaiting"
              size="small"
              data-testid="auto-execute-plan"
            />
            <span>自动执行计划</span>
          </template>
          <a-divider type="vertical" />
          <span class="thread" v-if="messageStore.threadId">Thread: {{ messageStore.threadId }}</span>
        </div>
        <a-textarea
          v-model:value="draft"
          :auto-size="{ minRows: 2, maxRows: 5 }"
          :disabled="isInteractionLocked"
          data-testid="composer-input"
          placeholder="输入你的研究问题..."
          @keydown="handleComposerKeydown"
        />
        <div class="composer-actions">
          <a-button
            v-if="messageStore.running || messageStore.planWaiting"
            :disabled="!messageStore.threadId"
            danger
            @click="stop"
          >
            <PauseCircleOutlined />
            停止
          </a-button>
          <a-button
            type="primary"
            :disabled="!draft.trim() || isInteractionLocked"
            data-testid="send-message"
            @click="submit"
          >
            <SendOutlined />
            发送
          </a-button>
        </div>
      </div>
    </section>

    <Report
      :visible="reportVisible"
      :thread-id="messageStore.threadId"
      @close="reportVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CheckCircleOutlined,
  EditOutlined,
  FileTextOutlined,
  PauseCircleOutlined,
  SendOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { chatService } from '@/services'
import type { ChatStreamResponse } from '@/services/api/chat'
import { useConversationStore } from '@/store/ConversationStore'
import { useMessageStore } from '@/store/MessageStore'
import Report from '@/components/report/index.vue'
import MD from '@/components/md/index.vue'

interface SourceLink {
  title?: string
  url?: string
}

interface WorkflowNode {
  key: string
  title: string
  tag: string
  color: string
  summary: string
  sources: SourceLink[]
  sequence: number
}

const nodeTitleMap: Record<string, string> = {
  coordinator: 'Coordinator',
  rewrite_multi_query: 'Query Rewrite',
  background_investigator: 'Background Investigation',
  user_file_rag: 'User File RAG',
  professional_kb_decision: 'Professional KB Decision',
  professional_kb_rag: 'Professional KB RAG',
  planner: 'Planner',
  human_feedback: 'Human Feedback',
  research_team: 'Research Team',
  parallel_executor: 'Parallel Executor',
  information: 'Information Search',
  researcher: 'Researcher',
  coder: 'Coder',
  reporter: 'Reporter',
  runner: 'Runner',
  __END__: 'Done',
}

const router = useRouter()
const route = useRoute()
const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const draft = ref('')
const reportVisible = ref(false)
const scrollRef = ref<HTMLElement | null>(null)
const autoExecutePlan = ref(false)
const feedbackVisible = ref(false)
const feedbackDraft = ref('')
const pendingResumeDecision = ref<'accept' | 'feedback' | ''>('')

const activePlan = computed(() => (messageStore.planWaiting || messageStore.resuming) ? messageStore.plan : null)
const planSteps = computed(() => activePlan.value?.steps || [])
const isInteractionLocked = computed(() => messageStore.running || messageStore.planWaiting || messageStore.resuming)

const workflowNodes = computed(() => {
  const nodes = new Map<string, WorkflowNode>()
  for (const event of messageStore.events) {
    const key = nodeKey(event)
    const previous = nodes.get(key)
    const sources = sourceLinks(event)
    nodes.set(key, {
      key,
      title: displayTitle(event),
      tag: event.event_type || event.phase || event.status || 'message',
      color: eventColor(event),
      summary: eventSummary(event) || previous?.summary || '',
      sources: sources.length ? sources : previous?.sources || [],
      sequence: event.sequence ?? previous?.sequence ?? nodes.size,
    })
  }
  return [...nodes.values()].sort((left, right) => left.sequence - right.sequence)
})

function ensureConversation(query: string) {
  const routeId = route.params.convId as string | undefined
  if (routeId) {
    conversationStore.upsert(routeId)
    conversationStore.activate(routeId)
    return routeId
  }
  const item = conversationStore.newOne(query, 1)
  messageStore.prepareLocalSession(item.key)
  router.replace(`/chat/${item.key}`)
  return item.key
}

async function loadConversation(sessionId: string) {
  conversationStore.activate(sessionId)
  await messageStore.init(sessionId)
}

async function submit() {
  const query = draft.value.trim()
  if (!query || isInteractionLocked.value) {
    return
  }

  const sessionId = ensureConversation(query)
  conversationStore.updateTitle(sessionId, query)
  conversationStore.markLocalMessage(sessionId)
  messageStore.addUserMessage(query)
  messageStore.events = []
  messageStore.clearPlanGate()
  messageStore.running = true
  draft.value = ''
  feedbackVisible.value = false
  feedbackDraft.value = ''
  reportVisible.value = true
  await scrollToBottom()
  let streamError = ''

  try {
    const events = await chatService.stream(
      {
        query,
        session_id: sessionId,
        enable_deepresearch: messageStore.deepResearch,
        auto_accepted_plan: !messageStore.deepResearch || autoExecutePlan.value,
        max_step_num: 3,
      },
      event => {
        messageStore.addEvent(event.data)
        if (event.event === 'error') {
          streamError = errorReason(event.data)
        }
      },
    )

    const final = [...events].reverse().find(event => event.data.done)?.data
    const failed = [...events].reverse().find(event => event.event === 'error')?.data
    if (failed) {
      throw new Error(streamError || errorReason(failed))
    }
    if (!messageStore.planWaiting) {
      const report = extractReport(final) || messageStore.reportContent
      if (report) {
        messageStore.addAssistantMessage(report, messageStore.threadId)
      }
    }
  } catch (err: any) {
    message.error(err.message || '研究流程执行失败')
    messageStore.addAssistantMessage(`研究流程执行失败：${err.message || '未知错误'}`)
    messageStore.clearPlanGate()
  } finally {
    messageStore.running = false
    await scrollToBottom()
    await conversationStore.loadFromBackend()
  }
}

async function resumePlan(accepted: boolean) {
  if (!messageStore.convId || !messageStore.threadId || messageStore.running || messageStore.resuming) {
    return
  }

  const feedback = feedbackDraft.value.trim()
  if (!accepted && !feedback) {
    message.warning('请先填写修改意见')
    return
  }

  messageStore.resuming = true
  messageStore.running = true
  messageStore.planWaiting = false
  pendingResumeDecision.value = accepted ? 'accept' : 'feedback'
  reportVisible.value = true
  await scrollToBottom()
  let streamError = ''

  try {
    const events = await chatService.resume(
      {
        session_id: messageStore.convId,
        thread_id: messageStore.threadId,
        feedback: accepted,
        feedback_content: accepted ? undefined : feedback,
      },
      event => {
        messageStore.addEvent(event.data)
        if (event.event === 'error') {
          streamError = errorReason(event.data)
        }
      },
    )

    const final = [...events].reverse().find(event => event.data.done)?.data
    const failed = [...events].reverse().find(event => event.event === 'error')?.data
    if (failed) {
      throw new Error(streamError || errorReason(failed))
    }
    if (!messageStore.planWaiting) {
      const report = extractReport(final) || messageStore.reportContent
      if (report) {
        messageStore.addAssistantMessage(report, messageStore.threadId)
      }
      feedbackVisible.value = false
      feedbackDraft.value = ''
    }
  } catch (err: any) {
    message.error(err.message || '继续研究失败')
    messageStore.addAssistantMessage(`继续研究失败：${err.message || '未知错误'}`, messageStore.threadId)
    messageStore.clearPlanGate()
  } finally {
    if (messageStore.planWaiting) {
      feedbackVisible.value = false
      feedbackDraft.value = ''
    }
    messageStore.running = false
    messageStore.resuming = false
    pendingResumeDecision.value = ''
    await scrollToBottom()
    await conversationStore.loadFromBackend()
  }
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    submit()
  }
}

async function stop() {
  if (!messageStore.threadId) {
    return
  }
  try {
    await chatService.stop({
      session_id: messageStore.convId,
      thread_id: messageStore.threadId,
    })
    messageStore.running = false
    messageStore.clearPlanGate()
    message.success('已请求停止当前研究')
    await conversationStore.loadFromBackend()
  } catch (err: any) {
    message.error(err.message || '停止失败')
  }
}

function displayTitle(event: ChatStreamResponse) {
  const key = nodeKey(event)
  return nodeTitleMap[key] || event.displayTitle || event.display_title || event.nodeName || event.node_name || '工作流节点'
}

async function retryLoad() {
  const sessionId = route.params.convId as string | undefined
  if (sessionId) {
    await loadConversation(sessionId)
  }
}

function startDraft() {
  conversationStore.startDraft()
  messageStore.reset()
  router.push('/chat')
}

function eventColor(event: ChatStreamResponse) {
  if (event.event_type?.includes('failed') || event.phase === 'error' || event.phase === 'failed') return 'red'
  if (event.event_type === 'human_feedback.waiting' || event.phase === 'waiting') return 'orange'
  if (event.done || event.event_type?.includes('completed')) return 'green'
  if (event.event_type?.includes('started')) return 'blue'
  return 'gray'
}

function nodeKey(event: ChatStreamResponse) {
  const name = event.node_name || event.nodeName || event.node_type || 'workflow'
  if (name === 'researcher' || name === 'coder') {
    return event.executor_id || event.step_id || name
  }
  return name
}

function eventSummary(event: ChatStreamResponse) {
  if (typeof event.content === 'string') {
    return event.content.length > 240 ? `${event.content.slice(0, 240)}...` : event.content
  }
  if (event.event_type?.includes('failed') || event.phase === 'error' || event.phase === 'failed') {
    return errorReason(event)
  }
  if (event.event_type === 'human_feedback.waiting' || event.phase === 'waiting') {
    return '计划已生成，等待确认或修改意见。'
  }
  if (Array.isArray(event.payload)) {
    return arraySummary(event.payload)
  }
  if (event.payload && typeof event.payload === 'object') {
    const payload = event.payload as Record<string, unknown>
    if (payload.title) return String(payload.title)
    if (payload.route) return `路由：${payload.route}`
    if (payload.next_route) return `路由：${payload.next_route}`
    if (payload.nextRoute) return `路由：${payload.nextRoute}`
    if (payload.completedSteps != null && payload.totalSteps != null) {
      return `已完成 ${payload.completedSteps}/${payload.totalSteps} 个步骤。`
    }
    if (payload.step && typeof payload.step === 'object') {
      const step = payload.step as Record<string, unknown>
      if (step.title) return String(step.title)
    }
    if (payload.reason) return String(payload.reason)
  }
  if (event.phase === 'completed' && event.payload) {
    if (Array.isArray(event.payload)) {
      return event.payload.length ? `完成，返回 ${event.payload.length} 条结果。` : '完成，未返回结果。'
    }
    if (typeof event.payload === 'object') {
      const payload = event.payload as Record<string, unknown>
      if (payload.title) return String(payload.title)
      if (payload.route) return `路由：${payload.route}`
      if (payload.next_route) return `路由：${payload.next_route}`
    }
  }
  if (event.done) {
    return '研究流程完成。'
  }
  if (event.phase === 'started') {
    return '节点已开始执行。'
  }
  return ''
}

function arraySummary(items: unknown[]) {
  if (items.length === 0) {
    return '未命中可用结果。'
  }
  if (items.every(item => typeof item === 'string' || typeof item === 'number')) {
    return `命中：${items.join('、')}`
  }
  return `完成，返回 ${items.length} 条结果。`
}

function sourceLinks(event: ChatStreamResponse): SourceLink[] {
  const data = event.siteInformation || event.site_information
  if (!Array.isArray(data)) {
    return []
  }
  return data
    .flatMap(item => (Array.isArray(item) ? item : [item]))
    .filter((item): item is SourceLink => Boolean(item && typeof item === 'object'))
    .slice(0, 6)
}

function extractReport(event?: ChatStreamResponse) {
  if (!event) return ''
  const content = event.content
  if (typeof content === 'string') return content
  if (content && typeof content === 'object' && 'output' in content) {
    return String((content as { output?: unknown }).output || '')
  }
  if (typeof event.payload === 'string') return event.payload
  return ''
}

function errorReason(event: ChatStreamResponse) {
  const content = event.content
  if (content && typeof content === 'object' && 'reason' in content) {
    return String((content as { reason?: unknown }).reason || '后端处理出错')
  }
  if (typeof content === 'string') {
    return content
  }
  return '后端处理出错'
}

async function scrollToBottom() {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  }
}

watch(
  () => route.params.convId,
  async value => {
    const sessionId = value as string | undefined
    if (sessionId) {
      if (messageStore.running && messageStore.convId === sessionId) {
        conversationStore.activate(sessionId)
        return
      }
      await loadConversation(sessionId)
    } else {
      conversationStore.startDraft()
      messageStore.reset()
    }
  },
  { immediate: true },
)

watch(
  () => [messageStore.messages.length, messageStore.events.length],
  () => scrollToBottom(),
)

onMounted(() => {
  if (!route.params.convId) {
    conversationStore.startDraft()
  }
})
</script>

<style lang="less" scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 56px);
  min-width: 0;
}

.chat-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e8edf4;
  display: flex;
  justify-content: space-between;
  padding: 18px 24px;
}

.chat-header h1 {
  font-size: 22px;
  margin: 2px 0 0;
}

.eyebrow {
  color: #738096;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.message-area {
  flex: 1;
  overflow: auto;
  padding: 24px;
}

.welcome {
  align-items: center;
  color: #5d6879;
  display: flex;
  flex-direction: column;
  height: 100%;
  justify-content: center;
  text-align: center;
}

.welcome h2 {
  color: #1b2638;
  font-size: 26px;
  margin-bottom: 8px;
}

.message {
  display: flex;
  gap: 12px;
  margin: 0 auto 16px;
  max-width: 880px;
}

.message.user {
  flex-direction: row-reverse;
}

.avatar {
  align-items: center;
  background: #2356f6;
  border-radius: 8px;
  color: #fff;
  display: flex;
  flex: 0 0 34px;
  font-size: 13px;
  font-weight: 700;
  height: 34px;
  justify-content: center;
}

.message.assistant .avatar {
  background: #152033;
}

.bubble {
  background: #fff;
  border: 1px solid #e7ecf4;
  border-radius: 8px;
  max-width: min(760px, 100%);
  padding: 12px 14px;
}

.message.user .bubble {
  background: #2356f6;
  border-color: #2356f6;
  color: #fff;
}

.bubble p {
  margin: 0;
  white-space: pre-wrap;
}

.event-card {
  background: #fff;
  border: 1px solid #e4ebf5;
  border-radius: 8px;
  margin: 0 auto 18px;
  max-width: 880px;
  padding: 16px 18px 2px;
}

.event-card-header {
  align-items: center;
  display: flex;
  font-weight: 700;
  justify-content: space-between;
  margin-bottom: 16px;
}

.event-line {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.event-summary {
  color: #607086;
  margin: 4px 0 0;
  white-space: pre-wrap;
}

.sources {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.sources a {
  background: #f1f5ff;
  border-radius: 6px;
  color: #2356f6;
  font-size: 12px;
  max-width: 240px;
  overflow: hidden;
  padding: 4px 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-review {
  background: #fff;
  border: 1px solid #f2c37b;
  border-radius: 8px;
  margin: 0 auto 18px;
  max-width: 880px;
  padding: 18px;
}

.plan-review-header {
  align-items: flex-start;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 12px;
}

.plan-label {
  color: #a15c05;
  display: block;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 4px;
}

.plan-review h2 {
  color: #1b2638;
  font-size: 20px;
  line-height: 1.35;
  margin: 0;
}

.plan-thought {
  color: #55657a;
  margin: 0 0 14px;
  white-space: pre-wrap;
}

.plan-steps {
  counter-reset: plan-step;
  display: flex;
  flex-direction: column;
  gap: 10px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.plan-steps li {
  border: 1px solid #edf1f7;
  border-radius: 8px;
  padding: 12px 12px 10px 44px;
  position: relative;
}

.plan-steps li::before {
  align-items: center;
  background: #fff6e8;
  border: 1px solid #f3c27a;
  border-radius: 8px;
  color: #9b5a00;
  content: counter(plan-step);
  counter-increment: plan-step;
  display: flex;
  font-weight: 700;
  height: 24px;
  justify-content: center;
  left: 12px;
  position: absolute;
  top: 12px;
  width: 24px;
}

.step-title {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.plan-steps p {
  color: #637087;
  margin: 6px 0 0;
  white-space: pre-wrap;
}

.plan-feedback {
  margin-top: 14px;
}

.plan-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 14px;
}

.composer {
  background: #fff;
  border-top: 1px solid #e8edf4;
  padding: 14px 24px 18px;
}

.composer-options {
  align-items: center;
  color: #5d6879;
  display: flex;
  font-size: 13px;
  gap: 8px;
  margin-bottom: 10px;
}

.thread {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 10px;
}

@media (max-width: 980px) {
  .chat-page {
    flex-direction: column;
  }

  .chat-main {
    min-height: 50%;
  }
}
</style>
