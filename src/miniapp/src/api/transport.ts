/**
 * 统一请求层与两套历史封装的接线（合并 M05/M06/M07 时新增）
 *
 * 分工：
 *   - `api/http.ts`：唯一的底层出口，负责服务端地址、Authorization 令牌、超时、网络错误、401 清理；
 *   - `authTransport`：给 M07 的 `createClient({ transport })` 用，返回 `{statusCode,data}`，由 client 解包；
 *   - `envelopeTransport`：给 M06 的 `createHttpPostApi(transport)` 用，返回完整 `{code,data,message}`，由它解包。
 *
 * 两个传输层都**不解包**，保证 data 只被上层解一次；两边都自动带登录令牌、共享 401 处理。
 */

import type { HttpTransport } from './client.ts'
import { requestRaw, type HttpMethod } from './http.ts'
import type { ApiEnvelope } from './types.ts'

/** M07：client.ts 的 HttpTransport —— 传入完整 URL，返回原始响应。 */
export const authTransport: HttpTransport = async (options) => {
  const response = await requestRaw({
    url: options.url,
    method: options.method,
    data: options.data,
    header: options.header,
    timeout: options.timeout,
  })
  return { statusCode: response.statusCode, data: response.data }
}

/** M06：httpPostApi.ts 的传输对象 —— 传入相对路径，返回完整响应信封。 */
export interface EnvelopeTransport {
  request<T>(options: { path: string; method: HttpMethod; data?: unknown }): Promise<ApiEnvelope<T>>
}

export const envelopeTransport: EnvelopeTransport = {
  async request<T>(options: {
    path: string
    method: HttpMethod
    data?: unknown
  }): Promise<ApiEnvelope<T>> {
    const response = await requestRaw({
      url: options.path,
      method: options.method,
      data: options.data,
    })
    return response.data as ApiEnvelope<T>
  },
}
