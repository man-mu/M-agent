<template>
  <aside class="report-panel" :class="{ open: visible }">
    <div class="report-header">
      <div>
        <div class="eyebrow">Research Report</div>
        <h2>研究报告</h2>
      </div>
      <a-space>
        <a-tag :color="statusView.color">{{ statusView.label }}</a-tag>
        <a-tooltip title="复制报告">
          <a-button size="small" :disabled="!content" @click="copyReport">
            <CopyOutlined />
          </a-button>
        </a-tooltip>
        <a-tooltip title="刷新报告">
          <a-button size="small" :disabled="!threadId" :loading="loading" @click="loadPersistedReport">
            <ReloadOutlined />
          </a-button>
        </a-tooltip>
        <a-tooltip title="关闭报告">
          <a-button size="small" @click="$emit('close')">
            <CloseOutlined />
          </a-button>
        </a-tooltip>
      </a-space>
    </div>

    <div class="report-meta">
      <span>来源：{{ sourceLabel }}</span>
      <span v-if="updatedAt">更新：{{ updatedAt }}</span>
    </div>

    <div class="report-body">
      <a-alert
        v-if="error && !notGenerated"
        type="warning"
        show-icon
        :message="error"
        style="margin-bottom: 12px"
      />

      <nav v-if="toc.length" class="report-toc" aria-label="报告目录">
        <span class="section-title">目录</span>
        <button
          v-for="item in toc"
          :key="item.key"
          :class="`level-${item.level}`"
          type="button"
          @click="scrollToHeading(item.title)"
        >
          {{ item.title }}
        </button>
      </nav>

      <Suspense v-if="content">
        <MD :content="content" />
        <template #fallback>
          <p class="report-loading">报告渲染中...</p>
        </template>
      </Suspense>

      <a-empty
        v-else
        :description="statusView.emptyDescription"
      />

      <section class="source-section">
        <span class="section-title">来源链接</span>
        <div v-if="sources.length" class="source-list">
          <a
            v-for="source in sources"
            :key="source.url"
            :href="source.url"
            target="_blank"
            rel="noopener noreferrer"
          >
            <span>{{ source.title }}</span>
            <small>{{ source.host }}</small>
          </a>
        </div>
        <p v-else class="source-empty">本次后端未返回来源链接。</p>
      </section>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { CloseOutlined, CopyOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import { reportService } from '@/services'
import type { ChatStreamResponse } from '@/services/api/chat'
import { isNotFoundError, userMessageFromError } from '@/utils/errors'
import { deriveReportSources, deriveReportToc, reportStatusView } from './helpers'

const MD = defineAsyncComponent(() => import('@/components/md/index.vue'))

const props = withDefaults(defineProps<{
  visible: boolean
  threadId: string
  liveContent?: string
  events?: ChatStreamResponse[]
}>(), {
  liveContent: '',
  events: () => [],
})

defineEmits<{
  (event: 'close'): void
}>()

const persistedReport = ref('')
const persistedUpdatedAt = ref('')
const loading = ref(false)
const error = ref('')
const notGenerated = ref(false)
const lastLoadedFromBackend = ref(false)

const content = computed(() => props.liveContent || persistedReport.value)
const toc = computed(() => deriveReportToc(content.value))
const sources = computed(() => deriveReportSources(props.events))
const updatedAt = computed(() => persistedUpdatedAt.value ? formatDateTime(persistedUpdatedAt.value) : '')
const statusView = computed(() => reportStatusView({
  hasThread: Boolean(props.threadId),
  hasContent: Boolean(content.value),
  loading: loading.value,
  notGenerated: notGenerated.value,
  error: error.value,
}))
const sourceLabel = computed(() => {
  if (props.liveContent) {
    return '当前流式结果'
  }
  if (lastLoadedFromBackend.value && persistedReport.value) {
    return '后端持久化报告'
  }
  return '等待后端报告'
})

async function loadPersistedReport() {
  if (!props.threadId) {
    return
  }
  loading.value = true
  error.value = ''
  notGenerated.value = false
  try {
    const result = await reportService.getReport(props.threadId)
    if (typeof result === 'string') {
      persistedReport.value = result
      persistedUpdatedAt.value = ''
    } else {
      persistedReport.value = result.report || ''
      persistedUpdatedAt.value = result.updated_at || result.created_at || ''
    }
    lastLoadedFromBackend.value = Boolean(persistedReport.value)
  } catch (err: any) {
    if (isNotFoundError(err)) {
      persistedReport.value = ''
      persistedUpdatedAt.value = ''
      lastLoadedFromBackend.value = false
      notGenerated.value = true
      return
    }
    error.value = userMessageFromError(err, '暂时没有可读取的报告。')
  } finally {
    loading.value = false
  }
}

async function copyReport() {
  if (!content.value) {
    return
  }
  if (await writeClipboardText(content.value)) {
    message.success('报告正文已复制')
  } else {
    message.error('复制失败，请手动选择报告正文')
  }
}

async function writeClipboardText(value: string) {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(value)
      return true
    } catch {
      return copyWithHiddenTextarea(value)
    }
  }
  return copyWithHiddenTextarea(value)
}

