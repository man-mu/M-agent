import { get, post } from '@/utils/request'

export interface ProviderSummary {
  providerId: string
  displayName: string
  models: string[]
  apiKeyConfigured: boolean
}

export interface CurrentModelSelection {
  providerId: string
  providerName: string
  modelName: string
  apiKeyConfigured: boolean
}

export interface TestModelResponse {
  providerId: string
  modelName: string
  ok: boolean
}

class ModelService {
  getProviders(): Promise<ProviderSummary[]> {
    return get<ProviderSummary[]>('/api/model/providers')
  }

  getCurrent(): Promise<CurrentModelSelection> {
    return get<CurrentModelSelection>('/api/model/current')
  }

  saveApiKey(providerId: string, apiKey: string): Promise<{ providerId: string; apiKeyConfigured: boolean }> {
    return post(`/api/model/providers/${providerId}/key`, { apiKey })
  }

  switchModel(providerId: string, modelName: string): Promise<CurrentModelSelection> {
    return post<CurrentModelSelection>('/api/model/switch', { providerId, modelName })
  }

  testModel(prompt?: string): Promise<TestModelResponse> {
    return post<TestModelResponse>('/api/model/test', { prompt })
  }
}

export default new ModelService()
