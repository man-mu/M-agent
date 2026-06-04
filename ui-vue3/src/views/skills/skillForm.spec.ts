import { describe, expect, it } from 'vitest'
import {
  deriveExampleParameters,
  normalizeDependencies,
  parameterCount,
  parseJsonObject,
  renderPromptPreview,
  validateHomepage,
  validateSkillName,
} from './skillForm'

describe('skill form helpers', () => {
  it('validates skill names before they become file paths', () => {
    expect(validateSkillName('code-review_2')).toBe('')
    expect(validateSkillName('bad/name')).toContain('仅支持')
    expect(validateSkillName('')).toContain('不能为空')
  })

  it('validates optional homepage URLs for market metadata', () => {
    expect(validateHomepage('')).toBe('')
    expect(validateHomepage(' https://example.com/skill ')).toBe('')
    expect(validateHomepage('ftp://example.com')).toContain('http:// 或 https://')
  })

  it('normalizes dependencies from text and removes duplicates', () => {
    expect(normalizeDependencies('amap\nweb, amap；search')).toEqual(['amap', 'web', 'search'])
  })

  it('parses only JSON objects for schema editing', () => {
    expect(parseJsonObject('{"type":"object"}')).toMatchObject({
      ok: true,
      value: { type: 'object' },
    })
    expect(parseJsonObject('[]')).toMatchObject({ ok: false })
    expect(parseJsonObject('{bad')).toMatchObject({ ok: false })
  })

  it('counts properties and required-only parameters', () => {
    expect(parameterCount({
      type: 'object',
      properties: {
        topic: { type: 'string' },
      },
      required: ['topic', 'city'],
    })).toBe(2)
  })

  it('derives example params from defaults, types, and required fields', () => {
    expect(deriveExampleParameters({
      type: 'object',
      properties: {
        topic: { type: 'string', default: '前端体验' },
        retries: { type: 'integer' },
        strict: { type: 'boolean' },
      },
      required: ['city'],
    })).toEqual({
      topic: '前端体验',
      retries: 1,
      strict: true,
      city: '示例city',
    })
  })

  it('renders prompt placeholders and reports missing params', () => {
    expect(renderPromptPreview('研究 {{topic}} in {{city}}', { topic: 'MCP' })).toEqual({
      text: '研究 MCP in {{city}}',
      missing: ['city'],
    })
  })
})
