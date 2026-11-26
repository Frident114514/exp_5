import test from 'node:test'
import assert from 'node:assert/strict'
import { buildUrl, createClient } from '../src/api/client.ts'
import type { HttpRequestOptions } from '../src/api/client.ts'
import { ApiError, describeError, isNotFound } from '../src/api/errors.ts'
import { createPostsApi, normalizePageQuery } from '../src/api/posts.ts'

const BASE = 'http://localhost:8080/api/v1'

function transportOf(body: unknown, statusCode = 200) {
  const calls: HttpRequestOptions[] = []
  return {
    calls,
    transport: async (options: HttpRequestOptions) => {
      calls.push(options)
      return { statusCode, data: body }
    },
  }
}

test('buildUrl 拼接路径、去重斜杠并跳过空参数', () => {
  assert.equal(buildUrl(BASE, '/posts'), BASE + '/posts')
  assert.equal(buildUrl(BASE + '/', 'posts', { page: 1, tag: undefined, to: '' }), BASE + '/posts?page=1')
  assert.equal(
    buildUrl(BASE, '/posts', { tag: '校园' }),
    BASE + '/posts?tag=' + encodeURIComponent('校园'),
  )
})

test('成功响应解开包装并返回 data', async () => {
  const { transport } = transportOf({ code: 'OK', data: { id: 1 }, message: '' })
  const client = createClient({ baseUrl: BASE, transport })
  const data = await client.get<{ id: number }>('/posts/1')
  assert.deepEqual(data, { id: 1 })
})

test('业务错误码转成 ApiError（HTTP 200 也是失败）', async () => {
  const { transport } = transportOf({ code: 'VALIDATION_ERROR', data: null, message: '参数有误' })
  const client = createClient({ baseUrl: BASE, transport })
  await assert.rejects(
    () => client.get('/posts'),
    (error: unknown) => {
      assert.ok(error instanceof ApiError)
      assert.equal(error.kind, 'business')
      assert.equal(error.code, 'VALIDATION_ERROR')
      assert.equal(error.message, '参数有误')
      return true
    },
  )
})

test('404 识别为“不存在/已删除”', async () => {
  const { transport } = transportOf({ code: 'NOT_FOUND', data: null, message: '动态不存在' }, 404)
  const client = createClient({ baseUrl: BASE, transport })
  await assert.rejects(
    () => client.get('/posts/404'),
    (error: unknown) => {
      assert.equal(isNotFound(error), true)
      assert.equal(describeError(error), '动态不存在')
      return true
    },
  )
})

test('500 或非契约响应给出可读提示，不返回 undefined', async () => {
  const { transport } = transportOf('<html>Internal Error</html>', 500)
  const client = createClient({ baseUrl: BASE, transport })
  await assert.rejects(
    () => client.get('/posts'),
    (error: unknown) => {
      assert.equal((error as ApiError).status, 500)
      assert.match(describeError(error), /服务端暂时不可用/)
      return true
    },
  )

  const ok = transportOf('<html>oops</html>', 200)
  const client2 = createClient({ baseUrl: BASE, transport: ok.transport })
  await assert.rejects(() => client2.get('/posts'), /格式异常/)
})

test('网络失败与超时分别归类', async () => {
  const network = createClient({
    baseUrl: BASE,
    transport: async () => {
      throw { errMsg: 'request:fail' }
    },
  })
  await assert.rejects(
    () => network.get('/posts'),
    (error: unknown) => {
      assert.equal((error as ApiError).kind, 'network')
      assert.match(describeError(error), /网络连接失败/)
      return true
    },
  )

  const timeout = createClient({
    baseUrl: BASE,
    transport: async () => {
      throw { errMsg: 'request:fail timeout' }
    },
  })
  await assert.rejects(
    () => timeout.get('/posts'),
    (error: unknown) => {
      assert.equal((error as ApiError).kind, 'timeout')
      assert.match(describeError(error), /超时/)
      return true
    },
  )
})

test('列表与详情请求的路径和分页参数符合契约', async () => {
  const listResponse = {
    code: 'OK',
    data: { items: [], page: 2, pageSize: 10, total: 0 },
    message: '',
  }
  const { calls, transport } = transportOf(listResponse)
  const api = createPostsApi(createClient({ baseUrl: BASE, transport }))

  const result = await api.list({ page: 2, pageSize: 10 })
  assert.equal(result.total, 0)
  assert.equal(calls[0].url, BASE + '/posts?page=2&pageSize=10')
  assert.equal(calls[0].method, 'GET')

  const { calls: detailCalls, transport: detailTransport } = transportOf({
    code: 'OK',
    data: { id: 7 },
    message: '',
  })
  const api2 = createPostsApi(createClient({ baseUrl: BASE, transport: detailTransport }))
  await api2.detail(7)
  assert.equal(detailCalls[0].url, BASE + '/posts/7')
})
test('分页参数按契约夹紧：page>=1，pageSize 1..50', () => {
  assert.deepEqual(normalizePageQuery({}), { page: 1, pageSize: 10 })
  assert.deepEqual(normalizePageQuery({ page: 0, pageSize: 0 }), { page: 1, pageSize: 1 })
  assert.deepEqual(normalizePageQuery({ page: 3, pageSize: 999 }), { page: 3, pageSize: 50 })
  assert.deepEqual(normalizePageQuery({ page: -2, pageSize: Number.NaN }), { page: 1, pageSize: 10 })
})
