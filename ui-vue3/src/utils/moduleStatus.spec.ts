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

  it('detects explicit missing key metadata', () => {
    expect(mcpServerStatusView({ ...baseServer, keyConfigured: false }))
      .toMatchObject({
        kind: 'missingKey',
        label: '需要配置 Key',
      })
  })

  it('detects stopped local MCP services', () => {
    expect(mcpServerStatusView({
      ...baseServer,
      url: 'http://127.0.0.1:18090',
      error: 'Connection refused: /127.0.0.1:18090',
    })).toMatchObject({
      kind: 'serviceStopped',
      label: '服务未启动',
    })
  })

  it('maps local MCP initialization timeout to stopped service status', () => {
    expect(mcpServerStatusView({
      ...baseServer,
      url: 'http://127.0.0.1:18090',
      error: 'TimeoutException: Did not observe any item or terminal signal',
    })).toMatchObject({
      kind: 'serviceStopped',
      label: '服务未启动',
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
