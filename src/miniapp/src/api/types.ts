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
