import { beforeEach, describe, expect, it } from 'vitest'
import { installUniStub, type UniStub } from './helpers/uni-stub'
import {
  buildQueryString,
  get,
  isApiError,
  post,
  resetUnauthorizedThrottle,
  type RequestOptions,
} from '../src/api/http'
import { authStore } from '../src/stores/auth'
import type { ApiError } from '../src/api/types'

const session = {
  accessToken: 'token-abc',
  expiresAt: new Date(Date.now() + 3600_000).toISOString(),
  user: { id: 1, account: 'demo', nickname: '示例用户', role: 'USER' as const },
}

let stub: UniStub

beforeEach(() => {
  stub = installUniStub()
  authStore.clear()
  resetUnauthorizedThrottle()
})

function respondOk(data: unknown): void {
  stub.respond((request) => {
    request.success?.({ statusCode: 200, data: { code: 'OK', data, message: '' } })
  })
}

/** 捕获 rejection 并按 ApiError 返回，便于断言字段。 */
async function captureApiError(promise: Promise<unknown>): Promise<ApiError> {
  const reason = await promise.then(
    () => {
      throw new Error('expected promise to reject')
    },
    (error: unknown) => error
  )
  if (!isApiError(reason)) {
    throw new Error(`expected ApiError, received: ${String(reason)}`)
  }
  return reason
}

describe('buildQueryString', () => {
  it('拼接并编码参数，跳过空值', () => {
    expect(buildQueryString()).toBe('')
    expect(buildQueryString({})).toBe('')
    expect(buildQueryString({ page: 2, tag: '校园', empty: '', none: undefined, blank: null })).toBe(
      '?page=2&tag=%E6%A0%A1%E5%9B%AD'
    )
  })
})

describe('request 成功路径', () => {
  it('只返回统一响应外壳里的 data', async () => {
    respondOk({ id: 7 })
    await expect(get<{ id: number }>('/users/me')).resolves.toEqual({ id: 7 })
  })

  it('拼接基础地址与查询参数', async () => {
    respondOk({ items: [] })
    await get('/posts', { page: 2, tag: '校园' })
    expect(stub.calls.requests[0].url).toBe(
      'http://127.0.0.1:8080/api/v1/posts?page=2&tag=%E6%A0%A1%E5%9B%AD'
    )
  })

  it('已登录时自动附加 Bearer 令牌', async () => {
    authStore.setSession(session)
    respondOk({ id: 1 })
    await get('/users/me')
    expect(stub.calls.requests[0].header?.Authorization).toBe('Bearer token-abc')
  })

  it('withAuth=false 时不附加令牌（登录、注册）', async () => {
    authStore.setSession(session)
    respondOk({ accessToken: 'x', expiresAt: '', user: session.user })
    await post('/auth/login', { account: 'demo', password: 'demo1234' }, { withAuth: false })
    expect(stub.calls.requests[0].header?.Authorization).toBeUndefined()
  })

  it('POST 自动带 JSON Content-Type', async () => {
    respondOk(null)
    await post('/posts', { text: 'hi' })
    expect(stub.calls.requests[0].header?.['Content-Type']).toBe('application/json')
    expect(stub.calls.requests[0].method).toBe('POST')
  })
})

describe('request 失败路径', () => {
  it('把服务端 code 与 message 原样交给调用方', async () => {
    stub.respond((request) => {
      request.success?.({
        statusCode: 400,
        data: { code: 'VALIDATION_ERROR', data: null, message: '标签最多 5 个' },
      })
    })
    const error = await captureApiError(get('/posts'))
    expect(error).toMatchObject({ status: 400, code: 'VALIDATION_ERROR', message: '标签最多 5 个' })
  })

  it('服务端没有 message 时按状态码给出中文兜底', async () => {
    stub.respond((request) => {
      request.success?.({ statusCode: 500, data: { code: 'UNKNOWN', data: null, message: '' } })
    })
    const error = await captureApiError(get('/posts'))
    expect(error.message).toBe('服务器繁忙，请稍后重试')
    expect(error.code).toBe('UNKNOWN_ERROR')
  })

  it('网络异常归一为 status 0 的 NETWORK_ERROR', async () => {
    stub.respond((request) => request.fail?.({ errMsg: 'request:fail timeout' }))
    const error = await captureApiError(get('/posts'))
    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR', message: '网络连接失败，请检查网络后重试' })
  })

  it('401 且本来带着令牌时：清空登录态并跳登录页，带回跳地址', async () => {
    authStore.setSession(session)
    stub.respond((request) => {
      request.success?.({
        statusCode: 401,
        data: { code: 'UNAUTHENTICATED', data: null, message: '登录状态已失效，请重新登录' },
      })
    })

    await expect(get('/users/me')).rejects.toMatchObject({ status: 401, code: 'UNAUTHENTICATED' })

    expect(authStore.isLoggedIn).toBe(false)
    expect(authStore.token).toBe('')
    const navigation = stub.calls.navigations.find((item) => item.type === 'navigateTo')
    expect(navigation?.url).toBe('/pages/login/index?redirect=%2Fpages%2Fplaza%2Findex')
  })

  it('已经在登录页时不再重复跳转', async () => {
    authStore.setSession(session)
    stub.setPages([{ route: 'pages/login/index', options: {} }])
    stub.respond((request) => {
      request.success?.({
        statusCode: 401,
        data: { code: 'UNAUTHENTICATED', data: null, message: '登录状态已失效' },
      })
    })

    await expect(get('/users/me')).rejects.toBeTruthy()
    expect(authStore.isLoggedIn).toBe(false)
    expect(stub.calls.navigations).toHaveLength(0)
  })

  it('登录接口自身的 401 不触发跳转（账号密码错误）', async () => {
    stub.respond((request) => {
      request.success?.({
        statusCode: 401,
        data: { code: 'UNAUTHENTICATED', data: null, message: '账号或密码不正确' },
      })
    })
    const options: Partial<RequestOptions> = { withAuth: false }
    await expect(post('/auth/login', { account: 'demo', password: 'x' }, options)).rejects.toMatchObject({
      message: '账号或密码不正确',
    })
    expect(stub.calls.navigations).toHaveLength(0)
  })

  it('未登录时的 401 只清理本地状态，不打断公开浏览', async () => {
    stub.respond((request) => {
      request.success?.({
        statusCode: 401,
        data: { code: 'UNAUTHENTICATED', data: null, message: '请先登录' },
      })
    })
    await expect(get('/users/me')).rejects.toMatchObject({ status: 401 })
    expect(stub.calls.navigations).toHaveLength(0)
  })
})
