export type QueryParams = Record<string, string | number | undefined>

export interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  query?: QueryParams
  signal?: AbortSignal
  public?: boolean
}

export interface HttpOptions {
  baseUrl: string
  fetch: typeof globalThis.fetch
  getSession: () => { accessToken: string; version: number } | null
  onUnauthorized: (version: number) => void
}

/*
职责：向页面提供可辨认的 API、网络和响应格式错误。
概览：保留 HTTP 状态与服务端错误码，页面据此选择重新登录、无权限或重试。
*/
export class ApiError extends Error {
  /*
  行为：建立请求错误并保留状态、错误码和原始原因。
  实现：继承 Error，网络错误使用状态 0，响应错误使用实际 HTTP 状态。
  */
  constructor(
    message: string,
    public readonly status = 0,
    public readonly code = 'NETWORK_ERROR',
    options?: ErrorOptions,
  ) {
    super(message, options)
    this.name = 'ApiError'
  }
}

/*
行为：识别网络输入中的普通对象。
实现：排除 null 和数组，为响应解析提供类型收窄。
*/
export function isRecord(input: unknown): input is Record<string, unknown> {
  return typeof input === 'object' && input !== null && !Array.isArray(input)
}

/*
行为：把未知异常转换为页面可读提示。
实现：沿用 Error 的说明，其他异常显示统一失败说明。
*/
export function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请重试'
}

/*
行为：创建一个只负责传输、响应信封和认证失效通知的请求入口。
实现：使用 fetch，公开请求不附加令牌，401 只通知发起请求时的会话版本。
*/
export function createHttpClient(options: HttpOptions) {
  const baseUrl = options.baseUrl.replace(/\/+$/, '')

  /*
  行为：发送 API 请求并返回成功信封中的 data。
  实现：编码查询参数，解析响应一次，按 HTTP 与业务 code 双重判断结果。
  */
  async function request(path: string, requestOptions: RequestOptions = {}): Promise<unknown> {
    const session = requestOptions.public ? null : options.getSession()
    if (!requestOptions.public && !session) {
      throw new ApiError('登录已失效，请重新登录', 401, 'UNAUTHENTICATED')
    }
    const params = new URLSearchParams()
    for (const [name, item] of Object.entries(requestOptions.query || {})) {
      if (item !== undefined) params.set(name, String(item))
    }
    const suffix = params.size ? `?${params.toString()}` : ''
    const headers = new Headers({ Accept: 'application/json' })
    if (session) headers.set('Authorization', `Bearer ${session.accessToken}`)
    if (requestOptions.body !== undefined) headers.set('Content-Type', 'application/json')

    let response: Response
    try {
      response = await options.fetch(`${baseUrl}${path}${suffix}`, {
        method: requestOptions.method || 'GET',
        headers,
        body: requestOptions.body === undefined ? undefined : JSON.stringify(requestOptions.body),
        signal: requestOptions.signal,
      })
    } catch (error) {
      if (requestOptions.signal?.aborted) throw error
      throw new ApiError('无法连接服务，请检查服务地址或网络后重试', 0, 'NETWORK_ERROR', {
        cause: error,
      })
    }

    // 认证失效依据 HTTP 状态，HTML 错误页也不能让失效会话继续使用。
    if (response.status === 401 && session) options.onUnauthorized(session.version)

    let payload: unknown
    try {
      payload = await response.json()
    } catch (error) {
      if (requestOptions.signal?.aborted) throw error
      throw new ApiError(
        response.status === 401 ? '登录已失效，请重新登录' : '服务返回格式异常，请联系接口负责人',
        response.status,
        'INVALID_RESPONSE',
        { cause: error },
      )
    }

    if (!isRecord(payload) || typeof payload.code !== 'string') {
      throw new ApiError('服务返回格式异常，请联系接口负责人', response.status, 'INVALID_RESPONSE')
    }
    if (!response.ok || payload.code !== 'OK') {
      const message =
        typeof payload.message === 'string' && payload.message.trim()
          ? payload.message
          : response.status === 403
            ? '当前账号无权访问管理数据'
            : response.status === 401
              ? '登录已失效，请重新登录'
              : '请求失败，请重试'
      throw new ApiError(message, response.status, payload.code)
    }
    if (!('data' in payload)) {
      throw new ApiError(
        '响应缺少 data 字段，请联系接口负责人',
        response.status,
        'INVALID_RESPONSE',
      )
    }
    return payload.data
  }

  return { request }
}

export type HttpClient = ReturnType<typeof createHttpClient>
