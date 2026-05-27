import { del, get, post, put, apiRequest } from '@/utils/request'

export interface SkillDefinition {
  name: string
  description: string
  version?: string
  enabled: boolean
  parameters?: Record<string, unknown>
  dependencies?: string[]
}

export interface SkillDetail {
  definition: SkillDefinition
  promptTemplate: string
}

export interface CreateSkillRequest {
  definition: SkillDefinition
  promptTemplate: string
}

class SkillService {
  list(): Promise<SkillDefinition[]> {
    return get<SkillDefinition[]>('/api/skills')
  }

  get(name: string): Promise<SkillDetail> {
    return get<SkillDetail>(`/api/skills/${name}`)
  }

  create(request: CreateSkillRequest): Promise<SkillDefinition> {
    return post<SkillDefinition>('/api/skills', request)
  }

  update(name: string, request: CreateSkillRequest): Promise<SkillDefinition> {
    return put<SkillDefinition>(`/api/skills/${name}`, request)
  }

  delete(name: string): Promise<void> {
    return del<void>(`/api/skills/${name}`)
  }

  toggle(name: string): Promise<SkillDefinition> {
    return apiRequest<SkillDefinition>({
      method: 'PATCH',
      url: `/api/skills/${name}/toggle`,
    })
  }
}

export default new SkillService()
