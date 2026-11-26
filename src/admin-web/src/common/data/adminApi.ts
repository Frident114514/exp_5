import { ApiError, isRecord } from './http'
import type { HttpClient } from './http'
import type {
  LoginResult,
  PageResult,
  PostQuery,
  PostRow,
  UserInfo,
  UserQuery,
  UserRow,
} from './models'

/*
行为：拒绝不符合已约定形状的网络数据。
实现：统一抛出响应格式错误，避免把缺失字段当作空数据或查询成功。
*/
function invalidResponse(): never {
  throw new ApiError('响应字段与接口约定不一致，请联系接口负责人', 200, 'INVALID_RESPONSE')
}

/*
行为：判断 ID 或计数是否为安全整数。
实现：要求整数可精确表示，ID 下界为 1，计数下界由调用方指定。
*/
function isInteger(input: unknown, minimum = 1): input is number {
  return typeof input === 'number' && Number.isSafeInteger(input) && input >= minimum
}

/*
行为：识别接口中的带时区时间。
实现：同时检查 ISO 时间的时区后缀和浏览器解析结果。
*/
export function isTimestamp(input: unknown): input is string {
  return (
    typeof input === 'string' &&
    /T.*(?:Z|[+-]\d{2}:\d{2})$/.test(input) &&
    Number.isFinite(Date.parse(input))
  )
}

/*
行为：解析登录或本人信息中的基本资料。
实现：校验所需字段、角色和可选状态，只返回前端使用的字段。
*/
export function readUserInfo(input: unknown): UserInfo {
  if (
    !isRecord(input) ||
    !isInteger(input.id) ||
    typeof input.account !== 'string' ||
    typeof input.nickname !== 'string' ||
    (input.role !== 'ADMIN' && input.role !== 'USER') ||
    (input.status !== undefined && input.status !== 'ACTIVE' && input.status !== 'DISABLED') ||
    (input.createdAt !== undefined && !isTimestamp(input.createdAt))
  ) {
    return invalidResponse()
  }
  return {
    id: input.id,
    account: input.account,
    nickname: input.nickname,
    role: input.role,
    status: input.status,
    createdAt: input.createdAt,
  }
}

/*
行为：解析用户表格所需的状态与注册时间。
实现：在基本资料校验后要求列表专用字段完整。
*/
function readUserRow(input: unknown): UserRow {
  const user = readUserInfo(input)
  if (!user.status || !user.createdAt) return invalidResponse()
  return { ...user, status: user.status, createdAt: user.createdAt }
}

/*
行为：解析已有 M02 动态视图。
实现：检查作者、媒体、标签、时间和评分形状，保留接口原始业务字段。
*/
function readPostRow(input: unknown): PostRow {
  if (
    !isRecord(input) ||
    !isInteger(input.id) ||
    !isRecord(input.author) ||
    !isInteger(input.author.id) ||
    typeof input.author.nickname !== 'string' ||
    typeof input.text !== 'string' ||
    !Array.isArray(input.media) ||
    !Array.isArray(input.tags) ||
    !input.tags.every((tag) => typeof tag === 'string') ||
    !isTimestamp(input.createdAt) ||
    !isTimestamp(input.updatedAt) ||
    !isRecord(input.rating) ||
    !isInteger(input.rating.count, 0) ||
    (input.rating.average !== null &&
      (typeof input.rating.average !== 'number' ||
        !Number.isFinite(input.rating.average) ||
        input.rating.average < 1 ||
        input.rating.average > 5))
  ) {
    return invalidResponse()
  }
  const media = input.media.map((item: unknown): PostRow['media'][number] => {
    if (
      !isRecord(item) ||
      !isInteger(item.id) ||
      (item.type !== 'IMAGE' && item.type !== 'VIDEO') ||
      typeof item.url !== 'string'
    ) {
      return invalidResponse()
    }
    return { id: item.id, type: item.type, url: item.url }
  })
  return {
    id: input.id,
    author: { id: input.author.id, nickname: input.author.nickname },
    text: input.text,
    media,
    tags: input.tags,
    createdAt: input.createdAt,
    updatedAt: input.updatedAt,
    rating: { average: input.rating.average, count: input.rating.count },
  }
}

/*
行为：解析分页响应并核对所请求页码。
实现：验证页大小、总数和记录数组，逐条调用业务对象解析器。
*/
function readPage<T>(
  input: unknown,
  query: UserQuery,
  readItem: (item: unknown) => T,
): PageResult<T> {
  if (
    !isRecord(input) ||
    !Array.isArray(input.items) ||
    input.page !== query.page ||
    input.pageSize !== query.pageSize ||
    !isInteger(input.page) ||
    !isInteger(input.pageSize) ||
    input.pageSize > 50 ||
    !isInteger(input.total, 0) ||
    input.items.length > input.pageSize ||
    input.items.length > input.total
  ) {
    return invalidResponse()
  }
  return {
    items: input.items.map(readItem),
    page: input.page,
    pageSize: input.pageSize,
    total: input.total,
  }
}

/*
行为：建立管理后台所需的四个 API 操作。
实现：以统一请求入口访问现有契约，并在网络边界解析业务对象。
*/
export function createAdminApi(http: HttpClient) {
  /*
  行为：提交账号密码并读取登录结果。
  实现：使用公开 POST 请求，校验令牌、有效期与用户资料。
  */
  async function login(
    account: string,
    password: string,
    signal?: AbortSignal,
  ): Promise<LoginResult> {
    const result = await http.request('/auth/login', {
      method: 'POST',
      body: { account, password },
      public: true,
      signal,
    })
    if (
      !isRecord(result) ||
      typeof result.accessToken !== 'string' ||
      !result.accessToken.trim() ||
      !isTimestamp(result.expiresAt)
    ) {
      return invalidResponse()
    }
    return {
      accessToken: result.accessToken,
      expiresAt: result.expiresAt,
      user: readUserInfo(result.user),
    }
  }

  /*
  行为：向服务端复核当前登录身份。
  实现：调用本人信息端点，返回经解析的用户资料。
  */
  async function getMe(signal?: AbortSignal): Promise<UserInfo> {
    return readUserInfo(await http.request('/users/me', { signal }))
  }

  /*
  行为：按关键词与分页查询用户。
  实现：发送服务端查询条件，解析用户列表与分页，取消信号由查询状态持有。
  */
  async function listUsers(query: UserQuery, signal?: AbortSignal): Promise<PageResult<UserRow>> {
    const result = await http.request('/admin/users', { query: { ...query }, signal })
    return readPage(result, query, readUserRow)
  }

  /*
  行为：按作者、日期、标签、关键词与分页查询动态。
  实现：沿用 M02 查询参数，解析动态列表与分页。
  */
  async function listPosts(query: PostQuery, signal?: AbortSignal): Promise<PageResult<PostRow>> {
    const result = await http.request('/admin/posts', { query: { ...query }, signal })
    return readPage(result, query, readPostRow)
  }

  return { login, getMe, listUsers, listPosts }
}

export type AdminApi = ReturnType<typeof createAdminApi>
