<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Card from 'ant-design-vue/es/card'
import Drawer from 'ant-design-vue/es/drawer'
import Form from 'ant-design-vue/es/form'
import Modal from 'ant-design-vue/es/modal'
import Table from 'ant-design-vue/es/table'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EnvironmentOutlined,
  KeyOutlined,
  PlusOutlined,
  PoweroffOutlined,
  ReloadOutlined,
  SettingOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import appService from '@/services/api/app'
import type { AppCapabilities, McpConnectionTestResult, McpServerConfig, McpServerStatus, McpStatus, McpToolInvocationResult } from '@/services/api/app'
import { userMessageFromError } from '@/utils/errors'
import { mcpOverallStatusView, mcpServerStatusView } from '@/utils/moduleStatus'
import {
  invocationResultSummary,
  mcpServerAddress,
  mcpServerDisplay,
  mcpSourceColor,
  mcpSourceLabel,
  mcpToolExampleInput,
  normalizeToolNames,
  parseMcpJsonObject,
  parseModelScopeJson,
  prettyMcpJson,
  testResultSummary,
  toolsText,
} from './mcpTools'

const router = useRouter()
const app = getCurrentInstance()?.appContext.app
if (app && !app.component('ACard')) {
  app.use(Card)
}
if (app) {
  if (!app.component('ADrawer')) app.use(Drawer)
  if (!app.component('AForm')) app.use(Form)
  if (!app.component('AModal')) app.use(Modal)
  if (!app.component('ATable')) app.use(Table)
}

const capabilities = ref<AppCapabilities | null>(null)
const mcpStatus = ref<McpStatus | null>(null)
const mcpServers = ref<McpServerConfig[]>([])
const loading = ref(false)
const actionLoading = ref(false)
const savingServer = ref(false)
const testingServerId = ref('')
const loadError = ref('')
const serverModalVisible = ref(false)
const connectionResults = ref<Record<string, McpConnectionTestResult>>({})
const toolDebugVisible = ref(false)
const debugToolName = ref('')
const debugToolLabel = ref('')
const debugToolInputText = ref('{}')
const debugToolInputError = ref('')
const invokingTool = ref(false)
const invocationResult = ref<McpToolInvocationResult | null>(null)

// 新增远程 MCP 表单
const jsonInput = ref('')
const descriptionInput = ref('')
const apiKeyInput = ref('')
const jsonParseError = ref('')
const parsedPreview = ref<{ id: string; type: string; url: string } | null>(null)

const serverColumns = [
  { title: '服务', key: 'server', width: 280 },
  { title: '状态', key: 'status', width: 120 },
  { title: '来源', key: 'source', width: 120 },
  { title: '工具', key: 'tools', width: 220 },
  { title: '连接测试', key: 'test', width: 220 },
  { title: '操作', key: 'actions', width: 200, fixed: 'right' },
] as const

