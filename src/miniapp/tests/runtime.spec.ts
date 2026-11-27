import { beforeEach, expect, it } from 'vitest'
import { installUniStub, type UniStub } from './helpers/uni-stub'
import { authStore, resetAuthStoreForTest } from '../src/stores/auth'
import { ensureCurrentUser, getCurrentUser, getPostApi, isUsingMock } from '../src/api/runtime'

let stub: UniStub
const user = { id: 7, account: 'actual_user', nickname: '当前用户', role: 'USER' as const }

beforeEach(() => {
  stub = installUniStub()
  resetAuthStoreForTest()
})

it('作者接口默认读取 M05 登录态并携带同一令牌发送真实请求', async () => {
  authStore.setSession({ accessToken: 'actual-token', expiresAt: '2099-01-01T00:00:00Z', user })
  stub.respond(request => request.success?.({ statusCode: 200, data: { code: 'OK', data: { id: 42 }, message: '' } }))
  expect(isUsingMock).toBe(false)
  expect(await ensureCurrentUser()).toEqual(user)
  expect(await getPostApi().create({ text: '第一周联调', mediaIds: [], tags: [] })).toEqual({ id: 42 })
  expect(stub.calls.requests[0]).toMatchObject({
    method: 'POST', url: 'http://127.0.0.1:8080/api/v1/posts',
    header: { Authorization: 'Bearer actual-token' },
  })
  authStore.clear()
  expect(getCurrentUser()).toBeNull()
  await expect(ensureCurrentUser()).rejects.toMatchObject({ code: 'UNAUTHENTICATED' })
})

it('作者接口收到 401 后清除 M05 登录态并报告失败', async () => {
  authStore.setSession({ accessToken: 'expired-token', expiresAt: '2099-01-01T00:00:00Z', user })
  stub.respond(request => request.success?.({ statusCode: 401, data: { code: 'UNAUTHENTICATED', data: null, message: '登录失效' } }))
  await expect(getPostApi().mine(1, 5)).rejects.toMatchObject({ code: 'UNAUTHENTICATED' })
  expect(authStore.isLoggedIn).toBe(false)
  expect(getCurrentUser()).toBeNull()
})
