import { describe, expect, it } from 'vitest'
import { mcpOverallStatusView, mcpServerStatusView } from './moduleStatus'
import type { McpServerStatus } from '@/services/api/app'

const baseServer: McpServerStatus = {
  url: 'https://mcp.amap.com',
  sseEndpoint: '/sse',
  description: '高德地图 MCP',
  configuredEnabled: true,
  connected: false,
}

describe('module status helpers', () => {
  it('maps connected MCP servers to a usable status', () => {
    expect(mcpServerStatusView({ ...baseServer, connected: true })).toMatchObject({
      kind: 'connected',
      label: '已连接',
      color: 'green',
    })
  })

  it('detects unresolved key placeholders', () => {
    expect(mcpServerStatusView({ ...baseServer, sseEndpoint: '/sse?key=${AMAP_MAPS_API_KEY}' }))
      .toMatchObject({
        kind: 'missingKey',
        label: '需要配置 Key',
      })
  })

  it('summarizes overall MCP status', () => {
    expect(mcpOverallStatusView(null).kind).toBe('disabled')
    expect(mcpOverallStatusView({
      enabled: true,
      toolCount: 0,
      servers: [{ ...baseServer, sseEndpoint: '/sse?key=${AMAP_MAPS_API_KEY}' }],
    }).kind).toBe('missingKey')
    expect(mcpOverallStatusView({
      enabled: true,
      toolCount: 3,
      servers: [{ ...baseServer, connected: true }],
    }).kind).toBe('connected')
  })
})
