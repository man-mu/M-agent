<template>
  <div class="chat-page">
    <section class="chat-main" data-testid="chat-main">
      <div class="chat-header">
        <div>
          <div class="eyebrow">Research Workbench</div>
          <h1>研究工作台</h1>
        </div>
        <a-space>
          <a-tooltip title="模型设置">
            <a-button @click="router.push('/settings')">
              <SettingOutlined />
            </a-button>
          </a-tooltip>
          <a-button
            :disabled="!messageStore.threadId"
            data-testid="toggle-report"
            @click="reportVisible = !reportVisible"
          >
            <FileTextOutlined />
            报告
            <a-tag v-if="currentConversation?.reportAvailable" color="green">可用</a-tag>
          </a-button>
        </a-space>
      </div>

      <div class="workbench-status" data-testid="research-status-bar">
        <div class="status-item" data-testid="mode-status">
          <span class="status-label">模式</span>
          <strong>{{ modeLabel }}</strong>
        </div>
        <div class="status-item" data-testid="plan-gate-status">
          <span class="status-label">计划</span>
          <strong>{{ planGateLabel }}</strong>
        </div>
        <button
          class="status-item status-link"
          data-testid="current-model-status"
          type="button"
          @click="router.push('/settings')"
        >
          <span class="status-label">模型</span>
          <strong>{{ currentModelText }}</strong>
          <a-tag v-if="currentModel" :color="currentModel.apiKeyConfigured ? 'green' : 'red'">
            {{ currentModel.apiKeyConfigured ? '可用' : '未配置' }}
          </a-tag>
          <a-tag v-else :color="modelLoadError ? 'orange' : 'default'">
            {{ modelLoadError ? '读取失败' : '读取中' }}
          </a-tag>
        </button>
        <div class="status-item session-status" :class="sessionStatus.kind" data-testid="session-status">
          <span class="status-label">会话</span>
          <strong>{{ sessionStatus.label }}</strong>
        </div>
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

        <div
          v-else-if="messageStore.messages.length === 0 && messageStore.events.length === 0"
          class="welcome"
          data-testid="chat-empty-state"
        >
          <h2>输入研究问题，启动真实模型流程。</h2>
          <p>进度、来源和报告只来自后端返回的数据。</p>
        </div>

        <article
          v-for="item in messageStore.messages"
          :key="item.id"
          class="message"
          :class="item.role"
        >
          <div class="avatar">{{ item.role === 'user' ? '你' : 'AI' }}</div>
          <div class="bubble">
            <Suspense v-if="item.role === 'assistant'">
              <MD :content="item.content" />
              <template #fallback>
                <p>{{ item.content }}</p>
              </template>
            </Suspense>
            <p v-else>{{ item.content }}</p>
          </div>
        </article>

        <PlanReview
          v-if="activePlan"
          v-model:feedback="feedbackDraft"
          :feedback-visible="feedbackVisible"
          :pending-action="pendingResumeDecision"
          :plan="activePlan"
          :resuming="messageStore.resuming"
          :running="messageStore.running"
          @accept="resumePlan(true)"
          @submit-feedback="resumePlan(false)"
          @toggle-feedback="toggleFeedback"
        />

        <div v-if="messageStore.events.length" class="event-card" data-testid="workflow-progress">
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
              <div class="workflow-node" :class="node.status">
                <div class="event-line">
                  <strong>{{ node.title }}</strong>
                  <a-tag :color="node.color">{{ node.statusLabel }}</a-tag>
                  <span v-if="node.eventType || node.phase" class="event-kind">
                    {{ node.eventType || node.phase }}
                  </span>
                  <span v-if="node.sequence" class="event-meta">#{{ node.sequence }}</span>
                  <span v-if="node.timestamp" class="event-meta">{{ formatEventTime(node.timestamp) }}</span>
                </div>
                <p v-if="node.summary" class="event-summary">{{ node.summary }}</p>
                <div v-if="node.sources.length" class="sources">
                  <template
                    v-for="source in node.sources"
                    :key="source.url || source.title"
                  >
                    <a
                      v-if="source.url"
                      :href="source.url"
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      {{ source.title || source.url }}
                    </a>
                    <span v-else class="source-label">{{ source.title }}</span>
                  </template>
                </div>
              </div>
            </a-timeline-item>
          </a-timeline>
        </div>
      </div>

      <div class="composer">
        <div class="composer-options">
          <a-switch
            v-model:checked="messageStore.deepResearch"
            :disabled="isInteractionLocked"
            data-testid="deep-research-switch"
          />
          <span>{{ messageStore.deepResearch ? '深度研究' : '快速回答' }}</span>
          <template v-if="messageStore.deepResearch">
            <a-divider type="vertical" />
            <a-switch
              v-model:checked="autoExecutePlan"
              :disabled="isInteractionLocked"
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
            :disabled="!canStop"
            danger
            data-testid="stop-research"
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

    <Suspense v-if="reportVisible || messageStore.threadId">
      <Report
        :events="messageStore.events"
        :live-content="messageStore.reportContent"
        :visible="reportVisible"
        :thread-id="messageStore.threadId"
        @close="reportVisible = false"
      />
      <template #fallback>
        <aside v-if="reportVisible" class="report-placeholder">报告加载中...</aside>
      </template>
    </Suspense>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  FileTextOutlined,
  PauseCircleOutlined,
  SendOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import { chatService } from '@/services'
