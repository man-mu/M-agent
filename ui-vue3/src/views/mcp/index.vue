<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Card from 'ant-design-vue/es/card'
import {
  ArrowLeftOutlined,
  EnvironmentOutlined,
  KeyOutlined,
  ReloadOutlined,
  SettingOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import appService from '@/services/api/app'
import type { AppCapabilities, McpStatus } from '@/services/api/app'
import { userMessageFromError } from '@/utils/errors'
import { mcpOverallStatusView, mcpServerStatusView } from '@/utils/moduleStatus'
import { mcpServerDisplay } from './mcpTools'

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
const serverViews = computed(() => (mcpStatus.value?.servers || []).map(server => ({
  server,
  status: mcpServerStatusView(server),
  display: mcpServerDisplay(server),
  address: serverAddress(server.url, server.sseEndpoint),
})))

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
          v-for="view in serverViews"
          :key="view.server.url"
          class="server-card"
          :class="{ 'weather-card': view.display.isLocalQWeather }"
          data-testid="mcp-server-card"
        >
          <template #title>
            <div class="server-title">
              <div class="server-heading">
                <span class="server-name">{{ view.display.serviceName }}</span>
                <span class="server-summary">{{ view.display.serviceSummary }}</span>
              </div>
              <div class="server-tags">
                <a-tag :color="view.status.color">{{ view.status.label }}</a-tag>
                <a-tag
                  v-if="view.display.requiredEnvVars.length"
                  :color="view.display.keyStatusColor"
                >
                  {{ view.display.keyStatusLabel }}
                </a-tag>
              </div>
            </div>
          </template>

          <div class="server-body">
            <div class="status-grid">
              <div>
                <span class="label">服务地址</span>
                <p class="server-url">{{ view.address }}</p>
              </div>
              <div>
                <span class="label">连接状态</span>
                <p>{{ view.status.description }}</p>
              </div>
              <div v-if="view.display.requiredEnvVars.length">
                <span class="label">访问凭证</span>
                <p>{{ view.display.requiredEnvVars.join('、') }}</p>
              </div>
            </div>

            <section v-if="view.display.tools.length" class="tool-section">
              <div class="section-title">
                <ToolOutlined />
                <span>工具列表</span>
              </div>
              <div class="tool-list">
                <div
                  v-for="tool in view.display.tools"
                  :key="tool.name"
                  class="tool-item"
                >
                  <div class="tool-title">
                    <strong>{{ tool.label }}</strong>
                    <a-tag color="blue">{{ tool.name }}</a-tag>
                  </div>
                  <p>{{ tool.description }}</p>
                </div>
              </div>
            </section>

            <section
              v-if="view.display.requiredEnvVars.length || view.display.setupHints.length"
              class="key-section"
            >
              <div class="section-title">
                <KeyOutlined />
                <span>Key 指引</span>
              </div>
              <div class="env-row" v-if="view.display.requiredEnvVars.length">
                <span class="label">必需</span>
                <div class="tag-list">
                  <a-tag
                    v-for="name in view.display.requiredEnvVars"
                    :key="name"
                    color="orange"
                  >
                    {{ name }}
                  </a-tag>
                </div>
              </div>
              <div class="env-row" v-if="view.display.optionalEnvVars.length">
                <span class="label">可选</span>
                <div class="tag-list">
                  <a-tag
                    v-for="name in view.display.optionalEnvVars"
                    :key="name"
                  >
                    {{ name }}
                  </a-tag>
                </div>
              </div>
              <ul class="hint-list">
                <li v-for="hint in view.display.setupHints" :key="hint">{{ hint }}</li>
              </ul>
            </section>

            <div v-if="view.server.error && view.status.kind !== 'missingKey'">
              <span class="label">当前状态</span>
              <p class="error-text">{{ view.status.description }}</p>
            </div>
          </div>
        </a-card>

        <a-empty
          v-if="!loading && capabilities?.mcpEnabled && !serverViews.length"
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

.weather-card {
  border-color: #c7e7df;
}

.server-card :deep(.ant-card-head-title) {
  min-width: 0;
  overflow: visible;
  white-space: normal;
}

.server-title {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  min-width: 0;
}

.server-heading {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.server-name {
  color: #172033;
  font-size: 16px;
  min-width: 0;
  word-break: break-word;
}

.server-summary {
  color: #6b7688;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.5;
}

.server-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.server-body {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.server-body > div,
.server-body > section {
  min-width: 0;
}

.server-body p {
  margin: 0;
}

.status-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: 2fr 1.5fr 1fr;
}

.server-url {
  color: #334155;
  word-break: break-all;
}

.section-title {
  align-items: center;
  color: #172033;
  display: flex;
  font-size: 14px;
  font-weight: 600;
  gap: 6px;
  margin-bottom: 10px;
}

.tool-list {
  display: grid;
  gap: 10px;
}

.tool-item {
  background: #f7fbff;
  border: 1px solid #e4edf7;
  border-radius: 8px;
  padding: 10px 12px;
}

.tool-title {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}

.tool-title strong {
  color: #172033;
}

.tool-item p,
.hint-list {
  color: #5f6b7c;
  font-size: 13px;
}

.key-section {
  background: #fffaf2;
  border: 1px solid #f4dfb6;
  border-radius: 8px;
  padding: 12px;
}

.env-row {
  margin-bottom: 10px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.hint-list {
  margin: 0;
  padding-left: 18px;
}

.hint-list li + li {
  margin-top: 4px;
}

.error-text {
  color: #b42318;
  word-break: break-word;
}

@media (max-width: 760px) {
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
    flex-direction: column;
  }

  .server-tags {
    justify-content: flex-start;
  }

  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
