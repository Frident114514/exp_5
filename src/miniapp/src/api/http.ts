/**
 * 统一请求层 · 维护人：M05
 *
 * 所有页面和业务接口模块都必须通过这里发请求，不要直接调用 uni.request：
 *   - 自动拼接服务端地址（config/env）并附加 `Authorization: Bearer <token>`；
 *   - 解析统一响应外壳 `{ code, data, message }`，成功只返回 `data`；
 *   - 失败统一抛出 `ApiError { status, code, message }`，`message` 可直接展示；
 *   - 401 / `UNAUTHENTICATED` 时清理登录态并引导重新登录（登录页自身的失败除外）；
 *   - 网络异常归一为 `NETWORK_ERROR`，避免页面各自判断。
 *
 * 服务端未就绪时可用 `VITE_MINIAPP_USE_MOCK=true` 命中 `api/mock.ts` 的假数据，
 * 模拟结果只能用于调页面，不能作为联调证据。
 */

import { buildApiUrl, resolveUseMock } from '@/config/env'
import { authStore } from '@/stores/auth'
import { ROUTES } from '@/config/routes'
import { redirectToLogin } from '@/utils/navigate'
import { findMock } from './mock'
import {
  API_ERROR_CODES,
  SUCCESS_CODE,
  type ApiEnvelope,
  type ApiError,
  type ApiErrorCode,
} from './types'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

/** 默认超时：图片/视频上传请单独指定更长的超时。 */
export const DEFAULT_TIMEOUT_MS = 15000

export type QueryValue = string | number | boolean | undefined | null

export interface RequestOptions {
  /** 相对 API 基础地址的路径，例如 `/auth/login`。 */
  path: string
  method?: HttpMethod
  /** JSON 请求体。 */
  data?: unknown
  /** 查询参数，值为 undefined / null / 空字符串时不拼接。 */
  query?: Record<string, QueryValue>
  headers?: Record<string, string>
  /** 是否附加登录令牌，默认 true；登录、注册等公开接口可传 false。 */
  withAuth?: boolean
  timeout?: number
}

/** 是否是本请求层抛出的接口错误。 */
export function isApiError(value: unknown): value is ApiError {
  if (!value || typeof value !== 'object') {
    return false
  }
  const record = value as Partial<ApiError>
  return typeof record.status === 'number' && typeof record.code === 'string' && typeof record.message === 'string'
}

export function createApiError(status: number, code: ApiError['code'], message: string): ApiError {
  return { status, code, message }
}

