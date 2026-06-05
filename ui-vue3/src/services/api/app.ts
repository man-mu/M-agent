import { apiRequest, del, get, post, put } from '@/utils/request'

export interface AppCapabilities {
  skillEnabled: boolean
  ragEnabled: boolean
  mcpEnabled: boolean
}

export interface McpServerStatus {
  id?: string
  url: string
  sseEndpoint?: string
  description?: string
  configuredEnabled: boolean
  connected: boolean
  error?: string
  allowedTools?: string[]
  keyEnvName?: string
  keyConfigured?: boolean
  requiredEnvVars?: string[]
  source?: 'BUILTIN' | 'LOCAL' | string
  editable?: boolean
  localOverride?: boolean
}

export interface McpStatus {
  enabled: boolean
  servers: McpServerStatus[]
  toolCount: number
}

export interface McpServerConfig {
  id?: string
  url: string
  sseEndpoint?: string
  description?: string
  enabled: boolean
  allowedTools?: string[]
  source?: 'BUILTIN' | 'LOCAL' | string
  editable?: boolean
  localOverride?: boolean
}

export interface McpConnectionTestResult {
  id?: string
  url: string
  sseEndpoint?: string
  connected: boolean
  toolCount: number
  toolNames: string[]
  error?: string
  durationMs: number
  requiredEnvVars?: string[]
  keyConfigured?: boolean
}

export const disabledCapabilities: AppCapabilities = {
  skillEnabled: false,
  ragEnabled: false,
  mcpEnabled: false,
}

class AppService {
  getCapabilities(): Promise<AppCapabilities> {
    return get<AppCapabilities>('/api/app/capabilities')
  }

  getMcpStatus(): Promise<McpStatus> {
    return get<McpStatus>('/api/mcp/status')
  }

  getMcpServers(): Promise<McpServerConfig[]> {
    return get<McpServerConfig[]>('/api/mcp/servers')
  }

  createMcpServer(request: McpServerConfig): Promise<McpServerConfig> {
    return post<McpServerConfig>('/api/mcp/servers', request)
  }

  updateMcpServer(id: string, request: McpServerConfig): Promise<McpServerConfig> {
    return put<McpServerConfig>(`/api/mcp/servers/${id}`, request)
  }

  deleteMcpServer(id: string): Promise<void> {
    return del<void>(`/api/mcp/servers/${id}`)
  }

  toggleMcpServer(id: string): Promise<McpServerConfig> {
    return apiRequest<McpServerConfig>({
      method: 'PATCH',
      url: `/api/mcp/servers/${id}/toggle`,
    })
  }

  testMcpServer(id: string): Promise<McpConnectionTestResult> {
    return post<McpConnectionTestResult>(`/api/mcp/servers/${id}/test`)
  }

  reloadMcp(): Promise<McpStatus> {
    return post<McpStatus>('/api/mcp/reload')
  }
}

export default new AppService()
