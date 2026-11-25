/**
 * 服务端地址配置 · 维护人：M05
 *
 * 变量名与 src/.env.example 保持一致，不得在页面里写死服务端地址：
 *   VITE_MINIAPP_API_BASE_URL   http://127.0.0.1:8080/api/v1
 *   VITE_MINIAPP_MEDIA_ORIGIN   http://127.0.0.1:8080
 *
 * 微信小程序不支持运行时读取 .env，编译时由 Vite 注入；
 * 修改后必须重启 `npm run dev:mp-weixin` 或重新构建才会生效。
 */

/** 未配置环境变量时使用的本机演示地址，仅用于开发。 */
export const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080/api/v1'
export const DEFAULT_MEDIA_ORIGIN = 'http://127.0.0.1:8080'

/** 去掉首尾空白和末尾多余的 `/`，空值回退到 fallback。 */
export function normalizeBaseUrl(raw: string | undefined | null, fallback: string): string {
  const trimmed = (raw ?? '').trim()
  const value = trimmed.length === 0 ? fallback : trimmed
  return value.replace(/\/+$/, '')
}

function readEnv(key: 'VITE_MINIAPP_API_BASE_URL' | 'VITE_MINIAPP_MEDIA_ORIGIN'): string | undefined {
  const env = import.meta.env
  return env ? env[key] : undefined
}

/** 生效的 API 基础地址（已归一化，不带末尾 `/`）。 */
export function resolveApiBaseUrl(): string {
  return normalizeBaseUrl(readEnv('VITE_MINIAPP_API_BASE_URL'), DEFAULT_API_BASE_URL)
}

/** 生效的媒体源地址（已归一化，不带末尾 `/`）。 */
export function resolveMediaOrigin(): string {
  return normalizeBaseUrl(readEnv('VITE_MINIAPP_MEDIA_ORIGIN'), DEFAULT_MEDIA_ORIGIN)
}

/** 把接口相对路径拼成完整请求地址；`path` 可带或不带前导 `/`。 */
export function buildApiUrl(path: string, base: string = resolveApiBaseUrl()): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizeBaseUrl(base, DEFAULT_API_BASE_URL)}${normalizedPath}`
}

/** 媒体内容地址：`/api/v1/media/{mediaId}/content`，可直接用于 image/video 的 src。 */
export function buildMediaUrl(mediaId: number, base: string = resolveApiBaseUrl()): string {
  if (!Number.isInteger(mediaId) || mediaId <= 0) {
    throw new Error(`mediaId 必须是正整数，收到：${String(mediaId)}`)
  }
  return buildApiUrl(`/media/${mediaId}/content`, base)
}

/**
 * 服务端返回的媒体 `url`（如 `/api/v1/media/33/content`）已包含 `/api/v1`，
 * 因此这里只补 `协议 + 主机`，不能再拼一次 API 基础路径。
 */
export function absoluteMediaUrl(url: string, origin: string = resolveMediaOrigin()): string {
  if (/^https?:\/\//i.test(url)) {
    return url
  }
  const normalizedOrigin = normalizeBaseUrl(origin, DEFAULT_MEDIA_ORIGIN)
  const path = url.startsWith('/') ? url : `/${url}`
  return `${normalizedOrigin}${path}`
}
