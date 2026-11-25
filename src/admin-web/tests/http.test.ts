import { describe, expect, it, vi } from 'vitest'

import { createAdminApi } from '../src/common/data/adminApi'
import { createHttpClient } from '../src/common/data/http'

/*
行为：建立响应可控的请求客户端。
实现：固定测试令牌与版本，并记录认证失效通知。
*/
function setup(response: Response) {
  const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(response)
  const onUnauthorized = vi.fn()
  const http = createHttpClient({
    baseUrl: '/api/v1/',
    fetch,
    getSession: () => ({ accessToken: 'test-token', version: 4 }),
    onUnauthorized,
  })
  return { http, fetch, onUnauthorized }
}

describe('管理 API 请求', () => {
  it('本地会话缺失时不向管理接口发送无凭证请求', async () => {
    const fetch = vi.fn<typeof globalThis.fetch>()
    const http = createHttpClient({
      baseUrl: '/api/v1',
      fetch,
      getSession: () => null,
      onUnauthorized: vi.fn(),
    })
    await expect(http.request('/admin/users')).rejects.toMatchObject({ status: 401 })
    expect(fetch).not.toHaveBeenCalled()
  })
  it('发送 Bearer 和编码参数，公开登录不附加令牌', async () => {
    const first = setup(new Response(JSON.stringify({ code: 'OK', data: {}, message: '' })))
    await first.http.request('/admin/posts', {
      query: { keyword: '校园&学习', page: 1, tag: undefined },
    })
    expect(first.fetch.mock.calls[0]?.[0]).toBe(
      '/api/v1/admin/posts?keyword=%E6%A0%A1%E5%9B%AD%26%E5%AD%A6%E4%B9%A0&page=1',
    )
    expect(new Headers(first.fetch.mock.calls[0]?.[1]?.headers).get('Authorization')).toBe(
      'Bearer test-token',
    )
    const second = setup(new Response(JSON.stringify({ code: 'OK', data: {}, message: '' })))
    await second.http.request('/auth/login', {
      public: true,
      method: 'POST',
      body: { account: 'test' },
    })
    expect(new Headers(second.fetch.mock.calls[0]?.[1]?.headers).has('Authorization')).toBe(false)
  })

  it('非 JSON 的 401 仍通知认证失效', async () => {
    const { http, onUnauthorized } = setup(
      new Response('<html>unauthorized</html>', { status: 401 }),
    )
    await expect(http.request('/users/me')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledWith(4)
  })

  it('业务错误和缺少字段都不能被当作成功', async () => {
    const failed = setup(
      new Response(JSON.stringify({ code: 'FORBIDDEN', data: null, message: '无权限' })),
    )
    await expect(failed.http.request('/admin/users')).rejects.toThrow('无权限')
    const broken = setup(new Response(JSON.stringify({ code: 'OK' })))
    await expect(broken.http.request('/admin/users')).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
    })
  })

  it('分页响应必须与请求页一致，缺失用户字段明确失败', async () => {
    const mismatched = setup(
      new Response(
        JSON.stringify({
          code: 'OK',
          data: { items: [], page: 2, pageSize: 10, total: 0 },
          message: '',
        }),
      ),
    )
    await expect(
      createAdminApi(mismatched.http).listUsers({ page: 1, pageSize: 10 }),
    ).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
    const incomplete = setup(
      new Response(
        JSON.stringify({
          code: 'OK',
          data: {
            items: [{ id: 1, account: 'test', nickname: '测试', role: 'USER' }],
            page: 1,
            pageSize: 10,
            total: 1,
          },
          message: '',
        }),
      ),
    )
    await expect(
      createAdminApi(incomplete.http).listUsers({ page: 1, pageSize: 10 }),
    ).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
})
