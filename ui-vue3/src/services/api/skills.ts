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

export interface SkillHealthCheck {
  name: string
  healthy: boolean
  message: string
}

export interface SkillDependencyHealth {
  name: string
  type: string
  available: boolean
  message: string
  matchedServers: string[]
  requiredEnvVars: string[]
  keyConfigured?: boolean | null
}

export interface SkillHealthResult {
  name: string
  healthy: boolean
  status: 'HEALTHY' | 'DEGRADED' | string
  checks: SkillHealthCheck[]
  dependencies: SkillDependencyHealth[]
  validatedAt: string
}

export interface SkillInvocationRecord {
  id: string
  skillName: string
  source: string
  invokedAt: string
  success: boolean
  input: Record<string, unknown>
  output: string
  error: string
  durationMs: number
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

  health(name: string): Promise<SkillHealthResult> {
    return get<SkillHealthResult>(`/api/skills/${name}/health`)
  }

  validate(name: string): Promise<SkillHealthResult> {
    return post<SkillHealthResult>(`/api/skills/${name}/validate`)
  }

  invocations(name: string, limit = 20): Promise<SkillInvocationRecord[]> {
    return get<SkillInvocationRecord[]>(`/api/skills/${name}/invocations?limit=${limit}`)
  }
}

export default new SkillService()
