import { ApiError, httpStatusMessage } from './errors.ts'
import type { ApiResponse } from './types.ts'

export interface HttpRequestOptions {
  url: string
  method: 'GET' | 'POST'
  data?: Record<string, unknown>
  header?: Record<string, string>
  timeout: number
}

export interface HttpResponseLike {
  statusCode: number
  data: unknown
}

export type HttpTransport = (options: HttpRequestOptions) => Promise<HttpResponseLike>

/** 默认传输层：uni.request 包装成 Promise。测试时注入假传输层即可脱离小程序环境。 */
export const uniTransport: HttpTransport = (options) =>
  new Promise<HttpResponseLike>((resolve, reject) => {
    uni.request({
      url: options.url,
      method: options.method,
      data: options.data,
      header: options.header,
      timeout: options.timeout,
      success: (res: { statusCode: number; data: unknown }) => {
        resolve({ statusCode: res.statusCode, data: res.data })
      },
      fail: (err: { errMsg?: string }) => {
        reject(err)
      },
    })
  })

export interface ClientOptions {
  baseUrl: string
  transport?: HttpTransport
  timeout?: number
}

export interface ClientRequestOptions {
  query?: Record<string, string | number | undefined | null>
  method?: 'GET' | 'POST'
  data?: Record<string, unknown>
  header?: Record<string, string>
}

export interface ApiClient {
  baseUrl: string
  get<T>(path: string, options?: ClientRequestOptions): Promise<T>
}

export function buildUrl(
  baseUrl: string,
  path: string,
  query?: Record<string, string | number | undefined | null>,
): string {
  const base = baseUrl.replace(/\/+$/, '')
  const suffix = path.startsWith('/') ? path : '/' + path
  const pairs: string[] = []
  if (query) {
    for (const key of Object.keys(query)) {
      const value = query[key]
      if (value === undefined || value === null || value === '') continue
      pairs.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(value)))
    }
  }
  const search = pairs.length ? '?' + pairs.join('&') : ''
  return base + suffix + search
}

function normalizeTransportError(error: unknown): ApiError {
  const errMsg =
    error && typeof error === 'object' && 'errMsg' in error
      ? String((error as { errMsg?: unknown }).errMsg ?? '')
      : error instanceof Error
        ? error.message
        : ''
  if (errMsg.toLowerCase().includes('timeout')) {
    return new ApiError('timeout', '请求超时，请重试')
  }
  return new ApiError('network', '网络连接失败，请确认服务端已启动')
}

/** 解开 { code, data, message } 包装；非 2xx 或 code != OK 一律抛 ApiError。 */
function unwrap<T>(response: HttpResponseLike): T {
  const ok = response.statusCode >= 200 && response.statusCode < 300
  const body = response.data as ApiResponse<T> | null | undefined

  if (body && typeof body === 'object' && typeof body.code === 'string') {
    if (ok && body.code === 'OK') {
      return body.data as T
    }
    const kind = ok ? 'business' : 'http'
    const message = body.message || httpStatusMessage(response.statusCode)
    throw new ApiError(kind, message, { status: response.statusCode, code: body.code })
  }

  // 2xx 但响应体不是契约形状：视为服务端错误，避免页面拿到 undefined 崩溃
  if (ok) {
    throw new ApiError('http', '服务端返回数据格式异常，请稍后重试', {
      status: response.statusCode,
      code: 'MALFORMED_RESPONSE',
    })
  }

  throw new ApiError('http', httpStatusMessage(response.statusCode), { status: response.statusCode })
}

export function createClient(options: ClientOptions): ApiClient {
  const transport = options.transport ?? uniTransport
  const timeout = options.timeout ?? 10000

  return {
    baseUrl: options.baseUrl,
    async get<T>(path: string, request: ClientRequestOptions = {}): Promise<T> {
      const url = buildUrl(options.baseUrl, path, request.query)
      let response: HttpResponseLike
      try {
        response = await transport({
          url,
          method: request.method ?? 'GET',
          data: request.data,
          header: request.header,
          timeout,
        })
      } catch (error) {
        throw normalizeTransportError(error)
      }
      return unwrap<T>(response)
    },
  }
}
