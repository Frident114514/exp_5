import { describe, expect, it } from 'vitest'
import { findMock, type MockContext } from '../src/api/mock'

/**
 * 校验本地模拟接口本身可用：M06/M07 开启 mock 后拿到的数据形状要与
 * api.md 一致，否则页面按契约写完后接真实服务端仍会出错。
 */

interface LoginResultLike {
  accessToken: string
  expiresAt: string
  user: { id: number; account: string; role: string }
}

interface PageLike<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

function mock(method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE', path: string) {
  const handler = findMock(method, path)
  if (!handler) {
    throw new Error(`模拟接口未覆盖 ${method} ${path}`)
  }
  return handler
}

function context(overrides: Partial<MockContext> = {}): MockContext {
  return { method: 'GET', path: '/', query: {}, token: '', ...overrides }
}

const DEMO_TOKEN = 'mock-token.demo.1'

describe('模拟账号', () => {
  it('demo/demo1234 可登录并返回令牌与用户资料', async () => {
    const result = (await mock('POST', '/auth/login')(
      context({
        method: 'POST',
        path: '/auth/login',
        data: { account: 'demo', password: 'demo1234' },
      })
    )) as LoginResultLike

    expect(result.accessToken).toContain('mock-token.')
    expect(result.expiresAt).toBeTruthy()
    expect(result.user).toMatchObject({ account: 'demo', role: 'USER' })
  })

  it('密码错误返回 401 UNAUTHENTICATED', async () => {
    await expect(
      mock('POST', '/auth/login')(
        context({ method: 'POST', path: '/auth/login', data: { account: 'demo', password: 'wrong' } })
      )
    ).rejects.toMatchObject({ status: 401, code: 'UNAUTHENTICATED' })
  })

  it('未带令牌访问 /users/me 返回 401，页面可据此验证登录拦截', async () => {
    await expect(mock('GET', '/users/me')(context({ path: '/users/me' }))).rejects.toMatchObject({
      status: 401,
      code: 'UNAUTHENTICATED',
    })
  })

  it('带令牌访问 /users/me 返回对应用户', async () => {
    const user = (await mock('GET', '/users/me')(
      context({ path: '/users/me', token: DEMO_TOKEN })
    )) as { account: string; nickname: string }

    expect(user.account).toBe('demo')
    expect(user.nickname).toBe('示例用户')
  })

  it('原密码错误时改密返回 400', async () => {
    await expect(
      mock('PUT', '/users/me/password')(
        context({
          method: 'PUT',
          path: '/users/me/password',
          token: DEMO_TOKEN,
          data: { oldPassword: 'wrong', newPassword: 'abcd1234' },
        })
      )
    ).rejects.toMatchObject({ status: 400, code: 'VALIDATION_ERROR' })
  })
})

describe('模拟动态', () => {
  it('列表返回分页结构与 3 条样例', async () => {
    const page = (await mock('GET', '/posts')(context({ path: '/posts' }))) as PageLike<unknown>

    expect(page.total).toBe(3)
    expect(page.items).toHaveLength(3)
    expect(page).toMatchObject({ page: 1, pageSize: 10 })
  })

  it('按标签筛选', async () => {
    const page = (await mock('GET', '/posts')(
      context({ path: '/posts', query: { tag: '校园' } })
    )) as PageLike<{ tags: string[] }>

    expect(page.total).toBe(1)
    expect(page.items[0].tags).toContain('校园')
  })

  it('按北京时间自然日筛选（含起止）', async () => {
    const page = (await mock('GET', '/posts')(
      context({ path: '/posts', query: { from: '2026-11-21', to: '2026-11-21' } })
    )) as PageLike<{ id: number }>

    expect(page.items.map((item) => item.id)).toEqual([102])
  })

  it('不存在的动态返回 404', async () => {
    await expect(mock('GET', '/posts/9999')(context({ path: '/posts/9999' }))).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
    })
  })

  it('未登录发布动态返回 401', async () => {
    await expect(
      mock('POST', '/posts')(context({ method: 'POST', path: '/posts', data: { text: 'hi' } }))
    ).rejects.toMatchObject({ status: 401 })
  })

  it('登录后发布空内容返回 400（文本与媒体至少一项）', async () => {
    await expect(
      mock('POST', '/posts')(
        context({ method: 'POST', path: '/posts', token: DEMO_TOKEN, data: { text: '', mediaIds: [] } })
      )
    ).rejects.toMatchObject({ status: 400, code: 'VALIDATION_ERROR' })
  })
})