const overallStatus = computed(() => mcpOverallStatusView(mcpStatus.value))
const connectedCount = computed(() => mcpStatus.value?.servers.filter(server => server.connected).length || 0)
const serverViews = computed(() => (mcpStatus.value?.servers || []).map(server => ({
  server,
  status: mcpServerStatusView(server),
  display: mcpServerDisplay(server),
  address: mcpServerAddress(server.url, server.sseEndpoint),
})))
const invocationSummary = computed(() => invocationResultSummary(invocationResult.value))
const invocationResultText = computed(() => invocationResult.value ? prettyMcpJson(invocationResult.value) : '')

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const appCapabilities = await appService.getCapabilities()
    capabilities.value = appCapabilities
    if (appCapabilities.mcpEnabled) {
      const [status, servers] = await Promise.all([
        appService.getMcpStatus(),
        appService.getMcpServers(),
      ])
      mcpStatus.value = status
      mcpServers.value = servers
    } else {
      mcpStatus.value = null
      mcpServers.value = []
    }
  } catch (err: any) {
    loadError.value = userMessageFromError(err, '加载 MCP 状态失败')
    message.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function openCreateServer() {
  jsonInput.value = ''
  descriptionInput.value = ''
  apiKeyInput.value = ''
  jsonParseError.value = ''
  parsedPreview.value = null
  serverModalVisible.value = true
}

function onJsonInputChange() {
  const result = parseModelScopeJson(jsonInput.value)
  if (result.ok) {
    parsedPreview.value = { id: result.id!, type: result.type!, url: result.url! }
    jsonParseError.value = ''
  } else {
    parsedPreview.value = null
    jsonParseError.value = result.error || ''
  }
}

async function saveServer() {
  const result = parseModelScopeJson(jsonInput.value)
  if (!result.ok) {
    jsonParseError.value = result.error || 'JSON 格式不正确'
    return
  }

  savingServer.value = true
  jsonParseError.value = ''
  try {
    await appService.createMcpServerFromJson(
      jsonInput.value,
      descriptionInput.value || undefined,
      apiKeyInput.value || undefined,
    )
    message.success('MCP Server 已新增')
    serverModalVisible.value = false
    await loadData()
  } catch (err: any) {
    jsonParseError.value = userMessageFromError(err, '新增 MCP Server 失败')
  } finally {
    savingServer.value = false
  }
}

async function toggleServer(server: McpServerConfig) {
  if (!server.id) {
    return
  }
  actionLoading.value = true
  try {
    await appService.toggleMcpServer(server.id)
    message.success(server.enabled ? 'MCP Server 已停用' : 'MCP Server 已启用')
    await loadData()
  } catch (err: any) {
    message.error(userMessageFromError(err, '切换 MCP Server 状态失败'))
  } finally {
    actionLoading.value = false
  }
}

function confirmDeleteServer(server: McpServerConfig) {
  if (!server.id) {
    return
  }
  Modal.confirm({
    title: '删除 MCP Server',
    content: server.localOverride
      ? '这会删除本地覆盖配置，内置配置会恢复显示。'
      : '这会删除本地 MCP Server 配置。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      actionLoading.value = true
      try {
        await appService.deleteMcpServer(server.id!)
        message.success('MCP Server 已删除')
        await loadData()
      } catch (err: any) {
        message.error(userMessageFromError(err, '删除 MCP Server 失败'))
      } finally {
        actionLoading.value = false
      }
    },
  })
}

async function testServer(server: McpServerConfig) {
  if (!server.id) {
    return
  }
  testingServerId.value = server.id
  try {
    const result = await appService.testMcpServer(server.id)
    connectionResults.value = {
      ...connectionResults.value,
      [server.id]: result,
    }
    if (result.connected) {
      message.success(testResultSummary(result))
    } else {
      message.warning(testResultSummary(result))
    }
  } catch (err: any) {
    message.error(userMessageFromError(err, '测试 MCP Server 连接失败'))
  } finally {
    testingServerId.value = ''
  }
}

function openToolDebug(tool: { name: string; label: string }) {
  debugToolName.value = tool.name
  debugToolLabel.value = tool.label
  debugToolInputText.value = prettyMcpJson(mcpToolExampleInput(tool.name))
  debugToolInputError.value = ''
  invocationResult.value = null
  toolDebugVisible.value = true
}

async function invokeDebugTool() {
  const parsed = parseMcpJsonObject(debugToolInputText.value)
  if (!parsed.ok) {
    debugToolInputError.value = parsed.error
    return
  }

  invokingTool.value = true
  debugToolInputError.value = ''
  invocationResult.value = null
  try {
    const result = await appService.invokeMcpTool(debugToolName.value, parsed.value)
    invocationResult.value = result
    if (result.error) {
      message.warning(invocationResultSummary(result))
    } else {
      message.success(invocationResultSummary(result))
    }
  } catch (err: any) {
    message.error(userMessageFromError(err, '调试 MCP 工具失败'))
  } finally {
    invokingTool.value = false
  }
}

async function reloadMcp() {
  actionLoading.value = true
  try {
    mcpStatus.value = await appService.reloadMcp()
    mcpServers.value = await appService.getMcpServers()
    message.success('MCP 工具列表已刷新')
  } catch (err: any) {
    message.error(userMessageFromError(err, '刷新 MCP 工具失败'))
  } finally {
    actionLoading.value = false
  }
}

