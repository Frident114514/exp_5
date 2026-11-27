/**
 * 联调冒烟测试：需要先启动 mock 服务（pnpm mock），验证小程序请求层
 * 与真实 HTTP 服务的字段解析、404 处理、分页顺序。
 *
 *   node mock/mock-server.mjs            # 终端 A
 *   node tests/integration.ts            # 终端 B
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { isNotFound } from '../src/api/errors.ts'

const BASE = process.env.API_BASE_URL || 'http://localhost:8080/api/v1'

// 用 fetch 模拟小程序的 uni.request，只用于 Node 环境联调冒烟
;(globalThis as unknown as { uni: unknown }).uni = {
  request(options: {
    url: string
    method?: string
    timeout?: number
    success: (res: { statusCode: number; data: unknown }) => void
    fail: (err: { errMsg: string }) => void
  }) {
    fetch(options.url, { method: options.method || 'GET' })
      .then(async (res) => {
        const text = await res.text()
        let data: unknown = text
        try {
          data = JSON.parse(text)
        } catch {
          // 保留原始文本，交给客户端判定为格式异常
        }
        options.success({ statusCode: res.status, data })
      })
      .catch(() => options.fail({ errMsg: 'request:fail' }))
  },
}

const { createClient } = await import('../src/api/client.ts')
const { createPostsApi } = await import('../src/api/posts.ts')

const api = createPostsApi(createClient({ baseUrl: BASE, timeout: 5000 }))
const client = createClient({ baseUrl: BASE, timeout: 5000 })

test('GET /posts 返回契约字段并倒序分页', async () => {
  const page1 = await api.list({ page: 1, pageSize: 5 })
  assert.equal(page1.total, 12)
  assert.equal(page1.page, 1)
  assert.equal(page1.items.length, 5)

  const first = page1.items[0]
  assert.equal(typeof first.id, 'number')
  assert.equal(typeof first.author.nickname, 'string')
  assert.equal(typeof first.text, 'string')
  assert.ok(Array.isArray(first.media))
  assert.ok(Array.isArray(first.tags))
  assert.match(first.createdAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2}$/)
  assert.ok(first.rating.average === null || typeof first.rating.average === 'number')
  assert.equal(typeof first.rating.count, 'number')

  const page3 = await api.list({ page: 3, pageSize: 5 })
  assert.equal(page3.items.length, 2)
  // 无重复 id
  const ids = [...page1.items, ...page3.items].map((item) => item.id)
  assert.equal(new Set(ids).size, ids.length)
})

test('GET /posts/{id} 返回详情与评分摘要', async () => {
  const detail = await api.detail(101)
  assert.equal(detail.id, 101)
  assert.equal(detail.author.nickname, '小明')
  assert.equal(detail.media.length, 4)
  assert.equal(detail.rating.average, 4.5)
  assert.equal(detail.rating.count, 2)

  const noRating = await api.detail(102)
  assert.equal(noRating.rating.average, null)
  assert.equal(noRating.rating.count, 0)
})

test('已删除/不存在的动态返回 404，可被详情页识别', async () => {
  await assert.rejects(
    () => api.detail(999),
    (error: unknown) => {
      assert.equal(isNotFound(error), true)
      return true
    },
  )
})

test('空结果与接口失败都有确定响应', async () => {
  const empty = await client.get<{ items: unknown[]; total: number }>('/posts', {
    query: { scenario: 'empty' },
  })
  assert.deepEqual(empty.items, [])
  assert.equal(empty.total, 0)

  await assert.rejects(
    () => client.get('/posts', { query: { scenario: 'error' } }),
    (error: unknown) => {
      assert.equal((error as { status: number }).status, 500)
      return true
    },
  )
})