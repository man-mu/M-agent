import type { AxiosInstance, AxiosRequestConfig } from 'axios'
import axios from 'axios'
import { message } from 'ant-design-vue'

type ApiEnvelope<T = unknown> = {
  code?: number
  status?: string
  message?: string
  data?: T
  report_information?: T
  session_history?: T
}

export type RequestConfig = AxiosRequestConfig & {
  silentError?: boolean
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_BASE_URL || '',
  timeout: 30_000,
  withCredentials: false,
})

service.interceptors.response.use(
  response => response,
  error => {
    const payload = error.response?.data
    const errorMessage =
      payload?.message ||
      payload?.error ||
      error.message ||
      '网络请求失败，请确认后端服务已启动'
    if (!(error.config as RequestConfig | undefined)?.silentError) {
      message.error(errorMessage)
    }
    return Promise.reject(error)
  },
)

function unwrap<T>(payload: ApiEnvelope<T> | T): T {
  if (!payload || typeof payload !== 'object') {
    return payload as T
  }

  const envelope = payload as ApiEnvelope<T>
  if (typeof envelope.code === 'number') {
    if (envelope.code >= 200 && envelope.code < 300) {
      return envelope.data as T
    }
    throw new Error(envelope.message || '请求失败')
  }

  if (envelope.status === 'success') {
    if ('report_information' in envelope) {
      return envelope.report_information as T
    }
    if ('session_history' in envelope) {
      return envelope.session_history as T
    }
  }

  if (envelope.status === 'error' || envelope.status === 'notfound') {
    throw new Error(envelope.message || '请求失败')
  }

  return payload as T
}

export async function apiRequest<T = unknown>(config: RequestConfig): Promise<T> {
  const response = await service(config)
  return unwrap<T>(response.data)
}

export function get<T = unknown>(url: string, params?: unknown): Promise<T> {
  return apiRequest<T>({ method: 'GET', url, params })
}

export function post<T = unknown>(url: string, data?: unknown): Promise<T> {
  return apiRequest<T>({ method: 'POST', url, data })
}

export function put<T = unknown>(url: string, data?: unknown): Promise<T> {
  return apiRequest<T>({ method: 'PUT', url, data })
}

export function del<T = unknown>(url: string, params?: unknown): Promise<T> {
  return apiRequest<T>({ method: 'DELETE', url, params })
}

export default service
