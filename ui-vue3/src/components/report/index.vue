<template>
  <aside class="report-panel" :class="{ open: visible }">
    <div class="report-header">
      <div>
        <div class="eyebrow">Research Report</div>
        <h2>研究报告</h2>
      </div>
      <a-space>
        <a-button size="small" :disabled="!threadId" :loading="loading" @click="loadPersistedReport">
          <ReloadOutlined />
        </a-button>
        <a-button size="small" @click="$emit('close')">
          <CloseOutlined />
        </a-button>
      </a-space>
    </div>

    <div class="report-body">
      <a-alert
        v-if="error"
        type="warning"
        show-icon
        :message="error"
        style="margin-bottom: 12px"
      />

      <MD v-if="content" :content="content" />

      <a-empty
        v-else
        :description="threadId ? '报告尚未生成，研究完成后会自动显示。' : '发送问题后会在这里显示研究过程和报告。'"
      />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CloseOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { reportService } from '@/services'
import { useMessageStore } from '@/store/MessageStore'
import { isNotFoundError, userMessageFromError } from '@/utils/errors'
import MD from '@/components/md/index.vue'

const props = defineProps<{
  visible: boolean
  threadId: string
}>()

defineEmits<{
  (event: 'close'): void
}>()

const messageStore = useMessageStore()
const persistedReport = ref('')
const loading = ref(false)
const error = ref('')

const content = computed(() => messageStore.reportContent || persistedReport.value)

async function loadPersistedReport() {
  if (!props.threadId) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await reportService.getReport(props.threadId)
    persistedReport.value = typeof result === 'string' ? result : result.report
  } catch (err: any) {
    if (isNotFoundError(err)) {
      persistedReport.value = ''
      error.value = '报告不存在或尚未生成。'
      return
    }
    error.value = userMessageFromError(err, '暂时没有可读取的报告。')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.threadId,
  () => {
    persistedReport.value = ''
    error.value = ''
    if (props.visible && props.threadId && !messageStore.reportContent) {
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
  justify-content: space-between;
  padding: 18px 20px;
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

.report-body {
  flex: 1;
  overflow: auto;
  padding: 20px;
}

@media (max-width: 980px) {
  .report-panel {
    border-left: 0;
    border-top: 1px solid #e7ebf2;
    flex-basis: 50%;
    min-width: 0;
  }
}
</style>