/** 把查询参数拼成 `?a=1&b=2`，跳过空值。 */
export function buildQueryString(query?: Record<string, QueryValue>): string {
  if (!query) {
    return ''
  }
  const parts: string[] = []
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null) {
      continue
    }
    const text = String(value)
    if (text.length === 0) {
      continue
    }
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(text)}`)
  }
  return parts.length === 0 ? '' : `?${parts.join('&')}`
}

const KNOWN_ERROR_CODES = Object.values(API_ERROR_CODES) as string[]

function normalizeCode(raw: unknown): ApiError['code'] {
  return typeof raw === 'string' && KNOWN_ERROR_CODES.includes(raw)
    ? (raw as ApiErrorCode)
    : 'UNKNOWN_ERROR'
}

/** 没有可读 message 时按状态码给出中文兜底文案。 */
export function fallbackMessage(status: number): string {
  if (status === 0) {
    return '网络连接失败，请检查网络后重试'
  }
  if (status === 400) {
    return '提交的内容不符合要求，请检查后重试'
  }
  if (status === 401) {
    return '登录状态已失效，请重新登录'
  }
  if (status === 403) {
    return '没有权限执行该操作'
  }
  if (status === 404) {
    return '请求的内容不存在或已被删除'
  }
  if (status === 409) {
    return '操作冲突，请刷新后重试'
  }
  if (status === 413) {
    return '文件超过大小限制'
  }
  if (status === 415) {
    return '不支持的文件格式'
  }
  if (status >= 500) {
    return '服务器繁忙，请稍后重试'
  }
  return '请求失败，请稍后重试'
}

function toApiError(status: number, body: unknown): ApiError {
  const envelope = (body ?? {}) as Partial<ApiEnvelope<unknown>>
  const message =
    typeof envelope.message === 'string' && envelope.message.trim().length > 0
      ? envelope.message.trim()
      : fallbackMessage(status)
  return createApiError(status, normalizeCode(envelope.code), message)
}

/** 把"HTTP 状态 + 响应体"转成可展示的 ApiError；上传等非走 request() 的请求也能复用。 */
export function apiErrorFromResponse(status: number, body: unknown): ApiError {
  return toApiError(status, body)
}

/** 是否为登录态失效类错误。 */
export function isUnauthorizedError(error: ApiError): boolean {
  return error.status === 401 || error.code === API_ERROR_CODES.UNAUTHENTICATED
}

/** 登录、注册自身的失败不应触发登出与跳转。 */
const PUBLIC_AUTH_PATHS = new Set(['/auth/login', '/auth/register'])

/** 连续 401 时只跳一次登录页，避免多个并发请求各跳一次。 */
let redirectingUntil = 0

function currentPageRoute(): string {
  if (typeof getCurrentPages !== 'function') {
    return ''
  }
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] as {
    route?: string
    $page?: { fullPath?: string }
  } | undefined
  return current?.route ? `/${current.route}` : ''
}

function shouldRedirect(path: string): boolean {
  if (PUBLIC_AUTH_PATHS.has(path)) {
    return false
  }
  const route = currentPageRoute()
  if (route === ROUTES.login.path || route === ROUTES.register.path) {
    return false
  }
  return Date.now() > redirectingUntil
}

function handleUnauthorized(path: string, tokenBeforeRequest: string): void {
  // 只有"本来带着令牌"的请求失败才说明令牌过期；未登录时的 401 交给页面自行提示。
  if (tokenBeforeRequest.length === 0) {
    authStore.clear()
    return
  }
  authStore.clear()
  if (!shouldRedirect(path)) {
    return
  }
  redirectingUntil = Date.now() + 2000
  redirectToLogin()
}

/** 供非 request() 通道（如媒体上传）复用同一套 401 处理。 */
export function notifyUnauthorized(path: string, tokenBeforeRequest: string): void {
  handleUnauthorized(path, tokenBeforeRequest)
}

/** 测试用：清除 401 跳转节流标记，避免执行顺序影响断言。 */
export function resetUnauthorizedThrottle(): void {
  redirectingUntil = 0
}

function handleResponse<T>(
  status: number,
  body: unknown,
  path: string,
  tokenBeforeRequest: string
): { ok: true; data: T } | { ok: false; error: ApiError } {
  const envelope = body as Partial<ApiEnvelope<T>> | null
  if (
    status >= 200 &&
    status < 300 &&
    envelope &&
    typeof envelope === 'object' &&
    envelope.code === SUCCESS_CODE
  ) {
    return { ok: true, data: envelope.data as T }
  }
  const error = toApiError(status, body)
  if (status === 401 || error.code === API_ERROR_CODES.UNAUTHENTICATED) {
    handleUnauthorized(path, tokenBeforeRequest)
  }
  return { ok: false, error }
}

/** 发起请求并返回业务数据；失败抛出 `ApiError`。 */
export async function request<T>(options: RequestOptions): Promise<T> {
  const method = options.method ?? 'GET'
  const path = options.path
  const tokenBeforeRequest = options.withAuth === false ? '' : authStore.token

  if (resolveUseMock()) {
    const mock = findMock(method, path)
    if (mock) {
      return (await mock({
        method,
        path,
        data: options.data,
        query: options.query ?? {},
        token: tokenBeforeRequest,
      })) as T
    }
  }

  const url = buildApiUrl(`${path}${buildQueryString(options.query)}`)
  const header: Record<string, string> = { Accept: 'application/json', ...(options.headers ?? {}) }
  if (tokenBeforeRequest) {
    header.Authorization = `Bearer ${tokenBeforeRequest}`
  }
  if (options.data !== undefined && header['Content-Type'] === undefined) {
    header['Content-Type'] = 'application/json'
  }

  return new Promise<T>((resolve, reject) => {
    // 小程序 request 的类型声明里没有 PATCH（api.md 的编辑接口用的是 PATCH），
    // 这里保留真实方法名透传，运行时行为以微信基础库为准，联调时需实测（见契约文档 Q7）。
    const requestOptions = {
      url,
      method,
      data: options.data,
      header,
      timeout: options.timeout ?? DEFAULT_TIMEOUT_MS,
      success: (res: { statusCode: number; data: unknown }) => {
        const result = handleResponse<T>(res.statusCode, res.data, path, tokenBeforeRequest)
        if (result.ok) {
          resolve(result.data)
        } else {
          reject(result.error)
        }
      },
      fail: () => {
        reject(createApiError(0, 'NETWORK_ERROR', fallbackMessage(0)))
      },
    } as unknown as UniApp.RequestOptions

    uni.request(requestOptions)
  })
}

export function get<T>(path: string, query?: Record<string, QueryValue>, options: Partial<RequestOptions> = {}): Promise<T> {
  return request<T>({ ...options, path, method: 'GET', query })
}

export function post<T>(path: string, data?: unknown, options: Partial<RequestOptions> = {}): Promise<T> {
  return request<T>({ ...options, path, method: 'POST', data })
}

export function put<T>(path: string, data?: unknown, options: Partial<RequestOptions> = {}): Promise<T> {
  return request<T>({ ...options, path, method: 'PUT', data })
}

export function patch<T>(path: string, data?: unknown, options: Partial<RequestOptions> = {}): Promise<T> {
  return request<T>({ ...options, path, method: 'PATCH', data })
}

export function del<T>(path: string, query?: Record<string, QueryValue>, options: Partial<RequestOptions> = {}): Promise<T> {
  return request<T>({ ...options, path, method: 'DELETE', query })
}
