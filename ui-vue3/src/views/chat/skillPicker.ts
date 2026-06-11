import type { SkillDefinition } from '@/services/api/skills'

export function findSkillTrigger(text: string) {
  const match = text.match(/(^|\s)@([\w-]*)$/)
  if (!match) {
    return null
  }
  return { query: match[2] || '' }
}

export function filterSkillCandidates(skills: SkillDefinition[], query: string, limit = 6) {
  const normalizedQuery = query.trim().toLowerCase()
  const results = skills
    .filter(skill => skill.enabled)
    .filter(skill =>
      !normalizedQuery
      || skill.name.toLowerCase().includes(normalizedQuery)
      || skill.description.toLowerCase().includes(normalizedQuery),
    )
  // 有查询条件时截断，无查询条件时全部展示
  return normalizedQuery ? results.slice(0, limit) : results
}

export function replaceSkillTrigger(text: string, name: string) {
  return text.replace(/(^|\s)@([\w-]*)$/, (_match, prefix) => `${prefix}@${name} `)
}
