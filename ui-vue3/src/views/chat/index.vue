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
        <div v-if="messageStore.messages.length === 0 && messageStore.events.length === 0" class="welcome">
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

        <div v-if="messageStore.events.length" class="event-card">
          <div class="event-card-header">
            <span>工作流进度</span>
            <a-tag v-if="messageStore.running" color="processing">运行中</a-tag>
            <a-tag v-else color="default">已结束</a-tag>
          </div>

          <a-timeline>
            <a-timeline-item
              v-for="event in visibleEvents"
              :key="`${event.sequence}-${event.event_type}-${event.phase}`"
              :color="eventColor(event)"
            >
              <div class="event-line">
                <strong>{{ displayTitle(event) }}</strong>
                <a-tag>{{ event.event_type || event.phase || 'message' }}</a-tag>
              </div>
              <p v-if="eventSummary(event)" class="event-summary">{{ eventSummary(event) }}</p>
              <div v-if="sourceLinks(event).length" class="sources">
                <a
                  v-for="source in sourceLinks(event)"
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
          <a-divider type="vertical" />
          <span class="thread" v-if="messageStore.threadId">Thread: {{ messageStore.threadId }}</span>
        </div>
        <a-textarea
          v-model:value="draft"
          :auto-size="{ minRows: 2, maxRows: 5 }"
          :disabled="messageStore.running"
          placeholder="输入你的研究问题..."
          @keydown="handleComposerKeydown"
        />
        <div class="composer-actions">
          <a-button v-if="messageStore.running" danger @click="stop">
            <PauseCircleOutlined />
            停止
          </a-button>
          <a-button type="primary" :disabled="!draft.trim() || messageStore.running" @click="submit">
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

const router = useRouter()
const route = useRoute()
const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const draft = ref('')
const reportVisible = ref(false)
const scrollRef = ref<HTMLElement | null>(null)

const visibleEvents = computed(() => messageStore.events.slice(-80))

function ensureConversation() {
  const routeId = route.params.convId as string | undefined
  if (routeId) {
    conversationStore.upsert(routeId)
    conversationStore.activate(routeId)
    return routeId
  }
  const item = conversationStore.newOne()
  router.replace(`/chat/${item.key}`)
  return item.key
}

async function loadConversation(sessionId: string) {
  conversationStore.activate(sessionId)
  await messageStore.init(sessionId)
}

async function submit() {
  const query = draft.value.trim()
  if (!query || messageStore.running) {
    return
  }

  const sessionId = ensureConversation()
  conversationStore.updateTitle(sessionId, query)
  messageStore.addUserMessage(query)
  messageStore.events = []
  messageStore.running = true
  draft.value = ''
  reportVisible.value = true
  await scrollToBottom()

  try {
    const events = await chatService.stream(
      {
        query,
        session_id: sessionId,
        enable_deepresearch: messageStore.deepResearch,
        auto_accepted_plan: true,
        max_step_num: 3,
      },
      event => {
        messageStore.addEvent(event.data)
        if (event.event === 'error') {
          throw new Error(errorReason(event.data))
        }
      },
    )

    const final = [...events].reverse().find(event => event.data.done)?.data
    const report = extractReport(final) || messageStore.reportContent
    if (report) {
      messageStore.addAssistantMessage(report, messageStore.threadId)
    }
  } catch (err: any) {
    message.error(err.message || '研究流程执行失败')
    messageStore.addAssistantMessage(`研究流程执行失败：${err.message || '未知错误'}`)
  } finally {
    messageStore.running = false
    await scrollToBottom()
    conversationStore.loadFromBackend()
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
    message.success('已请求停止当前研究')
  } catch (err: any) {
    message.error(err.message || '停止失败')
  }
}

function displayTitle(event: ChatStreamResponse) {
  return event.displayTitle || event.display_title || event.nodeName || event.node_name || '工作流节点'
}

function eventColor(event: ChatStreamResponse) {
  if (event.event_type?.includes('failed') || event.phase === 'error') return 'red'
  if (event.done || event.event_type?.includes('completed')) return 'green'
  if (event.event_type?.includes('started')) return 'blue'
  return 'gray'
}

function eventSummary(event: ChatStreamResponse) {
  if (typeof event.content === 'string') {
    return event.content.length > 240 ? `${event.content.slice(0, 240)}...` : event.content
  }
  if (event.phase === 'completed' && event.payload) {
    if (Array.isArray(event.payload)) {
      return `完成，返回 ${event.payload.length} 条结果。`
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
  return ''
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
      await loadConversation(sessionId)
    } else {
      messageStore.reset()
    }
  },
  { immediate: true },
)

watch(
  () => [messageStore.messages.length, messageStore.events.length],
  () => scrollToBottom(),
)

onMounted(async () => {
  if (!route.params.convId) {
    ensureConversation()
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
