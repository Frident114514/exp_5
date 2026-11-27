/**
 * 小程序服务端地址与请求常量（合并 M05 / M07 两份配置）
 *
 * 兼容两种历史写法，读到一个即可：
 *   VITE_API_BASE_URL          M07 使用（见 .env.local.example）
 *   VITE_MINIAPP_API_BASE_URL  M05 使用（见 src/.env.example）
 * 两者都含 `/api/v1`，拼接时不会重复追加。
 *
 * 微信小程序在编译期注入环境变量，改完必须重启 `pnpm dev:mp-weixin` 或重新构建。
 */

/** 未配置环境变量时的本机默认值，仅用于开发。 */
export const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080/api/v1'
export const DEFAULT_MEDIA_ORIGIN = 'http://127.0.0.1:8080'

/** 单次请求超时（毫秒）；媒体上传在 api/media.ts 单独放宽。 */
export const REQUEST_TIMEOUT = 15000

/** 列表每页条数，契约上限 50、默认 10。 */
export const DEFAULT_PAGE_SIZE = 10

/** 去掉首尾空白和末尾多余的 `/`，空值回退到 fallback。 */
export function normalizeBaseUrl(raw: string | undefined | null, fallback: string): string {
  const trimmed = (raw ?? '').trim()
  const value = trimmed.length === 0 ? fallback : trimmed
  return value.replace(/\/+$/, '')
}

function readEnv(key: string): string | undefined {
  const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env
  return env?.[key]
}

/** 生效的 API 基础地址（已归一化，不带末尾 `/`）。 */
export function resolveApiBaseUrl(): string {
  const raw = readEnv('VITE_API_BASE_URL') ?? readEnv('VITE_MINIAPP_API_BASE_URL')
  return normalizeBaseUrl(raw, DEFAULT_API_BASE_URL)
}

/** 生效的媒体源地址；未单独配置时从 API 地址推出（去掉 `/api/v1`）。 */
export function resolveMediaOrigin(): string {
  const raw = readEnv('VITE_MINIAPP_MEDIA_ORIGIN')
  if (raw && raw.trim().length > 0) {
    return normalizeBaseUrl(raw, DEFAULT_MEDIA_ORIGIN)
  }
  return resolveApiBaseUrl().replace(/\/api\/v1$/, '')
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
