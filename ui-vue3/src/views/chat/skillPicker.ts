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
  return skills
    .filter(skill => skill.enabled)
    .filter(skill =>
      !normalizedQuery
      || skill.name.toLowerCase().includes(normalizedQuery)
      || skill.description.toLowerCase().includes(normalizedQuery),
    )
    .slice(0, limit)
}

export function replaceSkillTrigger(text: string, name: string) {
  return text.replace(/(^|\s)@([\w-]*)$/, (_match, prefix) => `${prefix}@${name} `)
}
