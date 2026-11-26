/**
 * 本地模拟接口 · 维护人：M05
 *
 * 用途：M01/M02/M03/M04 的服务端尚未就绪时，让 M06/M07 能先把页面和交互跑通。
 * 开启方式：`src/miniapp/.env.local` 写 `VITE_MINIAPP_USE_MOCK=true`，重启编译。
 *
 * 严格约束：
 *   1. 只有 `api/http.ts` 在开关打开时才会命中这里，关闭开关即走真实服务端；
 *   2. 这里的数据全部是虚构样例，**不能作为联调完成或测试通过的证据**；
 *   3. 账号/密码/校验规则要与 specs/contracts/api.md 保持一致，改动需同步文档；
 *   4. 媒体地址指向 `/api/v1/media/{id}/content`，模拟模式下没有真实文件，
 *      页面必须对图片/视频加载失败显示占位，不能假设一定加载成功。
 */

import { TOKEN_EXPIRE_HOURS, type ApiError, type AuthUser, type MediaType } from './types'

type MockMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface MockContext {
  method: MockMethod
  path: string
  data?: unknown
  query: Record<string, string | number | boolean | undefined | null>
  token: string
}

type MockHandler = (ctx: MockContext, params: string[]) => unknown

/** 模拟网络延迟（毫秒），用于观察加载态；设为 0 可即时返回。 */
export const MOCK_DELAY_MS = 120

const MOCK_PREFIX = 'mock-token.'

function mockError(status: number, code: ApiError['code'], message: string): ApiError {
  return { status, code, message }
}

function nowIso(): string {
  return new Date().toISOString()
}

function futureIso(): string {
  return new Date(Date.now() + TOKEN_EXPIRE_HOURS * 3600 * 1000).toISOString()
}

/* ------------------------------------------------------------------ 账号 */

interface MockAccount extends AuthUser {
  password: string
}

const accounts: MockAccount[] = [
  {
    id: 1,
    account: 'demo',
    password: 'demo1234',
    nickname: '示例用户',
    role: 'USER',
    status: 'ACTIVE',
    createdAt: '2026-11-15T09:00:00+08:00',
  },
  {
    id: 2,
    account: 'admin',
    password: 'admin1234',
    nickname: '管理员',
    role: 'ADMIN',
    status: 'ACTIVE',
    createdAt: '2026-11-15T09:00:00+08:00',
  },
]

function toPublicUser(account: MockAccount): AuthUser {
  return {
    id: account.id,
    account: account.account,
    nickname: account.nickname,
    role: account.role,
    status: account.status,
    createdAt: account.createdAt,
  }
}

function accountFromToken(token: string): MockAccount | null {
  if (!token.startsWith(MOCK_PREFIX)) {
    return null
  }
  const account = token.slice(MOCK_PREFIX.length).split('.')[0]
  return accounts.find((item) => item.account === account) ?? null
}

function requireAccount(ctx: MockContext): MockAccount {
  const account = accountFromToken(ctx.token)
  if (!account) {
    throw mockError(401, 'UNAUTHENTICATED', '登录状态已失效，请重新登录')
  }
  return account
}

/* ------------------------------------------------------------------ 动态 */

interface MockPost {
  id: number
  author: { id: number; nickname: string }
  text: string
  media: { id: number; type: MediaType; url: string }[]
  tags: string[]
  createdAt: string
  rating: { average: number | null; count: number }
  status: 'ACTIVE' | 'DELETED'
}

let postSeq = 104
let mediaSeq = 205

