import { get } from '@/utils/request'

export interface AppCapabilities {
  skillEnabled: boolean
  ragEnabled: boolean
  mcpEnabled: boolean
}

export interface McpServerStatus {
  url: string
  sseEndpoint?: string
  description?: string
  configuredEnabled: boolean
  connected: boolean
  error?: string
}

export interface McpStatus {
  enabled: boolean
  servers: McpServerStatus[]
  toolCount: number
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
}

export default new AppService()
