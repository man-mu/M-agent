import { describe, expect, it } from 'vitest'
import { mcpServerDisplay } from './mcpTools'
import type { McpServerStatus } from '@/services/api/app'

const baseServer: McpServerStatus = {
  url: 'http://127.0.0.1:18090',
  sseEndpoint: '/sse',
  description: '本地和风天气 MCP - 查询城市实时天气',
  configuredEnabled: true,
  connected: false,
}

describe('mcpTools display helpers', () => {
  it('recognizes the local QWeather server by weather_now tool', () => {
    const display = mcpServerDisplay({
      ...baseServer,
      allowedTools: ['weather_now'],
      keyConfigured: false,
    })

    expect(display.isLocalQWeather).toBe(true)
    expect(display.serviceName).toBe('本地和风天气 MCP')
    expect(display.tools).toEqual([{
      name: 'weather_now',
      label: '实时天气查询',
      description: '查询城市、和风 Location ID 或经纬度的实时天气。',
    }])
    expect(display.requiredEnvVars).toContain('QWEATHER_API_KEY')
    expect(display.optionalEnvVars).toContain('QWEATHER_API_HOST')
    expect(display.keyStatusLabel).toBe('缺少 Key')
  })

  it('reports configured keys without exposing key values', () => {
    const display = mcpServerDisplay({
      ...baseServer,
      allowedTools: ['weather_now'],
      requiredEnvVars: ['QWEATHER_API_KEY'],
      keyConfigured: true,
    })

    expect(display.keyStatusLabel).toBe('Key 已配置')
    expect(display.keyStatusColor).toBe('green')
    expect(JSON.stringify(display)).not.toContain('secret')
  })

  it('keeps generic MCP services readable', () => {
    const display = mcpServerDisplay({
      url: 'https://mcp.amap.com',
      sseEndpoint: '/sse?key=${AMAP_MAPS_API_KEY}',
      description: '高德地图 MCP',
      configuredEnabled: true,
      connected: false,
      allowedTools: ['maps_geo', 'maps_text_search'],
      requiredEnvVars: ['AMAP_MAPS_API_KEY'],
    })

    expect(display.isLocalQWeather).toBe(false)
    expect(display.serviceName).toBe('高德地图 MCP')
    expect(display.tools.map(tool => tool.label)).toEqual(['地址解析', '关键词搜索'])
    expect(display.setupHints).toEqual(['配置 AMAP_MAPS_API_KEY 后刷新状态'])
  })

  it('keeps disabled services compact', () => {
    const display = mcpServerDisplay({
      url: 'https://mcp.amap.com',
      sseEndpoint: '/sse?key=${AMAP_MAPS_API_KEY}',
      description: '高德地图 MCP',
      configuredEnabled: false,
      connected: false,
      allowedTools: ['maps_geo'],
      requiredEnvVars: ['AMAP_MAPS_API_KEY'],
    })

    expect(display.serviceSummary).toBe('这个 MCP 服务当前未启用。')
    expect(display.tools).toEqual([])
    expect(display.requiredEnvVars).toEqual([])
    expect(display.setupHints).toEqual([])
  })
})