const posts: MockPost[] = [
  {
    id: 101,
    author: { id: 1, nickname: '示例用户' },
    text: '周末在图书馆自习，顺手拍了两张校园的照片。',
    media: [
      { id: 201, type: 'IMAGE', url: '/api/v1/media/201/content' },
      { id: 202, type: 'IMAGE', url: '/api/v1/media/202/content' },
    ],
    tags: ['校园', '学习'],
    createdAt: '2026-11-20T10:30:00+08:00',
    rating: { average: 4.5, count: 2 },
    status: 'ACTIVE',
  },
  {
    id: 102,
    author: { id: 2, nickname: '管理员' },
    text: '社团活动记录视频，MP4 格式，约 8 MB。',
    media: [{ id: 203, type: 'VIDEO', url: '/api/v1/media/203/content' }],
    tags: ['社团', '视频'],
    createdAt: '2026-11-21T16:00:00+08:00',
    rating: { average: null, count: 0 },
    status: 'ACTIVE',
  },
  {
    id: 103,
    author: { id: 1, nickname: '示例用户' },
    text: '只有文字的一条动态，用来检查无媒体时的排版。',
    media: [],
    tags: [],
    createdAt: '2026-11-24T08:15:00+08:00',
    rating: { average: 3, count: 1 },
    status: 'ACTIVE',
  },
]

interface MockComment {
  id: number
  postId: number
  author: { id: number; nickname: string }
  content: string
  createdAt: string
}

let commentSeq = 4
const comments: MockComment[] = [
  { id: 1, postId: 101, author: { id: 2, nickname: '管理员' }, content: '照片拍得不错。', createdAt: '2026-11-20T12:00:00+08:00' },
  { id: 2, postId: 101, author: { id: 1, nickname: '示例用户' }, content: '谢谢！', createdAt: '2026-11-20T12:30:00+08:00' },
  { id: 3, postId: 102, author: { id: 1, nickname: '示例用户' }, content: '视频能正常播放吗？', createdAt: '2026-11-21T17:10:00+08:00' },
]

/** 每条动态下当前用户的评分，键为 `${postId}`。 */
const myRatings = new Map<number, number>()

function activePosts(): MockPost[] {
  return posts.filter((post) => post.status === 'ACTIVE')
}

function findPost(id: number): MockPost {
  const post = posts.find((item) => item.id === id && item.status === 'ACTIVE')
  if (!post) {
    throw mockError(404, 'NOT_FOUND', '动态不存在或已被删除')
  }
  return post
}

function paginate<T>(items: T[], query: MockContext['query']): { items: T[]; page: number; pageSize: number; total: number } {
  const page = Math.max(1, Number(query.page ?? 1) || 1)
  const pageSize = Math.min(50, Math.max(1, Number(query.pageSize ?? 10) || 10))
  const start = (page - 1) * pageSize
  return { items: items.slice(start, start + pageSize), page, pageSize, total: items.length }
}

/** 按北京时间自然日（YYYY-MM-DD）比较，含起止。 */
function dayOf(iso: string): string {
  const date = new Date(iso)
  // 北京时间为 UTC+8，用固定偏移换算，避免依赖运行环境时区。
  const beijing = new Date(date.getTime() + 8 * 3600 * 1000)
  return beijing.toISOString().slice(0, 10)
}

function readBody<T>(ctx: MockContext): T {
  return (ctx.data ?? {}) as T
}

interface MockRoute {
  method: MockMethod
  pattern: RegExp
  handler: MockHandler
}