function statusForServer(server: McpServerConfig): McpServerStatus {
  const status = (mcpStatus.value?.servers || []).find(item =>
    (server.id && item.id === server.id) || item.url === server.url
  )
  if (status) {
    return status
  }
  return {
    id: server.id,
    url: server.url,
    sseEndpoint: server.sseEndpoint,
    description: server.description,
    configuredEnabled: server.enabled,
    connected: false,
    allowedTools: server.allowedTools,
    source: server.source,
    editable: server.editable,
    localOverride: server.localOverride,
  }
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
      <a-space wrap>
        <a-button :loading="actionLoading" :disabled="!capabilities?.mcpEnabled" @click="reloadMcp">
          <ReloadOutlined />
          重载工具
        </a-button>
        <a-button :loading="loading" @click="loadData">
          <ReloadOutlined />
          刷新
        </a-button>
      </a-space>
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
      <a-card class="config-card" data-testid="mcp-config-card">
        <template #title>
          <span><SettingOutlined /> Server 配置</span>
        </template>
        <template #extra>
          <a-button type="primary" @click="openCreateServer">
            <PlusOutlined />
            新增
          </a-button>
        </template>

        <a-table
          class="server-config-table"
          :columns="serverColumns"
          :data-source="mcpServers"
          :pagination="{ pageSize: 6, hideOnSinglePage: true }"
          :scroll="{ x: 1120 }"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'server'">
              <div class="config-server-cell">
                <strong>{{ record.description || record.id || 'MCP Server' }}</strong>
                <span>{{ mcpServerAddress(record.url, record.sseEndpoint) }}</span>
                <code v-if="record.id">{{ record.id }}</code>
              </div>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="mcpServerStatusView(statusForServer(record)).color">
                {{ mcpServerStatusView(statusForServer(record)).label }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'source'">
              <a-tag :color="mcpSourceColor(record)">
                {{ mcpSourceLabel(record) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'tools'">
              <div class="table-tools">
                <a-tag
                  v-for="tool in normalizeToolNames(record.allowedTools)"
                  :key="tool"
                  color="blue"
                >
                  {{ tool }}
                </a-tag>
                <span v-if="!normalizeToolNames(record.allowedTools).length" class="muted">
                  全部
                </span>
              </div>
            </template>
            <template v-else-if="column.key === 'test'">
              <div class="test-result">
                <a-button
                  size="small"
                  :loading="testingServerId === record.id"
                  :disabled="!record.id"
                  @click="testServer(record)"
                >
                  <ToolOutlined />
                  测试
                </a-button>
                <span
                  v-if="record.id && connectionResults[record.id]"
                  :class="{ 'test-ok': connectionResults[record.id].connected, 'test-failed': !connectionResults[record.id].connected }"
                >
                  {{ testResultSummary(connectionResults[record.id]) }}
                </span>
              </div>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space wrap>
                <a-tooltip :title="record.enabled ? '停用' : '启用'">
                  <a-button
                    size="small"
                    :danger="record.enabled"
                    :disabled="!record.id"
                    :loading="actionLoading"
                    @click="toggleServer(record)"
                  >
                    <PoweroffOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip title="连接测试">
                  <a-button
                    size="small"
                    :loading="testingServerId === record.id"
                    :disabled="!record.id"
                    @click="testServer(record)"
                  >
                    <ToolOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip :title="record.source === 'BUILTIN' ? '内置服务器不可删除' : '删除'">
                  <a-button
                    size="small"
                    danger
                    :disabled="record.source === 'BUILTIN'"
                    :loading="actionLoading"
                    @click="confirmDeleteServer(record)"
                  >
                    <DeleteOutlined />
                  </a-button>
                </a-tooltip>
              </a-space>
            </template>
          </template>
        </a-table>

        <div class="mobile-config-list">
          <article
            v-for="server in mcpServers"
            :key="server.id || server.url"
            class="mobile-config-card"
          >
            <div class="mobile-config-header">
              <div>
                <strong>{{ server.description || server.id || 'MCP Server' }}</strong>
                <span>{{ mcpServerAddress(server.url, server.sseEndpoint) }}</span>
              </div>
              <a-tag :color="mcpServerStatusView(statusForServer(server)).color">
                {{ mcpServerStatusView(statusForServer(server)).label }}
              </a-tag>
            </div>
            <div class="mobile-config-meta">
              <a-tag :color="mcpSourceColor(server)">{{ mcpSourceLabel(server) }}</a-tag>
              <a-tag
                v-for="tool in normalizeToolNames(server.allowedTools)"
                :key="tool"
                color="blue"
              >
                {{ tool }}
              </a-tag>
              <span v-if="!normalizeToolNames(server.allowedTools).length" class="muted">全部工具</span>
            </div>
            <div
              v-if="server.id && connectionResults[server.id]"
              class="mobile-test-result"
              :class="{ 'test-ok': connectionResults[server.id].connected, 'test-failed': !connectionResults[server.id].connected }"
            >
              {{ testResultSummary(connectionResults[server.id]) }}
            </div>
            <a-space class="mobile-config-actions" wrap>
              <a-button
                size="small"
                :loading="testingServerId === server.id"
                :disabled="!server.id"
                @click="testServer(server)"
              >
                <ToolOutlined />
                测试
              </a-button>
              <a-button
                size="small"
                :danger="server.enabled"
                :disabled="!server.id"
                :loading="actionLoading"
                @click="toggleServer(server)"
              >
                <PoweroffOutlined />
                {{ server.enabled ? '停用' : '启用' }}
              </a-button>
              <a-button
                size="small"
                danger
                :disabled="server.source === 'BUILTIN'"
                :loading="actionLoading"
                @click="confirmDeleteServer(server)"
              >
                <DeleteOutlined />
                删除
              </a-button>
            </a-space>
          </article>
        </div>
      </a-card>

      <div class="server-list">
        <a-card
          v-for="view in serverViews"
          :key="view.server.id || view.server.url"
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
                    <div class="tool-name">
                      <strong>{{ tool.label }}</strong>
                      <a-tag color="blue">{{ tool.name }}</a-tag>
                    </div>
                    <a-button
                      size="small"
                      :disabled="!view.server.connected"
                      @click="openToolDebug(tool)"
                    >
                      <ToolOutlined />
                      调试
                    </a-button>
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

    <a-modal
      v-model:open="serverModalVisible"
      :confirm-loading="savingServer"
      title="新增远程 MCP Server"
      ok-text="连接"
      width="680px"
      @ok="saveServer"
    >
      <a-alert
        v-if="jsonParseError"
        class="form-alert"
        show-icon
        type="warning"
        :message="jsonParseError"
      />
      <a-form layout="vertical">
        <a-form-item label="MCP 配置 JSON（从 ModelScope 社区复制）" required>
          <a-textarea
            v-model:value="jsonInput"
            :auto-size="{ minRows: 6, maxRows: 12 }"
            placeholder='{
  "mcpServers": {
    "Bazi-MCP": {
      "type": "streamable_http",
      "url": "https://mcp.api-inference.modelscope.net/b89553de02054a/mcp"
    }
  }
}'
            @change="onJsonInputChange"
          />
        </a-form-item>
        <a-form-item v-if="parsedPreview" label="解析预览">
          <div class="parsed-preview">
            <a-tag color="blue">ID: {{ parsedPreview.id }}</a-tag>
            <a-tag color="green">类型: {{ parsedPreview.type }}</a-tag>
            <span class="parsed-url">{{ parsedPreview.url }}</span>
          </div>
        </a-form-item>
        <a-form-item label="说明（可选）">
          <a-input v-model:value="descriptionInput" placeholder="默认：ModelScope MCP - {id}" />
        </a-form-item>
        <a-form-item label="API Key（可选）">
          <a-input-password v-model:value="apiKeyInput" placeholder="部分 MCP 服务需要" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-drawer
      v-model:open="toolDebugVisible"
      class="tool-debug-drawer"
      :title="debugToolLabel || debugToolName || '工具调试'"
      width="720px"
    >
      <div class="debug-panel">
        <div class="debug-summary">
          <span class="label">工具名称</span>
          <strong>{{ debugToolName }}</strong>
        </div>

        <a-form layout="vertical">
          <a-form-item
            label="JSON 参数"
            :validate-status="debugToolInputError ? 'error' : ''"
            :help="debugToolInputError || '参数会直接发送给当前已连接的 MCP 工具。'"
          >
            <a-textarea
              v-model:value="debugToolInputText"
              class="json-editor"
              :auto-size="{ minRows: 8, maxRows: 14 }"
            />
          </a-form-item>
        </a-form>

        <div class="debug-actions">
          <a-button @click="debugToolInputText = prettyMcpJson(mcpToolExampleInput(debugToolName))">
            示例参数
          </a-button>
          <a-button
            type="primary"
            :loading="invokingTool"
            @click="invokeDebugTool"
          >
            <ToolOutlined />
            调用工具
          </a-button>
        </div>

        <section v-if="invocationResult" class="debug-result">
          <div class="section-title">
            <ToolOutlined />
            <span>调用结果</span>
          </div>
          <a-alert
            :type="invocationResult.error ? 'warning' : 'success'"
            show-icon
            :message="invocationSummary"
          />
          <a-form layout="vertical">
            <a-form-item label="返回内容">
              <a-textarea
                class="json-editor"
                :value="invocationResult.error || invocationResult.output || '无输出'"
                :auto-size="{ minRows: 5, maxRows: 12 }"
                readonly
              />
            </a-form-item>
            <a-form-item label="完整响应">
              <a-textarea
                class="json-editor"
                :value="invocationResultText"
                :auto-size="{ minRows: 6, maxRows: 12 }"
                readonly
              />
            </a-form-item>
          </a-form>
        </section>
      </div>
    </a-drawer>
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
.config-card,
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

.config-card :deep(.ant-card-head-title) {
  min-width: 0;
}

.server-config-table {
  min-width: 0;
}

.mobile-config-list {
  display: none;
}

.config-server-cell {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.config-server-cell strong {
  color: #172033;
  min-width: 0;
  word-break: break-word;
}

.config-server-cell span,
.config-server-cell code,
.muted {
  color: #6b7688;
  font-size: 12px;
  min-width: 0;
  word-break: break-all;
}

.config-server-cell code {
  background: #f5f7fb;
  border-radius: 4px;
  padding: 2px 6px;
  width: fit-content;
}

.table-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.test-result {
  align-items: flex-start;
  display: grid;
  gap: 6px;
}

.test-result span {
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.test-ok {
  color: #138a52;
}

.test-failed {
  color: #b42318;
}

.mobile-config-card {
  background: #fbfcff;
  border: 1px solid #edf1f6;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  padding: 12px;
}

.mobile-config-header {
  align-items: flex-start;
  display: flex;
  gap: 10px;
  justify-content: space-between;
  min-width: 0;
}

.mobile-config-header > div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.mobile-config-header strong {
  color: #172033;
  word-break: break-word;
}

.mobile-config-header span {
  color: #6b7688;
  font-size: 12px;
  word-break: break-all;
}

.mobile-config-meta,
.mobile-config-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mobile-test-result {
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
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
  justify-content: space-between;
  margin-bottom: 6px;
}

.tool-name {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
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

.form-alert {
  margin-bottom: 12px;
}

.form-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) 120px;
}

.debug-panel {
  display: grid;
  gap: 14px;
}

.debug-summary {
  background: #fbfcff;
  border: 1px solid #edf1f6;
  border-radius: 8px;
  padding: 12px;
}

.debug-summary strong {
  color: #172033;
  word-break: break-all;
}

.debug-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.debug-result {
  display: grid;
  gap: 12px;
}

.json-editor {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
}

@media (max-width: 760px) {
  .mcp-page {
    padding: 18px 12px;
  }

  .page-header {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .server-config-table {
    display: none;
  }

  .mobile-config-list {
    display: grid;
    gap: 10px;
  }

  .mobile-config-header {
    flex-direction: column;
  }

  .mobile-config-actions :deep(.ant-space-item) {
    max-width: 100%;
  }

  .mobile-config-actions :deep(.ant-btn) {
    min-width: 76px;
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

  .form-grid {
    grid-template-columns: 1fr;
  }

  .debug-actions {
    justify-content: flex-start;
  }
}

.parsed-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.parsed-url {
  color: #7a8798;
  font-size: 12px;
  word-break: break-all;
}
</style>
