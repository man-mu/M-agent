import type { McpServerStatus } from '@/services/api/app'

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
        'PowerShell 当前会话设置 QWEATHER_API_KEY',
        '如使用专属和风 Host，可设置 QWEATHER_API_HOST',
        '启动 tools/local-qweather-mcp 后刷新状态',
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
