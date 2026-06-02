<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Card from 'ant-design-vue/es/card'
import {
  ArrowLeftOutlined,
  EnvironmentOutlined,
  ReloadOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import appService from '@/services/api/app'
import type { AppCapabilities, McpStatus } from '@/services/api/app'
import { userMessageFromError } from '@/utils/errors'
import { mcpOverallStatusView, mcpServerStatusView } from '@/utils/moduleStatus'

const router = useRouter()
const app = getCurrentInstance()?.appContext.app
if (app && !app.component('ACard')) {
  app.use(Card)
}

const capabilities = ref<AppCapabilities | null>(null)
const mcpStatus = ref<McpStatus | null>(null)
const loading = ref(false)
const loadError = ref('')

const overallStatus = computed(() => mcpOverallStatusView(mcpStatus.value))
const connectedCount = computed(() => mcpStatus.value?.servers.filter(server => server.connected).length || 0)

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const appCapabilities = await appService.getCapabilities()
    capabilities.value = appCapabilities
    if (appCapabilities.mcpEnabled) {
      mcpStatus.value = await appService.getMcpStatus()
    } else {
      mcpStatus.value = null
    }
  } catch (err: any) {
    loadError.value = userMessageFromError(err, '加载 MCP 状态失败')
    message.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function serverAddress(url: string, endpoint?: string) {
  return `${url}${endpoint || ''}`
}

onMounted(loadData)
</script>

<template>
  <main class="mcp-page" data-testid="mcp-page">
    <div class="page-header">
      <a-button @click="router.push('/chat')">
        <ArrowLeftOutlined />
        返回对话
      </a-button>
      <div>
        <div class="eyebrow">工具服务</div>
        <h1>MCP 工具</h1>
      </div>
      <a-button :loading="loading" @click="loadData">
        <ReloadOutlined />
        刷新
      </a-button>
    </div>

    <a-alert
      v-if="loadError"
      class="page-alert"
      show-icon
      type="warning"
      :message="loadError"
    >
      <template #action>
        <a-button size="small" @click="loadData">重试</a-button>
      </template>
    </a-alert>

    <a-card class="status-card" data-testid="mcp-summary-card">
      <template #title>
        <span><EnvironmentOutlined /> 工具服务状态</span>
      </template>
      <div class="summary-grid">
        <div class="summary-item">
          <span>模块状态</span>
          <strong>
            <a-tag :color="overallStatus.color">{{ overallStatus.label }}</a-tag>
          </strong>
        </div>
        <div class="summary-item">
          <span>服务数量</span>
          <strong>{{ mcpStatus?.servers.length || 0 }}</strong>
        </div>
        <div class="summary-item">
          <span>已连接</span>
          <strong>{{ connectedCount }}</strong>
        </div>
        <div class="summary-item">
          <span>工具数量</span>
          <strong>{{ mcpStatus?.toolCount || 0 }}</strong>
        </div>
      </div>
      <p class="summary-text">{{ overallStatus.description }}</p>
    </a-card>

    <a-empty v-if="!loading && capabilities && !capabilities.mcpEnabled" description="MCP 模块未启用">
      <a-button type="primary" @click="router.push('/settings')">
        <SettingOutlined />
        返回模型设置
      </a-button>
    </a-empty>

    <a-spin v-else :spinning="loading">
      <div class="server-list">
        <a-card
          v-for="server in mcpStatus?.servers || []"
          :key="server.url"
          class="server-card"
          data-testid="mcp-server-card"
        >
          <template #title>
            <div class="server-title">
              <span>{{ server.description || 'MCP 服务' }}</span>
              <a-tag :color="mcpServerStatusView(server).color">
                {{ mcpServerStatusView(server).label }}
              </a-tag>
            </div>
          </template>

          <div class="server-body">
            <div>
              <span class="label">服务地址</span>
              <p class="server-url">{{ serverAddress(server.url, server.sseEndpoint) }}</p>
            </div>
            <div>
              <span class="label">说明</span>
              <p>{{ mcpServerStatusView(server).description }}</p>
            </div>
            <div v-if="server.error && mcpServerStatusView(server).kind !== 'missingKey'">
              <span class="label">当前状态</span>
              <p class="error-text">{{ mcpServerStatusView(server).description }}</p>
            </div>
          </div>
        </a-card>

        <a-empty
          v-if="!loading && capabilities?.mcpEnabled && !mcpStatus?.servers.length"
          description="还没有启用的 MCP 服务"
        />
      </div>
    </a-spin>
  </main>
</template>

<style lang="less" scoped>
.mcp-page {
  height: calc(100vh - 56px);
  overflow: auto;
  padding: 24px;
}

.page-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin: 0 auto 18px;
  max-width: 1100px;
}

.page-header h1 {
  font-size: 24px;
  margin: 2px 0 0;
  text-align: center;
}

.eyebrow {
  color: #738096;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-align: center;
  text-transform: uppercase;
}

.status-card,
.server-list,
.page-alert {
  margin: 0 auto 16px;
  max-width: 1100px;
}

.summary-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.summary-item {
  background: #fbfcff;
  border: 1px solid #edf1f6;
  border-radius: 8px;
  padding: 12px;
}

.summary-item span,
.label {
  color: #6b7688;
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.summary-item strong {
  color: #172033;
  font-size: 18px;
}

.summary-text {
  color: #5f6b7c;
  margin: 12px 0 0;
}

.server-list {
  display: grid;
  gap: 14px;
}

.server-card {
  min-width: 0;
}

.server-card :deep(.ant-card-head-title) {
  min-width: 0;
  overflow: visible;
  white-space: normal;
}

.server-title {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
  min-width: 0;
}

.server-title span {
  min-width: 0;
  word-break: break-word;
}

.server-body {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.server-body > div {
  min-width: 0;
}

.server-body p {
  margin: 0;
}

.server-url {
  color: #334155;
  word-break: break-all;
}

.error-text {
  color: #b42318;
  word-break: break-word;
}

@media (max-width: 640px) {
  .mcp-page {
    padding: 18px 12px;
  }

  .page-header {
    align-items: stretch;
    gap: 10px;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .server-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
