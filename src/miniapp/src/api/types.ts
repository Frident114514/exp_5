/**
 * 接口通用类型 · 维护人：M05
 *
 * 严格按 specs/contracts/api.md 的约定描述响应外壳与账号相关对象：
 *   成功：{ "code": "OK", "data": ..., "message": "" }
 *   失败：{ "code": "ERROR_CODE", "data": null, "message": "可读说明" }
 * 字段名变更必须先改契约文档再改这里，并通知调用方（M06/M07/M08）。
 */

/** 统一响应外壳。 */
export interface ApiEnvelope<T> {
  code: string
  data: T | null
  message: string
}

/** 与 api.md「错误与权限边界」表一致的错误码。 */
export const API_ERROR_CODES = {
  VALIDATION_ERROR: 'VALIDATION_ERROR',
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  FORBIDDEN: 'FORBIDDEN',
  NOT_FOUND: 'NOT_FOUND',
  CONFLICT: 'CONFLICT',
  FILE_TOO_LARGE: 'FILE_TOO_LARGE',
  UNSUPPORTED_MEDIA_TYPE: 'UNSUPPORTED_MEDIA_TYPE',
} as const

export type ApiErrorCode = (typeof API_ERROR_CODES)[keyof typeof API_ERROR_CODES]

/** 归一化后的接口错误，供页面直接展示 message。 */
export interface ApiError {
  /** HTTP 状态码，网络异常时为 0。 */
  status: number
  /** 服务端错误码；网络异常时使用 `NETWORK_ERROR`。 */
  code: ApiErrorCode | 'NETWORK_ERROR' | 'UNKNOWN_ERROR'
  /** 可直接展示给用户的中文说明。 */
  message: string
}

/** 角色：普通用户 / 管理员，见 data-model.md `users.role`。 */
export type UserRole = 'USER' | 'ADMIN'

/** 账号状态：正常 / 已禁用。 */
export type UserStatus = 'ACTIVE' | 'DISABLED'

/** 登录用户与本人资料共用的对象形状。 */
export interface AuthUser {
  id: number
  account: string
  nickname: string
  role: UserRole
  status?: UserStatus
  createdAt?: string
}

/** `POST /auth/login` 请求体。 */
export interface LoginRequest {
  account: string
  password: string
}

/** `POST /auth/login` 响应数据：令牌有效期 12 小时。 */
export interface LoginResult {
  accessToken: string
  /** 带时区的 ISO 8601 字符串。 */
  expiresAt: string
  user: AuthUser
}

/** `POST /auth/register` 请求体；角色固定由服务端写为 USER。 */
export interface RegisterRequest {
  account: string
  password: string
  nickname: string
}

/** `POST /auth/register` 响应数据：用户 ID 与基本资料。 */
export type RegisterResult = AuthUser

/**
 * `PUT /users/me/password` 请求体。
 * 字段名 `oldPassword` / `newPassword` 为 M05 提出的约定，待 M01 在实现认证接口时确认。
 */
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/** 分页查询参数：`page` 从 1 开始，`pageSize` 默认 10、最大 50。 */
export interface PageQuery {
  page?: number
  pageSize?: number
}

/** 分页响应数据。 */
export interface PageResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export const PAGE_QUERY_DEFAULTS = {
  page: 1,
  pageSize: 10,
  maxPageSize: 50,
} as const

export function isAdmin(user: AuthUser | null | undefined): boolean {
  return user?.role === 'ADMIN'
}

/** 服务端成功响应的固定 code。 */
export const SUCCESS_CODE = 'OK'

/** 登录令牌有效期（小时），与 api.md 一致，用于本地过期判断。 */
export const TOKEN_EXPIRE_HOURS = 12

/** 本地缓存的登录态键名，统一由 stores/auth.ts 读写；改版时递增版本号使旧缓存失效。 */
export const AUTH_STORAGE_KEY = 'moments.auth.v1'

/* ==========================================================================
 * 动态、媒体、互动对象（M06/M07 直接使用，字段以 specs/contracts/api.md 为准）
 * ========================================================================== */

/** 动态作者摘要。 */
export interface Author {
  id: number
  nickname: string
}

/** 媒体类型：图片 / MP4 视频。 */
export type MediaType = 'IMAGE' | 'VIDEO'

/** 动态中的媒体项，`url` 为相对地址，展示前用 config/env 的 absoluteMediaUrl 补全。 */
export interface MediaItem {
  id: number
  type: MediaType
  url: string
}

/** 评分摘要：无评分时 average 为 null、count 为 0，页面不得显示伪造的 0 分。 */
export interface RatingSummary {
  average: number | null
  count: number
}

/** 动态（列表与详情共用）。 */
export interface Post {
  id: number
  author: Author
  text: string
  media: MediaItem[]
  tags: string[]
  createdAt: string
  rating: RatingSummary
}

/** 发布 / 编辑动态的请求体：文本与媒体至少一项，标签最多 5 个。 */
export interface PostPayload {
  text?: string
  mediaIds?: number[]
  tags?: string[]
}

/** PATCH 语义：只提交需要修改的字段，省略的字段保持不变。 */
export type PostPatchPayload = Partial<PostPayload>

/** 公开列表查询：日期按北京时间自然日筛选，`from` 不得晚于 `to`。 */
export interface PostListQuery extends PageQuery {
  from?: string
  to?: string
  tag?: string
}

/** 评论。 */
export interface Comment {
  id: number
  author: Author
  content: string
  createdAt: string
}

/** 提交评论：1–500 字符。 */
export interface CommentPayload {
  content: string
}

/** 提交评分：1–5 的整数，再次提交会更新原评分。 */
export interface RatingPayload {
  score: number
}

/** 评分提交后的返回：最新摘要与本人评分。 */
export interface RatingResult {
  rating: RatingSummary
  myScore: number | null
}

/** 媒体上传成功后的返回，`mediaId` 用于发布动态时的 `mediaIds`。 */
export interface MediaUploadResult {
  mediaId: number
  type: MediaType
  url: string
}

/** 统一判断任意错误对象是否为接口错误（用于页面区分网络/业务错误）。 */
export interface ApiErrorLike {
  status: number
  code: string
  message: string
}

