<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import {
  DeleteOutlined,
  MessageOutlined,
  PlusOutlined,
  SettingOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import appService from '@/services/api/app'
import { disabledCapabilities, type AppCapabilities } from '@/services/api/app'
import { useConversationStore } from '@/store/ConversationStore'
import type { ConversationItem, ConversationStatusKind } from '@/store/ConversationStore'
import { useMessageStore } from '@/store/MessageStore'
import { userMessageFromError } from '@/utils/errors'

const router = useRouter()
const route = useRoute()
const conversationStore = useConversationStore()
const messageStore = useMessageStore()
const capabilities = ref<AppCapabilities>(disabledCapabilities)

const currentMode = computed(() => {
  if (route.path.startsWith('/skills')) return 'skills'
  if (route.path.startsWith('/settings')) return 'settings'
  return 'chat'
})

const navItems = computed(() => {
  const items = [
    { value: 'chat', label: '对话', icon: MessageOutlined },
    { value: 'settings', label: '模型', icon: SettingOutlined },
  ]
  if (capabilities.value.skillEnabled) {
    items.splice(1, 0, { value: 'skills', label: 'Skills', icon: ToolOutlined })
  }
  return items
})

function switchMode(mode: string) {
  if (mode === 'chat') {
    router.push(conversationStore.curConvKey ? `/chat/${conversationStore.curConvKey}` : '/chat')
  } else if (mode === 'skills' && !capabilities.value.skillEnabled) {
    router.push('/chat')
  } else {
    router.push(`/${mode}`)
  }
}

function handleModeChange(value: string | number) {
  switchMode(String(value))
}

function createConversation() {
  conversationStore.startDraft()
  messageStore.reset()
  router.push('/chat')
}

async function deleteConversation(key: string) {
  await conversationStore.delete(key)
  if (route.params.convId === key) {
    messageStore.reset()
    router.push('/chat')
  }
}

function confirmClearAll() {
  import('ant-design-vue/es/modal').then(({ default: Modal }) => Modal.confirm({
    title: '清空所有会话？',
    content: '这会删除后端已保存的会话消息和本地会话列表。',
    okText: '清空',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await conversationStore.clearAll()
      messageStore.reset()
      message.success('已清空会话')
      router.push('/chat')
    },
  }))
}

async function loadCapabilities() {
  try {
    capabilities.value = await appService.getCapabilities()
  } catch (error) {
    message.warning(userMessageFromError(error, '应用能力信息加载失败，已按可选模块关闭处理。'))
    capabilities.value = disabledCapabilities
  }
}

function statusLabel(status?: ConversationStatusKind) {
  if (status === 'draft') return '本地草稿'
  if (status === 'completed') return '已完成'
  if (status === 'stopped') return '已停止'
  if (status === 'failed') return '失败'
  if (status === 'running') return '运行中'
  if (status === 'waiting') return '等待确认'
  if (status === 'saved') return '已保存'
  return '未知'
}

function statusClass(status?: ConversationStatusKind) {
  return status || 'unknown'
}

function messageCountLabel(item: ConversationItem) {
  return item.messageCount ? `${item.messageCount} 条消息` : '暂无消息'
}

