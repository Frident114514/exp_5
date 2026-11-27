export interface AuthUser {
  id: number
  account?: string
  nickname: string
  role?: 'USER' | 'ADMIN'
}

export interface AuthSession {
  accessToken: string
  expiresAt?: string
  user?: AuthUser
}

export interface KeyValueStorage {
  get<T>(key: string): T | undefined
  set<T>(key: string, value: T): void
  remove(key: string): void
}

/** M05 正式登录状态使用的唯一键；保存结构即 AuthSession。 */
export const AUTH_SESSION_STORAGE_KEY = 'moments.auth.session'

const SESSION_KEYS = [
  AUTH_SESSION_STORAGE_KEY,
  'moments.auth',
  'auth.session',
]

const TOKEN_KEYS = ['moments.accessToken', 'accessToken', 'token']
const USER_KEYS = ['moments.currentUser', 'currentUser', 'userInfo', 'user']

const uniStorage: KeyValueStorage = {
  get<T>(key: string): T | undefined {
    try {
      const value = uni.getStorageSync(key)
      return value === '' || value === undefined || value === null ? undefined : (value as T)
    } catch {
      return undefined
    }
  },
  set<T>(key: string, value: T): void {
    uni.setStorageSync(key, value)
  },
  remove(key: string): void {
    uni.removeStorageSync(key)
  },
}

function parseObject(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }
  if (typeof value !== 'string' || !value.trim()) return null
  try {
    const parsed: unknown = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null
  } catch {
    return null
  }
}

function toAuthUser(value: unknown): AuthUser | null {
  const item = parseObject(value)
  if (!item) return null
  const id = Number(item.id)
  if (!Number.isInteger(id) || id < 1 || typeof item.nickname !== 'string') return null
  return {
    id,
    nickname: item.nickname,
    account: typeof item.account === 'string' ? item.account : undefined,
    role: item.role === 'USER' || item.role === 'ADMIN' ? item.role : undefined,
  }
}

function toSession(value: unknown): AuthSession | null {
  const item = parseObject(value)
  if (!item || typeof item.accessToken !== 'string' || !item.accessToken.trim()) return null
  const user = toAuthUser(item.user)
  return {
    accessToken: item.accessToken,
    expiresAt: typeof item.expiresAt === 'string' ? item.expiresAt : undefined,
    user: user ?? undefined,
  }
}

function readFirst(storage: KeyValueStorage, keys: string[]): unknown {
  for (const key of keys) {
    const value = storage.get<unknown>(key)
    if (value !== undefined) return value
  }
  return undefined
}

/**
 * 读取登录态。优先使用 M05 统一会话键，同时兼容常见的 token/user 分键存储，
 * 避免 M05 合入前 M06 作者页继续写死演示用户。
 */
export function readAuthSession(storage: KeyValueStorage = uniStorage): AuthSession | null {
  for (const key of SESSION_KEYS) {
    const session = toSession(storage.get<unknown>(key))
    if (session) return session
  }

  const tokenValue = readFirst(storage, TOKEN_KEYS)
  const token = typeof tokenValue === 'string' ? tokenValue.trim() : ''
  if (!token) return null

  const user = toAuthUser(readFirst(storage, USER_KEYS)) ?? undefined
  return { accessToken: token, user }
}

export function saveAuthSession(session: AuthSession, storage: KeyValueStorage = uniStorage): void {
  storage.set(AUTH_SESSION_STORAGE_KEY, session)
}

export function clearAuthSession(storage: KeyValueStorage = uniStorage): void {
  storage.remove(AUTH_SESSION_STORAGE_KEY)
}

export function isSessionExpired(session: AuthSession, now: number = Date.now()): boolean {
  if (!session.expiresAt) return false
  const expiresAt = Date.parse(session.expiresAt)
  return Number.isFinite(expiresAt) && expiresAt <= now
}