import type { ChatStreamResponse } from '@/services/api/chat'
import modelService from '@/services/api/model'
import type { CurrentModelSelection } from '@/services/api/model'
import { useConversationStore } from '@/store/ConversationStore'
import { useMessageStore } from '@/store/MessageStore'
import { isAbortError, streamEventErrorMessage, userMessageFromError } from '@/utils/errors'

const Report = defineAsyncComponent(() => import('@/components/report/index.vue'))
const MD = defineAsyncComponent(() => import('@/components/md/index.vue'))
const PlanReview = defineAsyncComponent(() => import('@/components/plan-review/index.vue'))

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
const activeStreamController = ref<AbortController | null>(null)
const currentModel = ref<CurrentModelSelection | null>(null)
const modelLoadError = ref('')
const localTerminalStatus = ref<'completed' | 'failed' | 'stopped' | ''>('')

const activePlan = computed(() => (messageStore.planWaiting || messageStore.resuming) ? messageStore.plan : null)
const isInteractionLocked = computed(() => messageStore.running || messageStore.planWaiting || messageStore.resuming)
const canStop = computed(() => Boolean(messageStore.threadId || activeStreamController.value))
const modeLabel = computed(() => messageStore.deepResearch ? '深度研究' : '快速回答')
const planGateLabel = computed(() => {
  if (!messageStore.deepResearch) {
    return '无需计划'
  }
  if (messageStore.planWaiting) {
    return '等待确认'
  }
  return autoExecutePlan.value ? '自动执行' : '先审阅计划'
})
const currentModelText = computed(() => {
  if (currentModel.value) {
    return `${currentModel.value.providerName || currentModel.value.providerId} / ${currentModel.value.modelName}`
  }
  return modelLoadError.value ? '模型状态不可用' : '读取模型状态'
})
const eventTerminalStatus = computed(() => {
  for (const event of [...messageStore.events].reverse()) {
    const kind = terminalEventKind(event)
    if (kind) {
      return kind
    }
  }
  return ''
})
const sessionStatus = computed(() => {
  if (messageStore.loading) return { kind: 'loading', label: '加载中' }
  if (messageStore.lastError) return { kind: 'failed', label: '失败' }
  if (messageStore.resuming) return { kind: 'running', label: '恢复中' }
  if (messageStore.planWaiting) return { kind: 'waiting', label: '等待计划确认' }
  if (messageStore.running) return { kind: 'running', label: '运行中' }
  if (localTerminalStatus.value) return statusView(localTerminalStatus.value)
  if (eventTerminalStatus.value) return statusView(eventTerminalStatus.value)
  if (messageStore.messages.some(item => item.role === 'assistant')) return { kind: 'completed', label: '已完成' }
  if (messageStore.messages.length || messageStore.threadId) return { kind: 'saved', label: '已保存' }
  return { kind: 'draft', label: '草稿' }
})

const workflowNodes = computed(() => messageStore.workflowNodes)
const currentConversation = computed(() =>
  conversationStore.conversations.find(item => item.key === messageStore.convId || item.key === route.params.convId),
)

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
  await conversationStore.refreshConversationState(sessionId, messageStore.threadId)
}

