import { describe, expect, it, vi } from 'vitest'

import type { AdminApi } from '../src/common/data/adminApi'
import { ApiError } from '../src/common/data/http'
import type { LoginResult } from '../src/common/data/models'
import { createAuthState } from '../src/modules/auth/logic/authState'

/*
行为：为认证测试建立独立存储和 API。
实现：使用内存 Storage 及可控异步响应，不调用真实服务或保存真实凭据。
*/
function setup(result?: LoginResult) {
  const entries = new Map<string, string>()
  const storage: Storage = {
    length: 0,
    clear: () => entries.clear(),
    getItem: (key) => entries.get(key) || null,
    key: () => null,
    removeItem: (key) => {
      entries.delete(key)
    },
    setItem: (key, item) => {
      entries.set(key, item)
    },
  }
  const loginResult: LoginResult = result || {
    accessToken: 'test-token',
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    user: { id: 1, account: 'test_admin', nickname: '测试管理员', role: 'ADMIN', status: 'ACTIVE' },
  }
  const api: AdminApi = {
    login: vi.fn().mockResolvedValue(loginResult),
    getMe: vi.fn().mockResolvedValue(loginResult.user),
    listPosts: vi.fn(),
    listUsers: vi.fn(),
  }
  return { api, storage, loginResult }
}

describe('管理员前端会话', () => {
  it('管理员可登录，密码按原输入提交且不持久保存', async () => {
    const { api, storage } = setup()
    const auth = createAuthState(api, storage)
    await auth.login(' test_admin ', ' password with spaces ')
    expect(api.login).toHaveBeenCalledWith(
      'test_admin',
      ' password with spaces ',
      expect.any(AbortSignal),
    )
    expect(auth.user.value?.role).toBe('ADMIN')
    expect(storage.getItem('moments.admin.session')).not.toContain('password')
    expect(await auth.ensureSession()).toBe(true)
    expect(api.getMe).not.toHaveBeenCalled()
  })

  it('普通用户登录成功也不能建立后台会话', async () => {
    const { loginResult } = setup()
    loginResult.user.role = 'USER'
    const { api, storage } = setup(loginResult)
    const auth = createAuthState(api, storage)
    await expect(auth.login('test_user', 'test-password')).rejects.toMatchObject({ status: 403 })
    expect(auth.hasSession.value).toBe(false)
    expect(storage.getItem('moments.admin.session')).toBeNull()
  })

  it('禁用账号与过期凭证都无法进入管理页面', async () => {
    const { loginResult } = setup()
    loginResult.user.status = 'DISABLED'
    const first = setup(loginResult)
    await expect(
      createAuthState(first.api, first.storage).login('test_admin', 'test-password'),
    ).rejects.toMatchObject({ status: 403 })
    loginResult.user.status = 'ACTIVE'
    loginResult.expiresAt = new Date(Date.now() - 1).toISOString()
    const second = setup(loginResult)
    await expect(
      createAuthState(second.api, second.storage).login('test_admin', 'test-password'),
    ).rejects.toMatchObject({ status: 401 })
  })

  it('刷新恢复时共享一次身份验证，网络失败后可主动重试', async () => {
    const { api, storage, loginResult } = setup()
    storage.setItem('moments.admin.session', JSON.stringify(loginResult))
    let resolveUser: (user: LoginResult['user']) => void = () => undefined
    const pending = new Promise<LoginResult['user']>((resolve) => {
      resolveUser = resolve
    })
    vi.mocked(api.getMe).mockReturnValueOnce(pending)
    const auth = createAuthState(api, storage)
    const first = auth.ensureSession()
    const second = auth.ensureSession()
    expect(api.getMe).toHaveBeenCalledTimes(1)
    resolveUser(loginResult.user)
    expect(await Promise.all([first, second])).toEqual([true, true])

    const retryAuth = createAuthState(api, storage)
    vi.mocked(api.getMe).mockRejectedValueOnce(new ApiError('断网'))
    await expect(retryAuth.ensureSession()).rejects.toThrow('断网')
    expect(retryAuth.hasSession.value).toBe(true)
    expect(await retryAuth.ensureSession()).toBe(true)
  })

  it('退出期间晚到的身份响应不能恢复会话', async () => {
    const { api, storage, loginResult } = setup()
    storage.setItem('moments.admin.session', JSON.stringify(loginResult))
    let resolveUser: (user: LoginResult['user']) => void = () => undefined
    vi.mocked(api.getMe).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveUser = resolve
        }),
    )
    const auth = createAuthState(api, storage)
    const pending = auth.ensureSession()
    auth.clearSession()
    resolveUser(loginResult.user)
    expect(await pending).toBe(false)
    expect(auth.hasSession.value).toBe(false)
  })

  it('旧请求的 401 不撤销新登录会话', async () => {
    const { api, storage } = setup()
    const auth = createAuthState(api, storage)
    await auth.login('test_admin', 'test-password')
    const oldVersion = auth.version.value
    await auth.login('test_admin', 'test-password')
    auth.expireSession(oldVersion)
    expect(auth.hasSession.value).toBe(true)
    auth.expireSession(auth.version.value)
    expect(auth.hasSession.value).toBe(false)
  })

  it('损坏的本地会话被移除', () => {
    const { api, storage } = setup()
    storage.setItem('moments.admin.session', '{broken')
    const auth = createAuthState(api, storage)
    expect(auth.hasSession.value).toBe(false)
    expect(storage.getItem('moments.admin.session')).toBeNull()
  })
})
