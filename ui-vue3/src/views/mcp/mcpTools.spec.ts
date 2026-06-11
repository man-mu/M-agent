import { describe, expect, it } from 'vitest'
import {
  invocationResultSummary,
  mcpServerAddress,
  mcpServerDisplay,
  mcpSourceColor,
  mcpSourceLabel,
  mcpToolExampleInput,
  normalizeToolNames,
  parseMcpJsonObject,
  prettyMcpJson,
  testResultSummary,
  validateMcpServerConfig,
} from './mcpTools'
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
    expect(display.setupHints).toContain('启动 tools/local-qweather-mcp，确认 http://127.0.0.1:18090/health 返回 ok')
    expect(display.setupHints.join('\n')).toContain('不使用示例天气')
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

  it('normalizes tool lists from form text', () => {
    expect(normalizeToolNames('weather_now\nmaps_geo，weather_now; maps_text_search')).toEqual([
      'weather_now',
      'maps_geo',
      'maps_text_search',
    ])
  })

  it('validates server config before submit', () => {
    expect(validateMcpServerConfig({
      id: 'bad id',
      url: 'http://127.0.0.1:18090',
      sseEndpoint: '/sse',
      enabled: true,
    })).toContain('服务 ID')

    expect(validateMcpServerConfig({
      id: 'local-qweather',
      url: 'ftp://127.0.0.1',
      sseEndpoint: '/sse',
      enabled: true,
    })).toContain('http://')

    expect(validateMcpServerConfig({
      id: 'local-qweather',
      url: 'https://example.com',
      sseEndpoint: '/sse?key=secret-value',
      enabled: true,
    })).toContain('${ENV_NAME}')

    expect(validateMcpServerConfig({
      id: 'local-qweather',
      url: 'https://example.com',
      sseEndpoint: '/sse?key=${EXAMPLE_API_KEY}',
      enabled: true,
    })).toBe('')

    expect(validateMcpServerConfig({
      id: 'local-qweather',
      url: 'http://127.0.0.1:18090',
      sseEndpoint: '/sse',
      enabled: true,
    })).toBe('')
  })

  it('summarizes source and connection test results', () => {
    expect(mcpServerAddress('http://127.0.0.1:18090', '/sse')).toBe('http://127.0.0.1:18090/sse')
    expect(mcpSourceLabel({ source: 'LOCAL', localOverride: true })).toBe('本地覆盖')
    expect(mcpSourceColor({ source: 'LOCAL', localOverride: false })).toBe('green')
    expect(testResultSummary({
      id: 'local-qweather',
      url: 'http://127.0.0.1:18090',
      sseEndpoint: '/sse',
      connected: true,
      toolCount: 1,
      toolNames: ['weather_now'],
      durationMs: 15,
    })).toContain('1 个工具')
  })

  it('builds and validates MCP tool debug payloads', () => {
    expect(mcpToolExampleInput('weather_now')).toEqual({
      location: '上海',
      lang: 'zh',
      unit: 'm',
    })
    expect(mcpToolExampleInput('unknown_tool')).toEqual({})

    const parsed = parseMcpJsonObject('{"location":"上海"}')
    expect(parsed.ok).toBe(true)
    if (parsed.ok) {
      expect(parsed.value).toEqual({ location: '上海' })
    }

    expect(parseMcpJsonObject('[1,2]').ok).toBe(false)
    expect(parseMcpJsonObject('{').ok).toBe(false)
    expect(prettyMcpJson({ a: 1 })).toContain('\n  "a": 1\n')
  })

  it('summarizes MCP tool invocation results', () => {
    expect(invocationResultSummary({
      toolName: 'weather_now',
      input: { location: '上海' },
      output: '上海当前多云',
      durationMs: 18,
      error: '',
    })).toContain('18 ms')

    expect(invocationResultSummary({
      toolName: 'weather_now',
      input: {},
      output: '',
      durationMs: 0,
      error: 'MCP server is not reachable',
    })).toBe('MCP server is not reachable')
  })
})