async function submit() {
  const query = draft.value.trim()
  if (!query || isInteractionLocked.value) {
    return
  }

  localTerminalStatus.value = ''
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
  const controller = createStreamController()

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
      },
      controller.signal,
    )

    const final = [...events].reverse().find(event => event.data.done)?.data
    const failed = [...events].reverse().find(event => event.event === 'error')?.data
    if (failed) {
      throw new Error(streamEventErrorMessage(failed))
    }
    if (!messageStore.planWaiting) {
      const report = extractReport(final) || messageStore.reportContent
      if (report) {
        messageStore.addAssistantMessage(report, messageStore.threadId)
      }
      localTerminalStatus.value = 'completed'
    }
  } catch (err: any) {
    if (!isAbortError(err)) {
      const errorMessage = userMessageFromError(err, '研究流程执行失败')
      message.error(errorMessage)
      messageStore.addAssistantMessage(`研究流程执行失败：${errorMessage}`)
      reportVisible.value = false
      localTerminalStatus.value = 'failed'
    }
    messageStore.clearPlanGate()
  } finally {
    if (activeStreamController.value === controller) {
      activeStreamController.value = null
    }
    messageStore.running = false
    await scrollToBottom()
    await conversationStore.loadFromBackend()
    await conversationStore.refreshConversationState(sessionId, messageStore.threadId)
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
  localTerminalStatus.value = ''
  pendingResumeDecision.value = accepted ? 'accept' : 'feedback'
  reportVisible.value = true
  await scrollToBottom()
  const controller = createStreamController()

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
      },
      controller.signal,
    )

    const final = [...events].reverse().find(event => event.data.done)?.data
    const failed = [...events].reverse().find(event => event.event === 'error')?.data
    if (failed) {
      throw new Error(streamEventErrorMessage(failed))
    }
    if (!messageStore.planWaiting) {
      const report = extractReport(final) || messageStore.reportContent
      if (report) {
        messageStore.addAssistantMessage(report, messageStore.threadId)
      }
      feedbackVisible.value = false
      feedbackDraft.value = ''
      localTerminalStatus.value = 'completed'
    }
  } catch (err: any) {
    if (!isAbortError(err)) {
      const errorMessage = userMessageFromError(err, '继续研究失败')
      message.error(errorMessage)
      messageStore.addAssistantMessage(`继续研究失败：${errorMessage}`, messageStore.threadId)
      reportVisible.value = false
      localTerminalStatus.value = 'failed'
    }
    messageStore.clearPlanGate()
  } finally {
    if (activeStreamController.value === controller) {
      activeStreamController.value = null
    }
    if (messageStore.planWaiting) {
      feedbackVisible.value = false
      feedbackDraft.value = ''
    }
    messageStore.running = false
    messageStore.resuming = false
    pendingResumeDecision.value = ''
    await scrollToBottom()
    await conversationStore.loadFromBackend()
    await conversationStore.refreshConversationState(messageStore.convId, messageStore.threadId)
  }
}

function toggleFeedback() {
  if (messageStore.running || messageStore.resuming) {
    return
  }
  feedbackVisible.value = !feedbackVisible.value
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    submit()
  }
}

async function stop() {
  activeStreamController.value?.abort()
  activeStreamController.value = null
  messageStore.running = false
  messageStore.resuming = false
  messageStore.clearPlanGate()
  localTerminalStatus.value = 'stopped'

  if (!messageStore.threadId) {
    message.success('已停止当前请求')
    return
  }
  try {
    await chatService.stop({
      session_id: messageStore.convId,
      thread_id: messageStore.threadId,
    })
    message.success('已请求停止当前研究')
    await conversationStore.loadFromBackend()
    await conversationStore.refreshConversationState(messageStore.convId, messageStore.threadId)
  } catch (err: any) {
    message.error(userMessageFromError(err, '停止失败'))
  }
}

async function retryLoad() {
  const sessionId = route.params.convId as string | undefined
  if (sessionId) {
    await loadConversation(sessionId)
  }
}

function startDraft() {
  localTerminalStatus.value = ''
  conversationStore.startDraft()
  messageStore.reset()
  router.push('/chat')
}

async function loadCurrentModel() {
  modelLoadError.value = ''
  try {
    currentModel.value = await modelService.getCurrent()
  } catch (error) {
    currentModel.value = null
    modelLoadError.value = userMessageFromError(error, '模型状态读取失败')
  }
}

function terminalEventKind(event: ChatStreamResponse): 'completed' | 'failed' | 'stopped' | '' {
  const type = event.event_type || ''
  if (type === 'graph.failed' || event.phase === 'failed' || event.phase === 'error') return 'failed'
  if (type === 'graph.stopped' || event.phase === 'stopped') return 'stopped'
  if (event.done || type === 'graph.completed') return 'completed'
  return ''
}