function copyWithHiddenTextarea(value: string) {
  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()
  try {
    return document.execCommand('copy')
  } finally {
    document.body.removeChild(textarea)
  }
}

function scrollToHeading(title: string) {
  const headings = [...document.querySelectorAll<HTMLElement>('.report-body h1, .report-body h2, .report-body h3')]
  const heading = headings.find(item => item.textContent?.trim() === title)
  heading?.scrollIntoView({ block: 'start', behavior: 'smooth' })
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

watch(
  () => props.threadId,
  () => {
    persistedReport.value = ''
    persistedUpdatedAt.value = ''
    error.value = ''
    notGenerated.value = false
    lastLoadedFromBackend.value = false
    if (props.visible && props.threadId && !props.liveContent) {
      loadPersistedReport()
    }
  },
)

watch(
  () => props.visible,
  visible => {
    if (visible && props.threadId && !content.value) {
      loadPersistedReport()
    }
  },
)
</script>

<style lang="less" scoped>
.report-panel {
  background: #fff;
  border-left: 1px solid #e7ebf2;
  display: none;
  flex: 0 0 46%;
  min-width: 420px;
  overflow: hidden;
}

.report-panel.open {
  display: flex;
  flex-direction: column;
}

.report-header {
  align-items: center;
  border-bottom: 1px solid #edf0f5;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 18px 20px 12px;
}

.report-header h2 {
  font-size: 18px;
  margin: 2px 0 0;
}

.eyebrow {
  color: #738096;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.report-meta {
  align-items: center;
  background: #f8fbff;
  border-bottom: 1px solid #edf0f5;
  color: #667085;
  display: flex;
  flex-wrap: wrap;
  font-size: 12px;
  gap: 10px;
  padding: 8px 20px;
}

.report-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 18px 20px 20px;
}

.report-loading {
  color: #6b7688;
  margin: 0;
}

.report-toc,
.source-section {
  background: #fbfdff;
  border: 1px solid #e5ecf6;
  border-radius: 8px;
  margin-bottom: 16px;
  padding: 12px;
}

.section-title {
  color: #45546b;
  display: block;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 8px;
}

.report-toc button {
  background: transparent;
  border: 0;
  color: #2356f6;
  cursor: pointer;
  display: block;
  font: inherit;
  max-width: 100%;
  overflow: hidden;
  padding: 4px 0;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-toc .level-2 {
  padding-left: 12px;
}

.report-toc .level-3 {
  padding-left: 24px;
}

.source-section {
  margin: 18px 0 0;
}

.source-list {
  display: grid;
  gap: 8px;
}

.source-list a {
  background: #fff;
  border: 1px solid #e2e9f3;
  border-radius: 8px;
  color: #1d2b42;
  display: grid;
  gap: 2px;
  min-width: 0;
  padding: 8px 10px;
}

.source-list span,
.source-list small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-list small,
.source-empty {
  color: #667085;
  font-size: 12px;
}

.source-empty {
  margin: 0;
}

@media (max-width: 980px) {
  .report-panel {
    border-left: 0;
    border-top: 1px solid #e7ebf2;
    flex-basis: 50%;
    min-width: 0;
  }
}

@media (max-width: 640px) {
  .report-panel {
    flex: 1 1 auto;
    max-height: 52vh;
    width: 100%;
  }

  .report-header {
    align-items: flex-start;
    padding: 14px 12px 10px;
  }

  .report-header :deep(.ant-space) {
    flex-wrap: wrap;
    justify-content: flex-end;
    row-gap: 6px;
  }

  .report-meta {
    padding: 8px 12px;
  }

  .report-body {
    padding: 14px 12px 16px;
  }

  .report-toc button {
    white-space: normal;
  }
}
</style>
