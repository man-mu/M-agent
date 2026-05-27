import { apiRequest, del, get } from '@/utils/request'

export interface ResearchReport {
  thread_id: string
  session_id: string
  query: string
  report: string
  status: string
  error_message?: string
  created_at?: string
  updated_at?: string
}

class ReportService {
  getReport(threadId: string): Promise<string | ResearchReport> {
    return apiRequest<string | ResearchReport>({
      method: 'GET',
      url: `/api/reports/${threadId}`,
      silentError: true,
    })
  }

  exists(threadId: string): Promise<boolean> {
    return get<boolean>(`/api/reports/${threadId}/exists`)
  }

  getReportsBySession(sessionId: string): Promise<ResearchReport[]> {
    return get<ResearchReport[]>(`/api/reports/session/${sessionId}`)
  }

  deleteReport(threadId: string): Promise<void> {
    return del<void>(`/api/reports/${threadId}`)
  }
}

export default new ReportService()
