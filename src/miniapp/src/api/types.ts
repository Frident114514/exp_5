/**
 * 跨端数据契约（M02 维护口径，见 specs/contracts/api.md 与 data-model.md）。
 * 前端不得自定义字段：页面只消费这里声明的形状。
 */

/** 统一响应包装：成功 code 为 "OK"。 */
export interface ApiResponse<T> {
  code: string
  data: T | null
  message: string
}

/** 分页结果，page 从 1 开始。 */
export interface PageResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export interface Author {
  id: number
  nickname: string
}

export type MediaType = 'IMAGE' | 'VIDEO'

export interface MediaItem {
  id: number
  type: MediaType
  /** 服务端返回的内容地址，如 /api/v1/media/33/content */
  url: string
}

/** 评分摘要：无评分时 average 为 null、count 为 0。 */
export interface RatingSummary {
  average: number | null
  count: number
}

/** 列表项（GET /posts 的 items 元素）。 */
export interface PostSummary {
  id: number
  author: Author
  text: string
  media: MediaItem[]
  tags: string[]
  /**
   * ISO 8601 时间。服务端 PostView 用 Instant 序列化（UTC，如 2026-11-17T02:00:00Z），
   * 契约示例是 +08:00 偏移；两种都能被 formatDateTime 解析为设备本地时间。
   */
  createdAt: string
  /** 服务端返回的更新时间；第一周不展示，保留给后续“编辑时间”，与 main 的 PostView 保持一致为必填。 */
  updatedAt: string
  rating: RatingSummary
}

/** 详情（GET /posts/{postId}），当前与列表项同形状，后续阶段可扩展。 */
export type PostDetail = PostSummary

/** 服务端接口错误码，见 contracts/api.md。 */
export const API_ERROR_CODES = {
  VALIDATION_ERROR: 'VALIDATION_ERROR',
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  FORBIDDEN: 'FORBIDDEN',
  NOT_FOUND: 'NOT_FOUND',
  CONFLICT: 'CONFLICT',
  FILE_TOO_LARGE: 'FILE_TOO_LARGE',
  UNSUPPORTED_MEDIA_TYPE: 'UNSUPPORTED_MEDIA_TYPE',
} as const