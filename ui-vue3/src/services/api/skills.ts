import { del, get, post, put, apiRequest } from '@/utils/request'

export interface SkillDefinition {
  name: string
  description: string
  version?: string
  enabled: boolean
  parameters?: Record<string, unknown>
  dependencies?: string[]
  displayName?: string
  category?: string
  author?: string
  homepage?: string
  tags?: string[]
  packageType?: 'PROMPT' | 'JAR'
  source?: string
  storageLocation?: 'BUILTIN' | 'LOCAL'
  installed_at?: string
  updated_at?: string
  created_at?: string
  inputSchemaJson?: string
}

export interface SkillDetail {
  definition: SkillDefinition
  promptTemplate: string
}

export interface CreateSkillRequest {
  definition: SkillDefinition
  promptTemplate: string
}

export interface SkillPackageImportResult {
  name: string
  version?: string
  packageType?: 'PROMPT' | 'JAR'
  storageLocation?: 'BUILTIN' | 'LOCAL'
  enabled: boolean
  message?: string
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

  importPackage(file: File): Promise<SkillPackageImportResult> {
    const data = new FormData()
    data.append('file', file)
    return apiRequest<SkillPackageImportResult>({
      method: 'POST',
      url: '/api/skills/packages/import',
      data,
    })
  }

  exportPackage(name: string): Promise<Blob> {
    return apiRequest<Blob>({
      method: 'GET',
      url: `/api/skills/${name}/export`,
      responseType: 'blob',
    })
  }

  reload(name: string): Promise<SkillDefinition> {
    return post<SkillDefinition>(`/api/skills/${name}/reload`)
  }

  uninstallPackage(name: string): Promise<void> {
    return del<void>(`/api/skills/packages/${name}`)
  }
}

export default new SkillService()
