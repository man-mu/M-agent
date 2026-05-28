import { describe, expect, it } from 'vitest'
import { filterSkillCandidates, findSkillTrigger, replaceSkillTrigger } from './skillPicker'
import type { SkillDefinition } from '@/services/api/skills'

const skills: SkillDefinition[] = [
  {
    name: 'code-review',
    description: '审查代码',
    enabled: true,
  },
  {
    name: 'location-analyzer',
    description: '分析地点',
    enabled: true,
  },
  {
    name: 'disabled-skill',
    description: '不可用',
    enabled: false,
  },
]

describe('chat skill picker helpers', () => {
  it('detects a trailing @skill trigger', () => {
    expect(findSkillTrigger('@cod')).toEqual({ query: 'cod' })
    expect(findSkillTrigger('请使用 @location')).toEqual({ query: 'location' })
    expect(findSkillTrigger('hello @code now')).toBeNull()
  })

  it('filters enabled skills by name or description', () => {
    expect(filterSkillCandidates(skills, 'code').map(skill => skill.name)).toEqual(['code-review'])
    expect(filterSkillCandidates(skills, '地点').map(skill => skill.name)).toEqual(['location-analyzer'])
    expect(filterSkillCandidates(skills, '').map(skill => skill.name)).toEqual(['code-review', 'location-analyzer'])
  })

  it('replaces the active trigger with the selected skill name', () => {
    expect(replaceSkillTrigger('@cod', 'code-review')).toBe('@code-review ')
    expect(replaceSkillTrigger('请使用 @loc', 'location-analyzer')).toBe('请使用 @location-analyzer ')
  })
})
