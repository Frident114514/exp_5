/**
 * 登录态 store · 维护人：M05
 *
 * 只负责"保存/读取/判断登录态"，不发请求（避免与 api/http 循环依赖）。
 * 页面通过 `authStore.user` 等 getter 读取，都是响应式的。
 *
 * 约定：
 *   - 令牌、过期时间、用户资料一起缓存在 `AUTH_STORAGE_KEY`（moments.auth.v1）；
 *   - 过期时间来自服务端 `expiresAt`（ISO 8601，令牌 12 小时），本地据此判断过期；
 *   - `expiresAt` 缺失或无法解析时按"未过期"处理，最终以服务端 401 为准；
 *   - 退出登录、改密成功、401 都必须调用 `clear()`，不能只清页面变量。
 */

import { reactive } from 'vue'
import { AUTH_STORAGE_KEY, TOKEN_EXPIRE_HOURS, isAdmin, type AuthUser } from '../api/types.ts'

export { AUTH_STORAGE_KEY, TOKEN_EXPIRE_HOURS }

/** 登录接口返回并缓存的会话。 */
export interface AuthSession {
  accessToken: string
  expiresAt: string
  user: AuthUser
}

interface AuthState {
  accessToken: string
  expiresAt: string
  user: AuthUser | null
}

const state = reactive<AuthState>({
  accessToken: '',
  expiresAt: '',
  user: null,
})

/** 本地是否已从缓存恢复过，避免重复读存储。 */
let restored = false

/** 判断令牌是否已过期；空值或无法解析一律视为未过期，交由服务端裁决。 */
export function isSessionExpired(expiresAt: string, now: number = Date.now()): boolean {
  if (!expiresAt) {
    return false
  }
  const timestamp = Date.parse(expiresAt)
  if (Number.isNaN(timestamp)) {
    return false
  }
  return timestamp <= now
}

function normalizeSession(raw: unknown): AuthSession | null {
  let value = raw
  if (typeof value === 'string' && value.trim().length > 0) {
    try {
      value = JSON.parse(value) as unknown
    } catch {
      return null
    }
  }
  if (!value || typeof value !== 'object') {
    return null
  }
  const record = value as Partial<AuthSession>
  if (typeof record.accessToken !== 'string' || record.accessToken.length === 0) {
    return null
  }
  if (!record.user || typeof record.user !== 'object') {
    return null
  }
  return {
    accessToken: record.accessToken,
    expiresAt: typeof record.expiresAt === 'string' ? record.expiresAt : '',
    user: record.user as AuthUser,
  }
}

function persist(): void {
  const session: AuthSession | null = state.user
    ? { accessToken: state.accessToken, expiresAt: state.expiresAt, user: state.user }
    : null
  try {
    if (session) {
      uni.setStorageSync(AUTH_STORAGE_KEY, session)
    } else {
      uni.removeStorageSync(AUTH_STORAGE_KEY)
    }
  } catch (error) {
    // 存储失败不影响本次会话，只是重启后需要重新登录。
    console.warn('[auth] 写入本地登录态失败', error)
  }
}

function apply(session: AuthSession | null): void {
  state.accessToken = session?.accessToken ?? ''
  state.expiresAt = session?.expiresAt ?? ''
  state.user = session?.user ?? null
}

export const authStore = {
  /** 当前令牌，未登录为空字符串。 */
  get token(): string {
    return state.accessToken
  },

  /** 令牌过期时间（ISO 8601），未登录为空字符串。 */
  get expiresAt(): string {
    return state.expiresAt
  },

  /** 当前用户资料，未登录为 null。 */
  get user(): AuthUser | null {
    return state.user
  },

  /** 是否已登录：有令牌且未过期。 */
  get isLoggedIn(): boolean {
    return state.accessToken.length > 0 && !isSessionExpired(state.expiresAt)
  },

  /** 是否管理员（决定是否展示后台入口）。 */
  get isAdmin(): boolean {
    return isAdmin(state.user)
  },

  /** 应用启动时恢复缓存；只执行一次，可重复调用。 */
  load(): void {
    if (restored) {
      return
    }
    restored = true
    let raw: unknown = ''
    try {
      raw = uni.getStorageSync(AUTH_STORAGE_KEY)
    } catch (error) {
      console.warn('[auth] 读取本地登录态失败', error)
      return
    }
    const session = normalizeSession(raw)
    if (!session) {
      return
    }
    if (isSessionExpired(session.expiresAt)) {
      try {
        uni.removeStorageSync(AUTH_STORAGE_KEY)
      } catch {
        // 忽略清理失败，下次启动会再次判断过期。
      }
      return
    }
    apply(session)
  },

  /** 登录/注册成功后写入会话并缓存。 */
  setSession(session: AuthSession): void {
    restored = true
    apply({
      accessToken: session.accessToken,
      expiresAt: typeof session.expiresAt === 'string' ? session.expiresAt : '',
      user: session.user,
    })
    persist()
  },

  /** 只更新用户资料（例如编辑资料、刷新 /users/me）。 */
  setUser(user: AuthUser): void {
    if (!state.accessToken) {
      return
    }
    state.user = user
    persist()
  },

  /** 退出登录、改密成功、收到 401 时调用，清空内存与缓存。 */
  clear(): void {
    restored = true
    apply(null)
    persist()
  },
}

/** 仅测试使用：丢弃内存状态并允许重新执行 load()。 */
export function resetAuthStoreForTest(): void {
  restored = false
  apply(null)
}
