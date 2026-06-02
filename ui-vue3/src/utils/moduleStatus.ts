import type { McpServerStatus, McpStatus } from '@/services/api/app'

export type ModuleStatusKind = 'connected' | 'missingKey' | 'disabled' | 'failed'

export interface ModuleStatusView {
  kind: ModuleStatusKind
  label: string
  color: string
  description: string
}

export function mcpServerStatusView(server: McpServerStatus): ModuleStatusView {
  if (!server.configuredEnabled) {
    return {
      kind: 'disabled',
      label: '未启用',
      color: 'default',
      description: '这个 MCP 服务当前没有启用。',
    }
  }

  if (server.connected) {
    return {
      kind: 'connected',
      label: '已连接',
      color: 'green',
      description: '服务已连接，可在研究流程中使用相关工具。',
    }
  }

  const error = server.error || ''
  if (server.sseEndpoint?.includes('${') || /key|token|credential|api[_-]?key/i.test(error)) {
    return {
      kind: 'missingKey',
      label: '需要配置 Key',
      color: 'orange',
      description: '服务需要本地环境变量或后端安全配置提供访问 Key。',
    }
  }

  return {
    kind: 'failed',
    label: '连接失败',
    color: 'red',
    description: '服务暂未连接成功，请检查后端日志、网络和服务地址。',
  }
}

export function mcpOverallStatusView(status: McpStatus | null | undefined): ModuleStatusView {
  if (!status || !status.enabled) {
    return {
      kind: 'disabled',
      label: '未启用',
      color: 'default',
      description: 'MCP 模块当前未启用。',
    }
  }

  if (status.servers.some(server => mcpServerStatusView(server).kind === 'connected')) {
    return {
      kind: 'connected',
      label: '可用',
      color: 'green',
      description: `${status.toolCount} 个工具已接入。`,
    }
  }

  if (status.servers.some(server => mcpServerStatusView(server).kind === 'missingKey')) {
    return {
      kind: 'missingKey',
      label: '需要配置',
      color: 'orange',
      description: '已发现 MCP 服务，但需要补充访问 Key 后才能连接。',
    }
  }

  return {
    kind: 'failed',
    label: '未连接',
    color: 'red',
    description: 'MCP 模块已启用，但当前没有已连接的服务。',
  }
}
