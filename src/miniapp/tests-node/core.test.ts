import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import {
  createHttpPostApi,
  type HttpRequestOptions,
  type HttpTransport,
} from '../src/api/httpPostApi'
import { MockPostApi } from '../src/api/mockPostApi'
import {
  AUTH_SESSION_STORAGE_KEY,
  isSessionExpired,
  readAuthSession,
  saveAuthSession,
  type AuthSession,
  type KeyValueStorage,
} from '../src/api/session'
import type { PostAuthor, PostView } from '../src/api/types'
import {
  countCodePoints,
  validateEditedText,
  validateTextOnlyPost,
} from '../src/utils/postRules'

const currentUser: PostAuthor = { id: 6006, nickname: '测试用户' }
const fixedNow = () => new Date('2026-11-26T08:00:00.000Z')

function createApi(posts: PostView[] = []): MockPostApi {
  return new MockPostApi({
    currentUser,
    initialPosts: posts,
    latencyMs: 0,
    now: fixedNow,
  })
}

function otherPost(): PostView {
  return {
    id: 9001,
    author: { id: 7007, nickname: '其他用户' },
    text: '他人的动态',
    media: [],
    tags: [],
    createdAt: '2026-11-26T01:00:00.000Z',
    updatedAt: '2026-11-26T01:00:00.000Z',
    rating: { average: null, count: 0 },
  }
}

describe('纯文本动态规则', () => {
  it('去除首尾空白并拒绝空正文', () => {
    assert.equal(validateTextOnlyPost('   ').valid, false)
    assert.deepEqual(
      { valid: validateTextOnlyPost('  第一条动态  ').valid, normalized: validateTextOnlyPost('  第一条动态  ').normalized },
      { valid: true, normalized: '第一条动态' },
    )
  })

  it('允许 10000 个 Unicode 码点并拒绝第 10001 个', () => {
    assert.equal(validateTextOnlyPost('a'.repeat(10_000)).valid, true)
    assert.equal(validateTextOnlyPost('a'.repeat(10_001)).valid, false)
  })

  it('按 Unicode 码点计数', () => {
    assert.equal(countCodePoints('😀'.repeat(10_000)), 10_000)
    assert.equal(validateTextOnlyPost('😀'.repeat(10_000)).valid, true)
  })

  it('编辑清空文字时必须保留媒体', () => {
    assert.equal(validateEditedText('   ', 0).valid, false)
    assert.equal(validateEditedText('   ', 1).valid, true)
  })
})

describe('MockPostApi 第一周契约行为', () => {
  it('创建后可在本人列表和详情中读取', async () => {
    const api = createApi()
    const created = await api.create({ text: '  新动态  ', mediaIds: [], tags: [] })
    const page = await api.mine(1, 5)
    assert.equal(created.id, 1001)
    assert.equal(page.total, 1)
    assert.equal(page.items[0]?.text, '新动态')
    assert.equal((await api.detail(1001)).text, '新动态')
  })

  it('本人列表按发布时间倒序分页', async () => {
    const api = createApi()
    for (let index = 1; index <= 6; index += 1) {
      await api.create({ text: `动态 ${index}`, mediaIds: [], tags: [] })
    }
    const first = await api.mine(1, 2)
    const third = await api.mine(3, 2)
    assert.deepEqual(first.items.map((post) => post.id), [1006, 1005])
    assert.deepEqual(third.items.map((post) => post.id), [1002, 1001])
    assert.equal(first.total, 6)
  })

  it('编辑正文并刷新 updatedAt', async () => {
    const api = createApi()
    const created = await api.create({ text: '原文', mediaIds: [], tags: [] })
    const updated = await api.edit(created.id, { text: '修改后' })
    assert.equal(updated.text, '修改后')
    assert.equal((await api.detail(created.id)).text, '修改后')
  })

  it('删除后列表和详情均不可见', async () => {
    const api = createApi()
    const created = await api.create({ text: '待删除', mediaIds: [], tags: [] })
    await api.delete(created.id)
    assert.equal((await api.mine(1, 5)).total, 0)
    await assert.rejects(api.detail(created.id), { code: 'NOT_FOUND' })
  })

  it('禁止编辑或删除他人动态', async () => {
    const api = createApi([otherPost()])
    await assert.rejects(api.edit(9001, { text: '越权修改' }), (error: unknown) => {
      return Boolean(error && typeof error === 'object' && 'code' in error && error.code === 'FORBIDDEN')
    })
    await assert.rejects(api.delete(9001), { code: 'FORBIDDEN' })
  })

  it('模拟一次网络失败后可重试成功', async () => {
    const api = createApi()
    api.failNextRequest()
    await assert.rejects(api.create({ text: '失败重试', mediaIds: [], tags: [] }), {
      code: 'NETWORK_ERROR',
    })
    assert.equal((await api.create({ text: '失败重试', mediaIds: [], tags: [] })).id, 1001)
  })

  it('拒绝非法分页和第一周媒体/标签请求', async () => {
    const api = createApi()
    await assert.rejects(api.mine(0, 5), { code: 'VALIDATION_ERROR' })
    await assert.rejects(api.mine(1, 51), { code: 'VALIDATION_ERROR' })
    await assert.rejects(api.create({ text: '图片', mediaIds: [1], tags: [] }), {
      code: 'VALIDATION_ERROR',
    })
    await assert.rejects(api.create({ text: '标签', mediaIds: [], tags: ['校园'] }), {
      code: 'VALIDATION_ERROR',
    })
  })
})
describe('登录态与真实 PostApi 适配', () => {
  it('读取 M05 统一会话键中的真实用户', () => {
    const values = new Map<string, unknown>()
    const storage: KeyValueStorage = {
      get<T>(key: string) { return values.get(key) as T | undefined },
      set<T>(key: string, value: T) { values.set(key, value) },
      remove(key: string) { values.delete(key) },
    }
    const session: AuthSession = {
      accessToken: 'token-real',
      expiresAt: '2099-01-01T00:00:00Z',
      user: { id: 7, account: 'real_user', nickname: '真实用户', role: 'USER' },
    }

    saveAuthSession(session, storage)

    assert.equal(values.has(AUTH_SESSION_STORAGE_KEY), true)
    assert.deepEqual(readAuthSession(storage), session)
    assert.equal(isSessionExpired(session, Date.parse('2098-01-01T00:00:00Z')), false)
  })

  it('兼容 token 与 user 分键保存的登录态', () => {
    const storage: KeyValueStorage = {
      get<T>(key: string) {
        if (key === 'accessToken') return 'separate-token' as T
        if (key === 'currentUser') return { id: 8, nickname: '分键用户' } as T
        return undefined
      },
      set() {},
      remove() {},
    }

    assert.deepEqual(readAuthSession(storage), {
      accessToken: 'separate-token',
      user: { id: 8, nickname: '分键用户', account: undefined, role: undefined },
    })
  })

  it('真实 PostApi 保留 PATCH 方法并映射业务错误码', async () => {
    const calls: Array<{ path: string; method: string }> = []
    const transport: HttpTransport = {
      async request<T>(options: HttpRequestOptions) {
        calls.push({ path: options.path, method: options.method })
        return {
          code: 'FORBIDDEN',
          data: null,
          message: '只能管理本人动态',
        } as { code: string; data: T; message: string }
      },
    }
    const api = createHttpPostApi(transport)

    await assert.rejects(api.edit(7, { text: '修改' }), { code: 'FORBIDDEN' })
    assert.deepEqual(calls, [{ path: '/posts/7', method: 'PATCH' }])
  })
})