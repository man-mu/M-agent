import { get } from '@/utils/request'

export interface AppCapabilities {
  skillEnabled: boolean
  ragEnabled: boolean
  mcpEnabled: boolean
}

export const disabledCapabilities: AppCapabilities = {
  skillEnabled: false,
  ragEnabled: false,
  mcpEnabled: false,
}

class AppService {
  getCapabilities(): Promise<AppCapabilities> {
    return get<AppCapabilities>('/api/app/capabilities')
  }
}

export default new AppService()