function statusView(kind: 'completed' | 'failed' | 'stopped') {
  if (kind === 'failed') return { kind: 'failed', label: '失败' }
  if (kind === 'stopped') return { kind: 'stopped', label: '已停止' }
  return { kind: 'completed', label: '已完成' }
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

function formatEventTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function createStreamController() {
  activeStreamController.value?.abort()
  const controller = new AbortController()
  activeStreamController.value = controller
  return controller
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
    localTerminalStatus.value = ''
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
  loadCurrentModel()
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

.workbench-status {
  align-items: center;
  background: #f8fbff;
  border-bottom: 1px solid #e2e9f3;
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 10px 24px;
}

.status-item {
  align-items: center;
  background: #fff;
  border: 1px solid #e3eaf4;
  border-radius: 8px;
  color: #1d2b42;
  display: flex;
  gap: 8px;
  min-height: 40px;
  min-width: 0;
  padding: 8px 10px;
}

.status-item strong {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-label {
  color: #738096;
  flex: 0 0 auto;
  font-size: 12px;
}

.status-link {
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.status-link:hover {
  border-color: #9db7f8;
}

.session-status {
  border-left: 3px solid #cbd5e1;
}

.session-status.running {
  border-left-color: #2356f6;
}

.session-status.waiting {
  border-left-color: #d98506;
}

.session-status.completed {
  border-left-color: #2f9e44;
}

.session-status.failed {
  border-left-color: #d9363e;
}

.session-status.stopped,
.session-status.saved {
  border-left-color: #667085;
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
  min-width: 0;
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

.workflow-node {
  background: #fbfdff;
  border: 1px solid #e5ecf6;
  border-left: 3px solid #8ca0bd;
  border-radius: 8px;
  min-width: 0;
  padding: 10px 12px;
}

.workflow-node.running {
  border-left-color: #2356f6;
}

.workflow-node.waiting {
  border-left-color: #d98506;
}

.workflow-node.completed {
  border-left-color: #2f9e44;
}

.workflow-node.failed {
  border-left-color: #d9363e;
}

.workflow-node.stopped {
  border-left-color: #667085;
}

.event-kind,
.event-meta {
  color: #7a8798;
  font-size: 12px;
}

.event-kind {
  background: #eef3fa;
  border-radius: 6px;
  max-width: 180px;
  overflow: hidden;
  padding: 2px 6px;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.source-label {
  background: #f3f6fb;
  border-radius: 6px;
  color: #667085;
  font-size: 12px;
  max-width: 240px;
  overflow: hidden;
  padding: 4px 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  min-width: 0;
  overflow: hidden;
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

.report-placeholder {
  align-items: center;
  background: #fff;
  border-left: 1px solid #e7ebf2;
  color: #6b7688;
  display: flex;
  flex: 0 0 46%;
  justify-content: center;
  min-width: 420px;
}

@media (max-width: 980px) {
  .chat-page {
    flex-direction: column;
  }

  .chat-main {
    min-height: 50%;
  }
}

@media (max-width: 640px) {
  .chat-page {
    height: auto;
    min-height: calc(100vh - 112px);
    width: 100%;
  }

  .chat-main {
    flex: 1 1 auto;
    min-height: 560px;
    width: 100%;
  }

  .chat-header {
    align-items: flex-start;
    gap: 12px;
    padding: 14px 16px;
  }

  .workbench-status {
    grid-template-columns: 1fr;
    padding: 10px 12px;
  }

  .chat-header h1 {
    font-size: 20px;
  }

  .message-area {
    padding: 16px 12px;
  }

  .welcome {
    min-height: 260px;
    padding: 24px 12px;
  }

  .welcome h2 {
    font-size: 21px;
  }

  .message {
    gap: 8px;
    margin-bottom: 12px;
    max-width: 100%;
  }

  .avatar {
    flex-basis: 30px;
    height: 30px;
  }

  .bubble {
    padding: 10px 12px;
  }

  .event-card {
    margin-bottom: 14px;
    padding: 14px 12px 2px;
  }

  .workflow-node {
    padding: 10px;
  }

  .event-kind {
    max-width: 100%;
  }

  .composer-actions {
    justify-content: stretch;
  }

  .composer-actions :deep(.ant-btn) {
    flex: 1;
  }

  .composer {
    padding: 12px;
  }

  .composer-options {
    flex-wrap: wrap;
  }

  .thread {
    max-width: 100%;
  }
}
</style>