function lastTimeLabel(value?: string) {
  if (!value) {
    return '本地'
  }
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

onMounted(async () => {
  await Promise.all([
    conversationStore.loadFromBackend(),
    loadCapabilities(),
  ])
})
</script>

<template>
  <a-layout class="app-shell">
    <a-layout-header class="topbar">
      <div class="brand" @click="switchMode('chat')">M-Agent</div>
      <a-segmented
        :value="currentMode"
        :options="navItems"
        @change="handleModeChange"
      >
        <template #label="{ payload }">
          <span class="nav-label">
            <component :is="payload.icon" />
            {{ payload.label }}
          </span>
        </template>
      </a-segmented>
    </a-layout-header>

    <a-layout class="main-layout">
      <a-layout-sider v-if="currentMode === 'chat'" class="sidebar" width="288">
        <div class="sidebar-actions">
          <a-button type="primary" block @click="createConversation">
            <PlusOutlined />
            新研究
          </a-button>
        </div>

        <div class="conversation-list">
          <a-alert
            v-if="conversationStore.lastError"
            class="sidebar-alert"
            show-icon
            type="warning"
            :message="conversationStore.lastError"
          />

          <button
            v-for="item in conversationStore.conversations"
            :key="item.key"
            class="conversation-item"
            :class="{ active: item.key === route.params.convId }"
            @click="router.push(`/chat/${item.key}`)"
          >
            <span class="conversation-title">{{ item.title }}</span>
            <span class="conversation-badges">
              <span class="status-badge" :class="statusClass(item.status)">
                {{ statusLabel(item.status) }}
              </span>
              <span v-if="item.reportAvailable" class="report-badge">报告</span>
            </span>
            <span class="conversation-meta">
              <span>{{ messageCountLabel(item) }}</span>
              <span>{{ lastTimeLabel(item.lastMessageAt) }}</span>
            </span>
            <a-button
              size="small"
              type="text"
              danger
              class="delete-button"
              @click.stop="deleteConversation(item.key)"
            >
              <DeleteOutlined />
            </a-button>
          </button>
        </div>

        <div class="sidebar-footer" v-if="conversationStore.conversations.length">
          <a-button danger size="small" block @click="confirmClearAll">清空全部</a-button>
        </div>
      </a-layout-sider>

      <a-layout-content class="content">
        <RouterView />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style lang="less" scoped>
.app-shell {
  background: #f5f7fb;
  height: 100vh;
}

.topbar {
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e8edf4;
  display: flex;
  gap: 24px;
  height: 56px;
  line-height: 56px;
  padding: 0 18px;
}

.brand {
  color: #172033;
  cursor: pointer;
  font-size: 18px;
  font-weight: 700;
}

.nav-label {
  align-items: center;
  display: inline-flex;
  gap: 6px;
}

.sidebar {
  background: #eef2f7 !important;
  border-right: 1px solid #e0e7f0;
  overflow: hidden;
}

.sidebar-actions {
  padding: 14px;
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: calc(100vh - 156px);
  overflow: auto;
  padding: 0 10px;
}

.sidebar-alert {
  margin-bottom: 8px;
}

.conversation-item {
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  color: #263244;
  cursor: pointer;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 6px 8px;
  padding: 10px;
  position: relative;
  text-align: left;
  width: 100%;
}

.conversation-item:hover,
.conversation-item.active {
  background: #fff;
  border-color: #dce5f2;
}

.conversation-title {
  align-self: center;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-badges {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  grid-column: 1 / 2;
  min-width: 0;
}

.status-badge,
.report-badge {
  border-radius: 6px;
  font-size: 11px;
  line-height: 18px;
  padding: 0 6px;
}

.status-badge {
  background: #eef3fa;
  color: #667085;
}

.status-badge.completed {
  background: #eaf7ee;
  color: #237847;
}

.status-badge.stopped,
.status-badge.saved,
.status-badge.unknown {
  background: #eef3fa;
  color: #667085;
}

.status-badge.failed {
  background: #fff0f0;
  color: #c32f35;
}

.status-badge.running {
  background: #eaf1ff;
  color: #2356f6;
}

.status-badge.waiting {
  background: #fff7e8;
  color: #a65f00;
}

.status-badge.draft {
  background: #f5f0ff;
  color: #5b3fb4;
}

.report-badge {
  background: #eef7ff;
  color: #2356f6;
}

.conversation-meta {
  color: #7a8798;
  display: flex;
  flex-wrap: wrap;
  font-size: 12px;
  gap: 6px;
  grid-column: 1 / 2;
}

.delete-button {
  grid-column: 2 / 3;
  grid-row: 1 / 4;
}

.sidebar-footer {
  bottom: 0;
  padding: 10px;
  position: absolute;
  width: 100%;
}

.content {
  min-width: 0;
  overflow: hidden;
}

@media (max-width: 720px) {
  .topbar {
    gap: 12px;
    padding: 0 12px;
  }

  .brand {
    font-size: 16px;
  }

  .nav-label {
    gap: 4px;
  }

  .sidebar {
    flex: 0 0 220px !important;
    max-width: 220px !important;
    min-width: 220px !important;
    width: 220px !important;
  }
}

@media (max-width: 560px) {
  .app-shell {
    height: auto;
    min-height: 100vh;
  }

  .topbar {
    height: auto;
    line-height: 1.2;
    padding: 10px 12px;
  }

  .topbar :deep(.ant-segmented-item-label) {
    padding: 0 8px;
  }

  .main-layout {
    flex-direction: column;
  }

  .sidebar {
    border-bottom: 1px solid #e0e7f0;
    border-right: 0;
    flex: none !important;
    max-width: none !important;
    min-width: 0 !important;
    width: 100% !important;
  }

  .sidebar-actions {
    padding: 10px 12px;
  }

  .conversation-list {
    display: grid;
    grid-auto-columns: minmax(180px, 72vw);
    grid-auto-flow: column;
    max-height: none;
    overflow-x: auto;
    padding: 0 12px 10px;
  }

  .conversation-item {
    min-width: 0;
  }

  .sidebar-footer {
    display: none;
  }

  .content {
    flex: none;
    overflow: visible;
    width: 100%;
  }

  .main-layout.ant-layout-has-sider > .content {
    min-width: 100%;
    width: 100% !important;
  }
}
</style>
