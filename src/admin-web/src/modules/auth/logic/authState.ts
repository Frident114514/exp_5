import { computed, readonly, ref, shallowRef } from 'vue'

import { isTimestamp, readUserInfo } from '../../../common/data/adminApi'
import type { AdminApi } from '../../../common/data/adminApi'
import { ApiError, isRecord } from '../../../common/data/http'
import type { LoginResult } from '../../../common/data/models'

const SESSION_KEY = 'moments.admin.session'

/*
行为：管理单个浏览器标签页的登录状态与管理员身份复核。
实现：会话保存在 sessionStorage，网络验证和登录通过版本号隔离过期响应。
*/
export function createAuthState(api: AdminApi, storage: Storage, sessionKey = SESSION_KEY) {
  const session = shallowRef<LoginResult | null>(null)
  const version = ref(0)
  const isLoggingIn = ref(false)
  const notice = ref('')
  let verified = false
  let loginController: AbortController | null = null
  let verifyController: AbortController | null = null
  let verification: Promise<boolean> | null = null

  /*
  行为：清除当前会话并阻止在途认证结果恢复旧身份。
  实现：先递增版本并取消请求，再删除持久会话；存储不可用时提示刷新后的限制。
  */
  function clearSession(message = ''): void {
    version.value += 1
    loginController?.abort()
    verifyController?.abort()
    loginController = null
    verifyController = null
    verification = null
    isLoggingIn.value = false
    verified = false
    session.value = null
    notice.value = message
    try {
      storage.removeItem(sessionKey)
    } catch {
      notice.value = '浏览器无法清除保存的会话，请关闭此标签页后重新登录'
    }
  }

  /*
  行为：读取尚未到期的本标签页会话。
  实现：验证存储格式和身份字段，读取失败或过期时清除并交由登录页恢复。
  */
  function restoreSession(): void {
    try {
      const saved = storage.getItem(sessionKey)
      if (!saved) return
      const input: unknown = JSON.parse(saved)
      if (
        !isRecord(input) ||
        typeof input.accessToken !== 'string' ||
        !input.accessToken.trim() ||
        !isTimestamp(input.expiresAt)
      ) {
        throw new Error('会话格式不完整')
      }
      const user = readUserInfo(input.user)
      if (
        user.role !== 'ADMIN' ||
        user.status === 'DISABLED' ||
        Date.parse(input.expiresAt) <= Date.now()
      ) {
        clearSession('登录已失效，请重新登录')
        return
      }
      session.value = { accessToken: input.accessToken, expiresAt: input.expiresAt, user }
    } catch {
      clearSession('无法恢复保存的会话，请重新登录')
    }
  }

  /*
  行为：提供发起受保护请求所需的令牌和会话版本。
  实现：检查本地到期时刻，过期立即清会话，不解析 JWT 内容授予权限。
  */
  function getRequestSession(): { accessToken: string; version: number } | null {
    if (!session.value) return null
    if (Date.parse(session.value.expiresAt) <= Date.now()) {
      clearSession('登录已失效，请重新登录')
      return null
    }
    return { accessToken: session.value.accessToken, version: version.value }
  }

  /*
  行为：只撤销当前请求所属的会话。
  实现：比较版本后清理，较早请求的 401 不影响后来建立的新会话。
  */
  function expireSession(requestVersion: number): void {
    if (requestVersion === version.value) clearSession('登录已失效，请重新登录')
  }

  /*
  行为：完成管理员登录并建立前端会话。
  实现：等待服务端结果，拒绝普通用户与失效凭证，保存前仅接受本次登录版本。
  */
  async function login(account: string, password: string): Promise<void> {
    if (isLoggingIn.value) return
    clearSession()
    isLoggingIn.value = true
    const requestVersion = version.value
    const controller = new AbortController()
    loginController = controller
    try {
      const result = await api.login(account.trim(), password, controller.signal)
      if (controller.signal.aborted || version.value !== requestVersion) {
        throw new DOMException('登录请求已取消', 'AbortError')
      }
      if (result.user.role !== 'ADMIN' || result.user.status === 'DISABLED') {
        throw new ApiError('当前账号无后台访问权限，请使用管理员账号', 403, 'FORBIDDEN')
      }
      if (Date.parse(result.expiresAt) <= Date.now()) {
        throw new ApiError('登录凭证已经失效，请重新登录', 401, 'UNAUTHENTICATED')
      }
      session.value = result
      verified = true
      try {
        storage.setItem(sessionKey, JSON.stringify(result))
      } catch {
        notice.value = '浏览器未允许保存会话，刷新页面后需要重新登录'
      }
    } finally {
      if (requestVersion === version.value) {
        isLoggingIn.value = false
        loginController = null
      }
    }
  }

  /*
  行为：在受保护页面挂载之前确认有效管理员身份。
  实现：首次恢复会话调用本人信息接口，并发导航共享验证；网络失败向导航入口报告。
  */
  async function ensureSession(): Promise<boolean> {
    if (!getRequestSession()) return false
    if (verified) return true
    if (verification) return verification
    const requestVersion = version.value
    const controller = new AbortController()
    verifyController = controller
    verification = (async () => {
      try {
        const user = await api.getMe(controller.signal)
        if (requestVersion !== version.value || !session.value || controller.signal.aborted)
          return false
        if (!getRequestSession()) return false
        if (user.role !== 'ADMIN' || user.status === 'DISABLED') {
          clearSession('当前账号无后台访问权限，请重新登录')
          return false
        }
        session.value = { ...session.value, user }
        verified = true
        return true
      } catch (error) {
        if (requestVersion !== version.value || controller.signal.aborted) return false
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          clearSession(error.message)
          return false
        }
        throw error
      } finally {
        if (requestVersion === version.value) {
          verification = null
          verifyController = null
        }
      }
    })()
    return verification
  }

  restoreSession()

  return {
    user: computed(() => session.value?.user || null),
    version: readonly(version),
    isLoggingIn: readonly(isLoggingIn),
    notice: readonly(notice),
    hasSession: computed(() => session.value !== null),
    login,
    clearSession,
    getRequestSession,
    expireSession,
    ensureSession,
  }
}

export type AuthState = ReturnType<typeof createAuthState>
