import type { SkillDefinition, SkillHealthResult, SkillInvocationRecord } from '@/services/api/skills'

export function isBuiltinSkill(skill: SkillDefinition) {
  return skill.storageLocation === 'BUILTIN' || skill.source === 'builtin'
}

export function skillSourceLabel(skill: SkillDefinition) {
  return isBuiltinSkill(skill) ? '内置' : '本地'
}

export function skillSourceColor(skill: SkillDefinition) {
  return isBuiltinSkill(skill) ? 'blue' : 'green'
}

export function skillPackageTypeLabel(skill: SkillDefinition) {
  return skill.packageType === 'JAR' ? 'Jar' : 'Prompt'
}

export function skillStorageLabel(skill: SkillDefinition) {
  return isBuiltinSkill(skill) ? '内置内容目录' : '本地市场目录'
}

export function skillDisplayTitle(skill: SkillDefinition) {
  return skill.displayName || skill.name
}

export function skillCategoryLabel(skill: SkillDefinition) {
  return skill.category || '未分类'
}

export function skillListLabel(values: string[] | undefined | null) {
  return values?.length ? values.join('、') : '无'
}

export function skillStatusLabel(skill: SkillDefinition) {
  return skill.enabled ? '启用' : '停用'
}

export function skillHealthLabel(health: SkillHealthResult | undefined | null) {
  if (!health) {
    return '未检查'
  }
  return health.healthy ? '健康' : '异常'
}

export function skillHealthColor(health: SkillHealthResult | undefined | null) {
  if (!health) {
    return 'default'
  }
  return health.healthy ? 'green' : 'orange'
}

export function invocationSourceLabel(record: SkillInvocationRecord) {
  if (record.source === 'EXPLICIT') {
    return '显式调用'
  }
  if (record.source === 'TOOL') {
    return '工具调用'
  }
  return record.source || '未知'
}

export function skillSearchText(skill: SkillDefinition) {
  return [
    skill.name,
    skill.displayName,
    skill.description,
    skill.version,
    skill.category,
    skill.author,
    skill.source,
    skill.storageLocation,
    ...(skill.tags || []),
    ...(skill.dependencies || []),
  ].join(' ').toLowerCase()
}

export function filterSkillMarket(
  skills: SkillDefinition[],
  options: {
    tab: 'installed' | 'market'
    keyword?: string
    status?: 'all' | 'enabled' | 'disabled'
  },
) {
  const query = options.keyword?.trim().toLowerCase() || ''
  const status = options.status || 'all'
  return skills
    .filter(skill => options.tab === 'market' ? !isBuiltinSkill(skill) : true)
    .filter(skill => {
      if (status === 'enabled' && !skill.enabled) {
        return false
      }
      if (status === 'disabled' && skill.enabled) {
        return false
      }
      return !query || skillSearchText(skill).includes(query)
    })
}
