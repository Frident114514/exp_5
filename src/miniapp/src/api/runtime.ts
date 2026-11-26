import { createHttpPostApi } from './httpPostApi'
import type { HttpRequestOptions, HttpTransport } from './httpPostApi'
import { createMockPostApi } from './mockPostApi'
import {
  isSessionExpired,
  readAuthSession,
  saveAuthSession,
  type AuthSession,
  type AuthUser,
} from './session'
import {
  PostApiError,
  type ApiEnvelope,
  type ApiErrorCode,
  type PostApi,
} from './types'

const DEMO_USER: AuthUser = {
  id: 6006,
  nickname: 'M06 演示用户',
  role: 'USER',
}

const apiBaseUrl = (
  import.meta.env.VITE_MINIAPP_API_BASE_URL || 'http://127.0.0.1:8080/api/v1'
).replace(/\/+$/, '')

/** 真实接口为默认模式；测试或离线演示必须显式设置 VITE_MINIAPP_USE_MOCK=true。 */
export const isUsingMock = import.meta.env.VITE_MINIAPP_USE_MOCK === 'true'
export const mockPostApi = createMockPostApi({ currentUser: DEMO_USER })

function toApiErrorCode(value: string, status: number): ApiErrorCode {
  const known: ApiErrorCode[] = [
    'VALIDATION_ERROR',
    'UNAUTHENTICATED',
    'FORBIDDEN',
    'NOT_FOUND',
    'CONFLICT',
    'NETWORK_ERROR',
    'DEPENDENCY_UNAVAILABLE',
  ]
  if (known.includes(value as ApiErrorCode)) return value as ApiErrorCode
  if (status === 400) return 'VALIDATION_ERROR'
  if (status === 401) return 'UNAUTHENTICATED'
  if (status === 403) return 'FORBIDDEN'
  if (status === 404) return 'NOT_FOUND'
  if (status === 409) return 'CONFLICT'
  return 'DEPENDENCY_UNAVAILABLE'
}

function responseMessage(status: number): string {
  if (status === 400) return '请求参数有误'
  if (status === 401) return '登录状态已失效，请重新登录'
  if (status === 403) return '没有权限执行此操作'
  if (status === 404) return '内容不存在或已删除'
  if (status === 409) return '操作冲突，请刷新后重试'
  if (status >= 500) return '服务端暂时不可用，请稍后重试'
  return '请求失败，请稍后重试'
}

function absoluteUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) return path
  return apiBaseUrl + (path.startsWith('/') ? path : '/' + path)
}

const uniTransport: HttpTransport = {
  request<T>(options: HttpRequestOptions): Promise<ApiEnvelope<T>> {
    const session = readAuthSession()
    const header: Record<string, string> = {}
    if (session?.accessToken) {
      header.Authorization = `Bearer ${session.accessToken}`
    }

    return new Promise<ApiEnvelope<T>>((resolve, reject) => {
      uni.request({
        url: absoluteUrl(options.path),
        method: options.method as unknown as UniApp.RequestOptions['method'],
        data: options.data as unknown as UniApp.RequestOptions['data'],
        header,
        timeout: 10000,
        success: (response) => {
          const status = response.statusCode
          const body = response.data as ApiEnvelope<T> | null | undefined

          if (status >= 200 && status < 300) {
            if (!body || typeof body.code !== 'string') {
              reject(new PostApiError('DEPENDENCY_UNAVAILABLE', '服务端返回数据格式异常'))
              return
            }
            resolve(body)
            return
          }

          const code = toApiErrorCode(body?.code ?? '', status)
          reject(new PostApiError(code, body?.message || responseMessage(status)))
        },
        fail: (error) => {
          const message = String(error.errMsg || '').toLowerCase().includes('timeout')
            ? '请求超时，请重试'
            : '网络连接失败，请确认服务端已启动'
          reject(new PostApiError('NETWORK_ERROR', message))
        },
      })
    })
  },
}

export const realPostApi: PostApi = createHttpPostApi(uniTransport)

export function getPostApi(): PostApi {
  return isUsingMock ? mockPostApi : realPostApi
}

/** 仅 Mock 模式使用；真实模式没有登录用户时返回 null。 */
export function getCurrentUser(): AuthUser | null {
  if (isUsingMock) return getMockUser()
  return readAuthSession()?.user ?? null
}

function getMockUser(): AuthUser {
  return mockPostApi.getCurrentUser()
}

/**
 * 真实模式优先读取 M05 登录态；只有 token 没有用户资料时调用 GET /users/me。
 */
export async function ensureCurrentUser(): Promise<AuthUser> {
  if (isUsingMock) return getMockUser()

  const session = readAuthSession()
  if (!session) {
    throw new PostApiError('UNAUTHENTICATED', '请先登录后再管理动态')
  }
  if (isSessionExpired(session)) {
    throw new PostApiError('UNAUTHENTICATED', '登录状态已过期，请重新登录')
  }
  if (session.user) return session.user

  const response = await uniTransport.request<AuthUser>({
    path: '/users/me',
    method: 'GET',
  })
  if (response.code !== 'OK' || !response.data) {
    throw new PostApiError('UNAUTHENTICATED', response.message || '无法读取登录用户')
  }

  const nextSession: AuthSession = { ...session, user: response.data }
  saveAuthSession(nextSession)
  return response.data
}