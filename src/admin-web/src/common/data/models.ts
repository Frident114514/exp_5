export type UserRole = 'ADMIN' | 'USER'
export type UserStatus = 'ACTIVE' | 'DISABLED'

/** 登录身份由服务端提供，status 和 createdAt 取决于本人信息接口。 */
export interface UserInfo {
  id: number
  account: string
  nickname: string
  role: UserRole
  status?: UserStatus
  createdAt?: string
}

export interface UserRow extends UserInfo {
  status: UserStatus
  createdAt: string
}

export interface LoginResult {
  accessToken: string
  /** 带时区的 ISO 8601 时间，表示服务端令牌到期时刻。 */
  expiresAt: string
  user: UserInfo
}

export interface PostRow {
  id: number
  author: { id: number; nickname: string }
  text: string
  media: { id: number; type: 'IMAGE' | 'VIDEO'; url: string }[]
  tags: string[]
  createdAt: string
  updatedAt: string
  rating: { average: number | null; count: number }
}

export interface PageResult<T> {
  items: T[]
  /** API 页码从 1 开始，每页 1–50 条。 */
  page: number
  pageSize: number
  total: number
}

export interface UserQuery {
  keyword?: string
  page: number
  pageSize: number
}

export interface PostQuery extends UserQuery {
  authorId?: number
  /** 日期按北京时间自然日解释，格式为 YYYY-MM-DD，包含起止日。 */
  from?: string
  to?: string
  tag?: string
}
