import { get, post, del } from '@/utils/request'
import { apiRequest } from '@/utils/request'

export interface RagDocumentItem {
  id: string
  fileName: string
  chunks: number
  uploadedAt: string
}

export interface RagDocumentListResponse {
  documents: RagDocumentItem[]
}

export interface GlobalUploadResult {
  fileName: string
  chunks: number
  scope: string
}

export interface UserProfileData {
  profile_summary: string
  expertise_level: string
  detail_preference: string
  style_preference: string
  manual_fields: string[]
  updated_at: string
  has_profile: boolean
}

export interface UserProfileUpdateRequest {
  profile_summary?: string
  expertise_level?: string
  detail_preference?: string
  style_preference?: string
  manual_fields: string[]
}

class KnowledgeService {
  async uploadGlobalDocument(file: File): Promise<GlobalUploadResult> {
    const data = new FormData()
    data.append('file', file, file.name)
    data.append('session_id', '__global__')
    data.append('user_id', 'global')

    const payload = await apiRequest<Record<string, unknown>>({
      method: 'POST',
      url: '/api/rag/upload?scope=global',
      data,
    })
    return {
      fileName: (payload?.file_name as string) || file.name,
      chunks: (payload?.chunks as number) || 0,
      scope: (payload?.scope as string) || 'global',
    }
  }

  async listGlobalDocuments(limit = 50): Promise<RagDocumentItem[]> {
    const payload = await get<RagDocumentListResponse>(`/api/rag/documents?scope=global&limit=${limit}`)
    return payload?.documents || []
  }

  async deleteGlobalDocument(id: string): Promise<void> {
    await del<void>(`/api/rag/documents/${id}?scope=global`)
  }

  async getUserProfile(): Promise<UserProfileData> {
    return get<UserProfileData>('/api/user-profile')
  }

  async updateUserProfile(request: UserProfileUpdateRequest): Promise<UserProfileData> {
    return apiRequest<UserProfileData>({
      method: 'PUT',
      url: '/api/user-profile',
      data: request,
    })
  }

  async resetManualFields(): Promise<{ message: string }> {
    return post<{ message: string }>('/api/user-profile/reset')
  }
}

export default new KnowledgeService()
