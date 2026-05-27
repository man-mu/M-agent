<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import {
  DeleteOutlined,
  MessageOutlined,
  PlusOutlined,
  SettingOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import { useConversationStore } from '@/store/ConversationStore'
import { useMessageStore } from '@/store/MessageStore'

const router = useRouter()
const route = useRoute()
const conversationStore = useConversationStore()
const messageStore = useMessageStore()

const currentMode = computed(() => {
  if (route.path.startsWith('/skills')) return 'skills'
  if (route.path.startsWith('/settings')) return 'settings'
  return 'chat'
})

const navItems = [
  { value: 'chat', label: '对话', icon: MessageOutlined },
  { value: 'skills', label: 'Skills', icon: ToolOutlined },
  { value: 'settings', label: '模型', icon: SettingOutlined },
]

function switchMode(mode: string) {
  if (mode === 'chat') {
    router.push(conversationStore.curConvKey ? `/chat/${conversationStore.curConvKey}` : '/chat')
  } else {
    router.push(`/${mode}`)
  }
}

function handleModeChange(value: string | number) {
  switchMode(String(value))
}

function createConversation() {
  const item = conversationStore.newOne()
  messageStore.reset(item.key)
  router.push(`/chat/${item.key}`)
}

async function deleteConversation(key: string) {
  await conversationStore.delete(key)
  if (route.params.convId === key) {
    messageStore.reset()
    router.push('/chat')
  }
}

function confirmClearAll() {
  Modal.confirm({
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
  })
}

onMounted(async () => {
  await conversationStore.loadFromBackend()
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

    <a-layout>
      <a-layout-sider v-if="currentMode === 'chat'" class="sidebar" width="288">
        <div class="sidebar-actions">
          <a-button type="primary" block @click="createConversation">
            <PlusOutlined />
            新研究
          </a-button>
        </div>

        <div class="conversation-list">
          <button
            v-for="item in conversationStore.conversations"
            :key="item.key"
            class="conversation-item"
            :class="{ active: item.key === route.params.convId }"
            @click="router.push(`/chat/${item.key}`)"
          >
            <span class="conversation-title">{{ item.title }}</span>
            <span class="conversation-meta">
              {{ item.messageCount ? `${item.messageCount} 条消息` : '本地会话' }}
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

.conversation-item {
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  color: #263244;
  cursor: pointer;
  display: grid;
  grid-template-columns: 1fr auto;
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
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-meta {
  color: #7a8798;
  font-size: 12px;
  grid-column: 1 / 2;
  margin-top: 4px;
}

.delete-button {
  grid-column: 2 / 3;
  grid-row: 1 / 3;
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
</style>
