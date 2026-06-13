import type { McpConnectionTestResult, McpServerConfig, McpServerStatus, McpToolInvocationResult } from '@/services/api/app'

export interface McpToolDisplay {
  name: string
  label: string
  description: string
}

export interface McpServerDisplay {
  serviceName: string
  serviceSummary: string
  tools: McpToolDisplay[]
  requiredEnvVars: string[]
  optionalEnvVars: string[]
  setupHints: string[]
  keyStatusLabel: string
  keyStatusColor: string
  isLocalQWeather: boolean
}

export function mcpServerAddress(url: string, endpoint?: string) {
  return `${url || ''}${endpoint || ''}`
}

export function mcpSourceLabel(server: Pick<McpServerStatus, 'source' | 'localOverride'>) {
  if (server.source === 'LOCAL' && server.localOverride) {
    return '本地覆盖'
  }
  if (server.source === 'LOCAL') {
    return '本地配置'
  }
  return '内置配置'
}

export function mcpSourceColor(server: Pick<McpServerStatus, 'source' | 'localOverride'>) {
  if (server.source === 'LOCAL' && server.localOverride) {
    return 'purple'
  }
  if (server.source === 'LOCAL') {
    return 'green'
  }
  return 'blue'
}

export function normalizeToolNames(input: string | string[] | undefined | null): string[] {
  const raw = Array.isArray(input) ? input.join('\n') : input || ''
  const names: string[] = []
  raw
    .split(/[\n,，;；]/)
    .map(name => name.trim())
    .filter(Boolean)
    .forEach(name => {
      if (!names.includes(name)) {
        names.push(name)
      }
    })
  return names
}

export function toolsText(tools: string[] | undefined | null) {
  return normalizeToolNames(tools || []).join('\n')
}

export function validateMcpServerConfig(server: McpServerConfig) {
  const id = server.id?.trim()
  if (id && !/^[a-zA-Z0-9][a-zA-Z0-9_-]{1,63}$/.test(id)) {
    return '服务 ID 只能包含字母、数字、下划线和短横线，长度为 2-64。'
  }
  const url = server.url?.trim()
  if (!url) {
    return '服务地址不能为空。'
  }
  if (!/^https?:\/\/\S+$/i.test(url)) {
    return '服务地址必须以 http:// 或 https:// 开头。'
  }
  if (hasInlineSecret(url)) {
    return 'Key/Token 参数必须使用 ${ENV_NAME} 占位符。'
  }
  const endpoint = server.sseEndpoint?.trim() || '/sse'
  if (!endpoint.startsWith('/')) {
    return 'SSE Endpoint 必须以 / 开头。'
  }
  if (hasInlineSecret(endpoint)) {
    return 'Key/Token 参数必须使用 ${ENV_NAME} 占位符。'
  }
  return ''
}

export function testResultSummary(result: McpConnectionTestResult | undefined) {
  if (!result) {
    return ''
  }
  if (result.connected) {
    return `连接成功，发现 ${result.toolCount} 个工具。`
  }
  return result.error || '连接失败。'
}

export type ParseMcpJsonResult =
  | { ok: true; value: Record<string, unknown> }
  | { ok: false; error: string }

export function parseMcpJsonObject(text: string): ParseMcpJsonResult {
  try {
    const value = JSON.parse(text || '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return { ok: false, error: '请输入 JSON 对象。' }
    }
    return { ok: true, value: value as Record<string, unknown> }
  } catch (err: any) {
    return { ok: false, error: err?.message || 'JSON 格式不正确。' }
  }
}

export function prettyMcpJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2)
}

export function mcpToolExampleInput(toolName: string): Record<string, unknown> {
  if (toolName === 'weather_now') {
    return {
      location: '上海',
      lang: 'zh',
      unit: 'm',
    }
  }
  return {}
}

export function invocationResultSummary(result: McpToolInvocationResult | null | undefined) {
  if (!result) {
    return ''
  }
  if (result.error) {
    return result.error
  }
  return `调用完成，用时 ${result.durationMs} ms。`
}

const knownTools: Record<string, Omit<McpToolDisplay, 'name'>> = {
  weather_now: {
    label: '实时天气查询',
    description: '查询城市、和风 Location ID 或经纬度的实时天气。',
  },
  maps_weather: {
    label: '天气查询',
    description: '查询城市天气信息。',
  },
  maps_geo: {
    label: '地址解析',
    description: '将地址转换为经纬度。',
  },
  maps_regeo: {
    label: '逆地址解析',
    description: '将经纬度转换为地址。',
  },
  maps_text_search: {
    label: '关键词搜索',
    description: '按关键词搜索地点。',
  },
  maps_around_search: {
    label: '周边搜索',
    description: '按位置搜索周边地点。',
  },
}

