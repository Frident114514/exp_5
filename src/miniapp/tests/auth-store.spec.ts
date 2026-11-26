import { beforeEach, describe, expect, it } from 'vitest'
import { installUniStub, type UniStub } from './helpers/uni-stub'
import {
  AUTH_STORAGE_KEY,
  authStore,
  isSessionExpired,
  resetAuthStoreForTest,
} from '../src/stores/auth'

const user = { id: 1, account: 'demo', nickname: '示例用户', role: 'USER' as const }

function session(expiresInMs: number) {
  return {
    accessToken: 'token-abc',
    expiresAt: new Date(Date.now() + expiresInMs).toISOString(),
    user,
  }
}

let stub: UniStub

beforeEach(() => {
  stub = installUniStub()
  resetAuthStoreForTest()
  stub.storage.clear()
})

describe('登录态写入与恢复', () => {
  it('setSession 同时写入内存和本地缓存', () => {
    authStore.setSession(session(3600_000))

    expect(authStore.isLoggedIn).toBe(true)
    expect(authStore.token).toBe('token-abc')
    expect(authStore.user).toEqual(user)
    expect(stub.storage.get(AUTH_STORAGE_KEY)).toMatchObject({ accessToken: 'token-abc' })
  })

  it('load 从缓存恢复登录态（模拟小程序重启）', () => {
    stub.storage.set(AUTH_STORAGE_KEY, session(3600_000))
    resetAuthStoreForTest()

    authStore.load()

    expect(authStore.isLoggedIn).toBe(true)
    expect(authStore.user?.account).toBe('demo')
  })

  it('缓存里的会话已过期时丢弃并清除缓存', () => {
    stub.storage.set(AUTH_STORAGE_KEY, session(-1000))
    resetAuthStoreForTest()

    authStore.load()

    expect(authStore.isLoggedIn).toBe(false)
    expect(authStore.token).toBe('')
    expect(stub.storage.has(AUTH_STORAGE_KEY)).toBe(false)
  })

  it('缓存内容损坏时按未登录处理，不抛异常', () => {
    stub.storage.set(AUTH_STORAGE_KEY, '{not-json')
    resetAuthStoreForTest()

    expect(() => authStore.load()).not.toThrow()
    expect(authStore.isLoggedIn).toBe(false)
  })

  it('clear 清空内存并删除缓存', () => {
    authStore.setSession(session(3600_000))
    authStore.clear()

    expect(authStore.isLoggedIn).toBe(false)
    expect(authStore.user).toBeNull()
    expect(stub.storage.has(AUTH_STORAGE_KEY)).toBe(false)
  })

  it('setUser 只更新资料，不改令牌', () => {
    authStore.setSession(session(3600_000))
    authStore.setUser({ ...user, nickname: '新昵称' })

    expect(authStore.token).toBe('token-abc')
    expect(authStore.user?.nickname).toBe('新昵称')
    expect(stub.storage.get(AUTH_STORAGE_KEY)).toMatchObject({ user: { nickname: '新昵称' } })
  })

  it('未登录时 setUser 不会凭空造出登录态', () => {
    authStore.setUser(user)
    expect(authStore.isLoggedIn).toBe(false)
    expect(authStore.user).toBeNull()
  })
})

describe('isSessionExpired', () => {
  it('空值与无法解析的时间视为未过期，交给服务端判断', () => {
    expect(isSessionExpired('')).toBe(false)
    expect(isSessionExpired('not-a-date')).toBe(false)
  })

  it('按给定时间比较过期边界', () => {
    const now = Date.parse('2026-11-26T12:00:00+08:00')
    expect(isSessionExpired('2026-11-26T13:00:00+08:00', now)).toBe(false)
    expect(isSessionExpired('2026-11-26T12:00:01+08:00', now)).toBe(false)
    expect(isSessionExpired('2026-11-26T12:00:00+08:00', now)).toBe(true)
    expect(isSessionExpired('2026-11-26T11:59:59+08:00', now)).toBe(true)
  })
})

describe('角色识别', () => {
  it('只有 ADMIN 被识别为管理员', () => {
    authStore.setSession({ ...session(3600_000), user: { ...user, role: 'ADMIN' } })
    expect(authStore.isAdmin).toBe(true)

    authStore.setSession({ ...session(3600_000), user })
    expect(authStore.isAdmin).toBe(false)
  })
})