const routes: MockRoute[] = [
  /* ---------------- 账号 ---------------- */
  {
    method: 'POST',
    pattern: /^\/auth\/register$/,
    handler: (ctx) => {
      const body = readBody<{ account?: string; password?: string; nickname?: string }>(ctx)
      const account = (body.account ?? '').trim()
      const password = body.password ?? ''
      const nickname = (body.nickname ?? '').trim()
      if (!account || !password || !nickname) {
        throw mockError(400, 'VALIDATION_ERROR', '账号、密码和昵称都不能为空')
      }
      if (accounts.some((item) => item.account === account)) {
        throw mockError(409, 'CONFLICT', '账号已被注册')
      }
      const created: MockAccount = {
        id: accounts.length + 1,
        account,
        password,
        nickname,
        role: 'USER',
        status: 'ACTIVE',
        createdAt: nowIso(),
      }
      accounts.push(created)
      return toPublicUser(created)
    },
  },
  {
    method: 'POST',
    pattern: /^\/auth\/login$/,
    handler: (ctx) => {
      const body = readBody<{ account?: string; password?: string }>(ctx)
      const account = accounts.find((item) => item.account === (body.account ?? '').trim())
      if (!account || account.password !== body.password) {
        throw mockError(401, 'UNAUTHENTICATED', '账号或密码不正确')
      }
      return {
        accessToken: `${MOCK_PREFIX}${account.account}.${Date.now()}`,
        expiresAt: futureIso(),
        user: toPublicUser(account),
      }
    },
  },
  {
    method: 'GET',
    pattern: /^\/users\/me$/,
    handler: (ctx) => toPublicUser(requireAccount(ctx)),
  },
  {
    method: 'PUT',
    pattern: /^\/users\/me\/password$/,
    handler: (ctx) => {
      const account = requireAccount(ctx)
      const body = readBody<{ oldPassword?: string; newPassword?: string }>(ctx)
      if (account.password !== (body.oldPassword ?? '')) {
        throw mockError(400, 'VALIDATION_ERROR', '原密码不正确')
      }
      if (!body.newPassword || body.newPassword.length < 8) {
        throw mockError(400, 'VALIDATION_ERROR', '新密码长度为 8–32 位')
      }
      account.password = body.newPassword
      return null
    },
  },

  /* ---------------- 动态 ---------------- */
  {
    method: 'GET',
    pattern: /^\/posts$/,
    handler: (ctx) => {
      const { from, to, tag } = ctx.query
      let list = activePosts()
      if (typeof tag === 'string' && tag.trim().length > 0) {
        list = list.filter((post) => post.tags.includes(tag.trim()))
      }
      if (typeof from === 'string' && from.length > 0) {
        list = list.filter((post) => dayOf(post.createdAt) >= from)
      }
      if (typeof to === 'string' && to.length > 0) {
        list = list.filter((post) => dayOf(post.createdAt) <= to)
      }
      list = [...list].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
      return paginate(list, ctx.query)
    },
  },
  {
    method: 'GET',
    pattern: /^\/posts\/(\d+)$/,
    handler: (_ctx, params) => findPost(Number(params[0])),
  },
  {
    method: 'POST',
    pattern: /^\/posts$/,
    handler: (ctx) => {
      const account = requireAccount(ctx)
      const body = readBody<{ text?: string; mediaIds?: number[]; tags?: string[] }>(ctx)
      const text = (body.text ?? '').trim()
      const mediaIds = body.mediaIds ?? []
      if (!text && mediaIds.length === 0) {
        throw mockError(400, 'VALIDATION_ERROR', '文本与媒体至少需要一项')
      }
      if (mediaIds.length > 9) {
        throw mockError(400, 'VALIDATION_ERROR', '媒体最多 9 项')
      }
      if ((body.tags ?? []).length > 5) {
        throw mockError(400, 'VALIDATION_ERROR', '标签最多 5 个')
      }
      const post: MockPost = {
        id: ++postSeq,
        author: { id: account.id, nickname: account.nickname },
        text,
        media: mediaIds.map((id) => ({ id, type: 'IMAGE' as MediaType, url: `/api/v1/media/${id}/content` })),
        tags: body.tags ?? [],
        createdAt: nowIso(),
        rating: { average: null, count: 0 },
        status: 'ACTIVE',
      }
      posts.push(post)
      return post
    },
  },
  {
    method: 'PATCH',
    pattern: /^\/posts\/(\d+)$/,
    handler: (ctx, params) => {
      const account = requireAccount(ctx)
      const post = findPost(Number(params[0]))
      if (post.author.id !== account.id) {
        throw mockError(403, 'FORBIDDEN', '只能修改自己发布的动态')
      }
      const body = readBody<{ text?: string; mediaIds?: number[]; tags?: string[] }>(ctx)
      if (body.text !== undefined) {
        post.text = body.text
      }
      if (body.tags !== undefined) {
        post.tags = body.tags
      }
      if (body.mediaIds !== undefined) {
        post.media = body.mediaIds.map((id) => ({ id, type: 'IMAGE' as MediaType, url: `/api/v1/media/${id}/content` }))
      }
      return post
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/posts\/(\d+)$/,
    handler: (ctx, params) => {
      const account = requireAccount(ctx)
      const post = findPost(Number(params[0]))
      if (post.author.id !== account.id) {
        throw mockError(403, 'FORBIDDEN', '只能删除自己发布的动态')
      }
      post.status = 'DELETED'
      return null
    },
  },
  {
    method: 'GET',
    pattern: /^\/users\/me\/posts$/,
    handler: (ctx) => {
      const account = requireAccount(ctx)
      const mine = activePosts()
        .filter((post) => post.author.id === account.id)
        .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
      return paginate(mine, ctx.query)
    },
  },

  /* ---------------- 互动 ---------------- */
  {
    method: 'PUT',
    pattern: /^\/posts\/(\d+)\/rating$/,
    handler: (ctx, params) => {
      const account = requireAccount(ctx)
      const post = findPost(Number(params[0]))
      if (post.author.id === account.id) {
        throw mockError(403, 'FORBIDDEN', '不能给自己的动态评分')
      }
      const score = Number(readBody<{ score?: number }>(ctx).score)
      if (!Number.isInteger(score) || score < 1 || score > 5) {
        throw mockError(400, 'VALIDATION_ERROR', '评分必须是 1–5 的整数')
      }
      const previous = myRatings.get(post.id)
      myRatings.set(post.id, score)
      const count = post.rating.count + (previous === undefined ? 1 : 0)
      const total = (post.rating.average ?? 0) * post.rating.count + (score - (previous ?? 0))
      post.rating = { average: Math.round((total / Math.max(1, count)) * 10) / 10, count }
      return { rating: post.rating, myScore: score }
    },
  },
  {
    method: 'GET',
    pattern: /^\/posts\/(\d+)\/comments$/,
    handler: (ctx, params) => {
      const post = findPost(Number(params[0]))
      const list = comments
        .filter((item) => item.postId === post.id)
        .sort((a, b) => (a.createdAt < b.createdAt ? -1 : 1))
      return paginate(list, ctx.query)
    },
  },
  {
    method: 'POST',
    pattern: /^\/posts\/(\d+)\/comments$/,
    handler: (ctx, params) => {
      const account = requireAccount(ctx)
      const post = findPost(Number(params[0]))
      const content = (readBody<{ content?: string }>(ctx).content ?? '').trim()
      if (content.length === 0 || content.length > 500) {
        throw mockError(400, 'VALIDATION_ERROR', '评论长度需为 1–500 字符')
      }
      const comment: MockComment = {
        id: ++commentSeq,
        postId: post.id,
        author: { id: account.id, nickname: account.nickname },
        content,
        createdAt: nowIso(),
      }
      comments.push(comment)
      return comment
    },
  },

  /* ---------------- 媒体 ---------------- */
  {
    method: 'POST',
    pattern: /^\/media$/,
    handler: (ctx) => {
      requireAccount(ctx)
      const id = ++mediaSeq
      return { mediaId: id, type: 'IMAGE' as MediaType, url: `/api/v1/media/${id}/content` }
    },
  },
]

function delay(ms: number): Promise<void> {
  return ms <= 0 ? Promise.resolve() : new Promise((resolve) => setTimeout(resolve, ms))
}

/** 按"方法 + 路径"查找模拟实现；未覆盖的接口会退回真实请求。 */
export function findMock(
  method: MockMethod,
  path: string
): ((ctx: MockContext) => Promise<unknown>) | undefined {
  for (const route of routes) {
    if (route.method !== method) {
      continue
    }
    const matched = route.pattern.exec(path)
    if (!matched) {
      continue
    }
    const params = matched.slice(1)
    return async (ctx: MockContext) => {
      await delay(MOCK_DELAY_MS)
      return route.handler(ctx, params)
    }
  }
  return undefined
}