export function mcpServerDisplay(server: McpServerStatus): McpServerDisplay {
  const tools = toolDisplays(server)
  const isLocalQWeather = localQWeatherServer(server, tools)

  if (!server.configuredEnabled) {
    return {
      serviceName: server.description || 'MCP 服务',
      serviceSummary: '这个 MCP 服务当前未启用。',
      tools: [],
      requiredEnvVars: [],
      optionalEnvVars: [],
      setupHints: [],
      keyStatusLabel: '未启用',
      keyStatusColor: 'default',
      isLocalQWeather,
    }
  }

  const requiredEnvVars = requiredEnvVarsFor(server, isLocalQWeather)

  if (isLocalQWeather) {
    return {
      serviceName: '本地和风天气 MCP',
      serviceSummary: '通过本地 MCP 服务调用和风天气真实 API，向对话提供实时天气工具。',
      tools,
      requiredEnvVars,
      optionalEnvVars: ['QWEATHER_API_HOST'],
      setupHints: [
        '通过 .local/mcp-keys.json 或当前 PowerShell 会话设置 QWEATHER_API_KEY',
        '启动 tools/local-qweather-mcp，确认 http://127.0.0.1:18090/health 返回 ok',
        '后端启动后点击重载工具，再执行连接测试和 weather_now 调试',
        '如果 Key 无效、供应商限流或网络失败，保留错误提示，不使用示例天气',
      ],
      ...keyStatus(server, requiredEnvVars),
      isLocalQWeather,
    }
  }

  return {
    serviceName: server.description || 'MCP 服务',
    serviceSummary: server.description || '外部 MCP 工具服务。',
    tools,
    requiredEnvVars,
    optionalEnvVars: [],
    setupHints: requiredEnvVars.length
      ? requiredEnvVars.map(name => `配置 ${name} 后刷新状态`)
      : [],
    ...keyStatus(server, requiredEnvVars),
    isLocalQWeather,
  }
}

function toolDisplays(server: McpServerStatus): McpToolDisplay[] {
  return uniqueNames(server.allowedTools || []).map(name => {
    const known = knownTools[name]
    return {
      name,
      label: known?.label || name,
      description: known?.description || '来自 MCP 服务配置的工具。',
    }
  })
}

function requiredEnvVarsFor(server: McpServerStatus, isLocalQWeather: boolean): string[] {
  const names = uniqueNames([
    ...(server.requiredEnvVars || []),
    ...(server.keyEnvName ? [server.keyEnvName] : []),
    ...(isLocalQWeather ? ['QWEATHER_API_KEY'] : []),
  ])
  return names
}

function localQWeatherServer(server: McpServerStatus, tools: McpToolDisplay[]): boolean {
  const text = `${server.description || ''} ${server.url || ''}`
  return tools.some(tool => tool.name === 'weather_now') || /qweather|和风天气|和风/i.test(text)
}

function keyStatus(server: McpServerStatus, requiredEnvVars: string[]) {
  if (!requiredEnvVars.length) {
    return {
      keyStatusLabel: '无需额外 Key',
      keyStatusColor: 'default',
    }
  }

  if (server.keyConfigured === true) {
    return {
      keyStatusLabel: 'Key 已配置',
      keyStatusColor: 'green',
    }
  }

  if (server.keyConfigured === false) {
    return {
      keyStatusLabel: '缺少 Key',
      keyStatusColor: 'orange',
    }
  }

  return {
    keyStatusLabel: 'Key 状态未知',
    keyStatusColor: 'default',
  }
}

function uniqueNames(names: string[]): string[] {
  return Array.from(new Set(
    names
      .map(name => name?.trim())
      .filter((name): name is string => Boolean(name))
  ))
}

function hasInlineSecret(value: string) {
  return /[?&](key|token|api[_-]?key|access[_-]?key)=(?!\$\{)[^&\s]+/i.test(value)
}

export interface ModelScopeMcpParseResult {
  ok: boolean
  id?: string
  type?: string
  url?: string
  error?: string
}

export function parseModelScopeJson(text: string): ModelScopeMcpParseResult {
  try {
    const root = JSON.parse(text || '{}')
    if (!root || typeof root !== 'object' || Array.isArray(root)) {
      return { ok: false, error: '请输入 JSON 对象。' }
    }

    const mcpServers = root.mcpServers
    if (!mcpServers || typeof mcpServers !== 'object' || Array.isArray(mcpServers)) {
      return { ok: false, error: 'JSON 中缺少 mcpServers 对象。' }
    }

    const entries = Object.entries(mcpServers)
    if (entries.length === 0) {
      return { ok: false, error: 'mcpServers 为空。' }
    }

    const [key, value] = entries[0]
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return { ok: false, error: `mcpServers.${key} 不是有效对象。` }
    }

    const serverConfig = value as Record<string, unknown>
    const url = typeof serverConfig.url === 'string' ? serverConfig.url.trim() : ''
    if (!url) {
      return { ok: false, error: `mcpServers.${key}.url 不能为空。` }
    }

    if (!/^https?:\/\/.+/i.test(url)) {
      return { ok: false, error: `mcpServers.${key}.url 格式不正确。` }
    }

    const type = typeof serverConfig.type === 'string' ? serverConfig.type.trim() : 'sse'

    return { ok: true, id: key, type, url }
  } catch (err: any) {
    return { ok: false, error: err?.message || 'JSON 格式不正确。' }
  }
}
