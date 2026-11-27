/**
 * 小程序端跨端数据契约
 *
 * 本文件合并了 M05（账号/请求层）、M06（作者流程）、M07（广场/详情）三套历史定义：
 *   - M06 的命名（ApiEnvelope、PageView、PostView、PostApi、CreatePostRequest…）保留为独立声明或别名；
 *   - M07 的命名（ApiResponse、PageResult、PostSummary、PostDetail、MediaItem、API_ERROR_CODES）保留；
 *   - 两者形状相同的类型用 `type` 别名统一，字段以 specs/contracts/api.md 为准，不为迁就命名而改服务端响应。
 * 修改字段前先改契约文档，再同步这里与三个使用方。
 */

/* ------------------------------------------------------------------ 响应信封 */

/** M06 用：已确认 code === SUCCESS_CODE 之后读取 data。 */
export interface ApiEnvelope<T> {
  code: string
  data: T
  message: string
}

/** M07 用：失败时 data 为 null，调用方需自行判空。 */
export interface ApiResponse<T> {
  code: string
  data: T | null
  message: string
}

export const SUCCESS_CODE = 'OK'

/* ------------------------------------------------------------------ 账号（M05） */

export type UserRole = 'USER' | 'ADMIN'
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

/** `POST /auth/register` 请求体；角色由服务端固定为 USER。 */
export interface RegisterRequest {
  account: string
  password: string
  nickname: string
}

export type RegisterResult = AuthUser

/** `PUT /users/me/password` 请求体（字段名待 M01 最终确认，见契约 Q1）。 */
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/** 登录令牌有效期（小时）。 */
export const TOKEN_EXPIRE_HOURS = 12

/** 本地登录态缓存键；改版时递增版本号使旧缓存失效。 */
export const AUTH_STORAGE_KEY = 'moments.auth.v1'

/* ------------------------------------------------------------------ 动态与媒体 */

/** 作者摘要（M07 命名）。 */
export interface Author {
  id: number
  nickname: string
}

/** M06 对作者摘要的历史命名。 */
export type PostAuthor = Author

export type MediaType = 'IMAGE' | 'VIDEO'

/** 媒体项（M07 命名）；`url` 形如 /api/v1/media/33/content。 */
export interface MediaItem {
  id: number
  type: MediaType
  url: string
}

/** M06 对媒体项的历史命名。 */
export type PostMedia = MediaItem

/** 评分摘要：无评分时 average 为 null、count 为 0，页面显示“暂无评分”。 */
export interface RatingSummary {
  average: number | null
  count: number
}

/**
 * 动态（列表项）。列表与详情当前同形状；`updatedAt` 为必填，
 * 保留给编辑页与后续“编辑时间”展示。
 */
export interface PostSummary {
  id: number
  author: Author
  text: string
  media: MediaItem[]
  tags: string[]
  /** ISO 8601，北京时间的展示由 utils/format 统一处理。 */
  createdAt: string
  updatedAt: string
  rating: RatingSummary
}

/** 详情（`GET /posts/{postId}`），当前与列表项同形状。 */
export type PostDetail = PostSummary

/** M06 的历史命名（作者流程里大量使用）。 */
export type PostView = PostSummary

/** 通用别名，供新代码使用。 */
export type Post = PostSummary

/* ------------------------------------------------------------------ 分页 */

export interface PageResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

/** M06 的历史命名。 */
export type PageView<T> = PageResult<T>

/** 分页查询参数：page 从 1 开始，pageSize 默认 10、最大 50。 */
export interface PageQuery {
  page?: number
  pageSize?: number
}

export const PAGE_QUERY_DEFAULTS = {
  page: 1,
  pageSize: 10,
  maxPageSize: 50,
} as const

/** 公开列表查询：日期按北京时间自然日筛选，`from` 不得晚于 `to`。 */
export interface PostListQuery extends PageQuery {
  from?: string
  to?: string
  tag?: string
}

/* ------------------------------------------------------------------ 写操作（M06） */

/** `POST /posts` 成功返回。 */
export interface CreatedPost {
  id: number
}

export interface CreatePostRequest {
  text: string
  mediaIds: number[]
  tags: string[]
}

/** PATCH 语义：省略的字段保持不变，不能为了改文字而顺带发空数组。 */
export interface EditPostRequest {
  text?: string
  mediaIds?: number[]
  tags?: string[]
}

/** M06 作者流程使用的接口集合（真实/模拟实现都实现它）。 */
export interface PostApi {
  create(input: CreatePostRequest): Promise<CreatedPost>
  edit(postId: number, input: EditPostRequest): Promise<PostView>
  delete(postId: number): Promise<void>
  detail(postId: number): Promise<PostView>
  mine(page: number, pageSize: number): Promise<PageView<PostView>>
}

/* ------------------------------------------------------------------ 互动与媒体上传（后续阶段） */

export interface Comment {
  id: number
  author: Author
  content: string
  createdAt: string
}

export interface CommentPayload {
  content: string
}

export interface RatingPayload {
  score: number
}

export interface RatingResult {
  rating: RatingSummary
  myScore: number | null
}

/** `POST /media` 成功返回，`mediaId` 用于发布动态时的 mediaIds。 */
export interface MediaUploadResult {
  mediaId: number
  type: MediaType
  url: string
}

/* ------------------------------------------------------------------ 错误 */

/** 与 specs/contracts/api.md「错误与权限边界」一致的服务端错误码。 */
export const API_ERROR_CODES = {
  VALIDATION_ERROR: 'VALIDATION_ERROR',
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  FORBIDDEN: 'FORBIDDEN',
  NOT_FOUND: 'NOT_FOUND',
  CONFLICT: 'CONFLICT',
  FILE_TOO_LARGE: 'FILE_TOO_LARGE',
  UNSUPPORTED_MEDIA_TYPE: 'UNSUPPORTED_MEDIA_TYPE',
} as const

/**
 * 前端可遇到的错误码：服务端 7 个 + 请求层归一出来的网络/依赖/未知错误。
 * 用并集而不是拆分，避免各成员加错误码时互相冲突。
 */
export type ApiErrorCode =
  | (typeof API_ERROR_CODES)[keyof typeof API_ERROR_CODES]
  | 'NETWORK_ERROR'
  | 'UNKNOWN_ERROR'
  | 'DEPENDENCY_UNAVAILABLE'

/** M05 请求层抛出的错误；`message` 可直接展示。 */
export interface ApiError {
  /** HTTP 状态码，网络异常时为 0。 */
  status: number
  code: ApiErrorCode
  message: string
}

/** M06 作者流程使用的错误类型（保持 instanceof 语义）。 */
export class PostApiError extends Error {
  readonly code: ApiErrorCode

  constructor(code: ApiErrorCode, message: string) {
    super(message)
    this.name = 'PostApiError'
    this.code = code
  }
}

/* ------------------------------------------------------------------ 工具 */

export function isAdmin(user: AuthUser | null | undefined): boolean {
  return user?.role === 'ADMIN'
}
