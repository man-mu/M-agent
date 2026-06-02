export type JsonObject = Record<string, unknown>

export const SKILL_NAME_PATTERN = /^[A-Za-z0-9_-]+$/

export function defaultParametersSchema(): JsonObject {
  return {
    type: 'object',
    properties: {},
    required: [],
  }
}

export function validateSkillName(name: string) {
  const trimmed = name.trim()
  if (!trimmed) {
    return 'Skill 名称不能为空。'
  }
  if (!SKILL_NAME_PATTERN.test(trimmed)) {
    return 'Skill 名称仅支持字母、数字、短横线和下划线。'
  }
  return ''
}

export function normalizeDependencies(input: string | string[] | undefined | null) {
  const raw = Array.isArray(input) ? input.join('\n') : input || ''
  const seen = new Set<string>()
  return raw
    .split(/[\n,，;；]+/)
    .map(item => item.trim())
    .filter(item => {
      if (!item || seen.has(item)) {
        return false
      }
      seen.add(item)
      return true
    })
}

export function formatDependencies(dependencies: string[] | undefined | null) {
  return (dependencies || []).join('\n')
}

export type ParseJsonObjectResult =
  | { ok: true; value: JsonObject }
  | { ok: false; error: string }

export function parseJsonObject(text: string): ParseJsonObjectResult {
  const source = text.trim() || '{}'
  try {
    const parsed = JSON.parse(source)
    if (!isRecord(parsed)) {
      return {
        ok: false,
        error: '请输入 JSON 对象，例如 {"type":"object"}。',
      }
    }
    return { ok: true, value: parsed }
  } catch {
    return {
      ok: false,
      error: 'JSON 格式错误，请检查逗号、引号和括号。',
    }
  }
}

export function prettyJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2)
}

export function parameterCount(parameters: JsonObject | undefined | null) {
  if (!parameters) {
    return 0
  }
  const names = new Set<string>()
  Object.keys(schemaProperties(parameters)).forEach(name => names.add(name))
  requiredParameterNames(parameters).forEach(name => names.add(name))
  return names.size
}

export function deriveExampleParameters(parameters: JsonObject | undefined | null): JsonObject {
  const examples: JsonObject = {}
  if (!parameters) {
    return examples
  }

  const properties = schemaProperties(parameters)
  Object.entries(properties).forEach(([name, schema]) => {
    examples[name] = exampleValueForSchema(name, schema)
  })

  requiredParameterNames(parameters).forEach(name => {
    if (!(name in examples)) {
      examples[name] = `示例${name}`
    }
  })

  return examples
}

export interface PromptPreview {
  text: string
  missing: string[]
}

export function renderPromptPreview(template: string, params: JsonObject): PromptPreview {
  const missing = new Set<string>()
  const text = template.replace(/\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}/g, (_, key: string) => {
    const value = params[key]
    if (value === undefined || value === null || value === '') {
      missing.add(key)
      return `{{${key}}}`
    }
    return stringifyPreviewValue(value)
  })

  return {
    text,
    missing: Array.from(missing),
  }
}

function requiredParameterNames(parameters: JsonObject) {
  const required = parameters.required
  if (!Array.isArray(required)) {
    return []
  }
  return required.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
}

function schemaProperties(parameters: JsonObject) {
  return isRecord(parameters.properties) ? parameters.properties : {}
}

function exampleValueForSchema(name: string, schema: unknown): unknown {
  if (isRecord(schema) && Object.prototype.hasOwnProperty.call(schema, 'default')) {
    return schema.default
  }

  const type = isRecord(schema) ? schema.type : undefined
  const primaryType = Array.isArray(type) ? type[0] : type
  switch (primaryType) {
    case 'integer':
    case 'number':
      return 1
    case 'boolean':
      return true
    case 'array':
      return []
    case 'object':
      return {}
    default:
      return `示例${name}`
  }
}

function stringifyPreviewValue(value: unknown) {
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  return JSON.stringify(value)
}

function isRecord(value: unknown): value is JsonObject {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
