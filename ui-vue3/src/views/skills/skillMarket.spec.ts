import { describe, expect, it } from 'vitest'
import type { SkillDefinition } from '@/services/api/skills'
import {
  filterSkillMarket,
  isBuiltinSkill,
  skillCategoryLabel,
  skillDisplayTitle,
  skillHealthColor,
  skillHealthLabel,
  invocationSourceLabel,
  skillListLabel,
  skillPackageTypeLabel,
  skillSourceLabel,
  skillStatusLabel,
  skillStorageLabel,
} from './skillMarket'

const builtinSkill: SkillDefinition = {
  name: 'weather-now',
  displayName: '实时天气',
  description: '查询实时天气',
  version: '1.0.0',
  enabled: true,
  dependencies: ['mcp-qweather'],
  tags: ['weather', 'mcp'],
  packageType: 'PROMPT',
  storageLocation: 'BUILTIN',
}

const localSkill: SkillDefinition = {
  name: 'phase3-summary',
  description: '总结导入的材料',
  version: '1.2.0',
  enabled: false,
  category: 'research',
  author: 'Local Team',
  dependencies: [],
  tags: ['summary'],
  packageType: 'PROMPT',
  storageLocation: 'LOCAL',
}

describe('skill market helpers', () => {
  it('labels builtin and local skills without exposing raw paths', () => {
    expect(isBuiltinSkill(builtinSkill)).toBe(true)
    expect(skillSourceLabel(builtinSkill)).toBe('内置')
    expect(skillStorageLabel(builtinSkill)).toBe('内置内容目录')

    expect(isBuiltinSkill(localSkill)).toBe(false)
    expect(skillSourceLabel(localSkill)).toBe('本地')
    expect(skillStorageLabel(localSkill)).toBe('本地市场目录')
  })

  it('formats metadata labels for the market list and drawer', () => {
    expect(skillDisplayTitle(builtinSkill)).toBe('实时天气')
    expect(skillCategoryLabel(builtinSkill)).toBe('未分类')
    expect(skillPackageTypeLabel(localSkill)).toBe('Prompt')
    expect(skillStatusLabel(localSkill)).toBe('停用')
    expect(skillListLabel(['mcp-qweather', 'weather_now'])).toBe('mcp-qweather、weather_now')
    expect(skillListLabel([])).toBe('无')
  })

  it('formats health and invocation labels', () => {
    expect(skillHealthLabel(null)).toBe('未检查')
    expect(skillHealthColor(null)).toBe('default')
    expect(skillHealthLabel({
      name: 'weather-now',
      healthy: true,
      status: 'HEALTHY',
      checks: [],
      dependencies: [],
      validatedAt: '2026-06-05T00:00:00Z',
    })).toBe('健康')
    expect(skillHealthColor({
      name: 'weather-now',
      healthy: false,
      status: 'DEGRADED',
      checks: [],
      dependencies: [],
      validatedAt: '2026-06-05T00:00:00Z',
    })).toBe('orange')
    expect(invocationSourceLabel({
      id: '1',
      skillName: 'weather-now',
      source: 'EXPLICIT',
      invokedAt: '2026-06-05T00:00:00Z',
      success: true,
      input: {},
      output: 'ok',
      error: '',
      durationMs: 1,
    })).toBe('显式调用')
  })

  it('filters installed and local market views by status and searchable metadata', () => {
    const skills = [builtinSkill, localSkill]

    expect(filterSkillMarket(skills, { tab: 'installed' })).toHaveLength(2)
    expect(filterSkillMarket(skills, { tab: 'market' })).toEqual([localSkill])
    expect(filterSkillMarket(skills, { tab: 'installed', status: 'enabled' })).toEqual([builtinSkill])
    expect(filterSkillMarket(skills, { tab: 'installed', keyword: 'local team' })).toEqual([localSkill])
    expect(filterSkillMarket(skills, { tab: 'installed', keyword: 'weather mcp' })).toEqual([builtinSkill])
  })
})
