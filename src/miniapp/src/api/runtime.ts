import { createHttpPostApi } from './httpPostApi'
import { createMockPostApi } from './mockPostApi'
import { envelopeTransport } from './transport'
import { authStore } from '../stores/auth'
import { fetchMe } from './auth'
import type { AuthUser } from './session'
import { PostApiError, type PostApi } from './types'

const DEMO_USER: AuthUser = { id: 6006, nickname: 'M06 演示用户', role: 'USER' }

/** 真实接口为默认模式；测试或离线演示必须显式设置 VITE_MINIAPP_USE_MOCK=true。 */
export const isUsingMock = import.meta.env.VITE_MINIAPP_USE_MOCK === 'true'
export const mockPostApi = createMockPostApi({ currentUser: DEMO_USER })
export const realPostApi: PostApi = createHttpPostApi(envelopeTransport)

export function getPostApi(): PostApi {
  return isUsingMock ? mockPostApi : realPostApi
}

/** 作者页面与登录、广场共用 M05 登录态。 */
export function getCurrentUser(): AuthUser | null {
  if (isUsingMock) return mockPostApi.getCurrentUser()
  authStore.load()
  return authStore.isLoggedIn ? authStore.user : null
}

export async function ensureCurrentUser(): Promise<AuthUser> {
  const user = getCurrentUser()
  if (user) return user
  if (!authStore.isLoggedIn) {
    throw new PostApiError('UNAUTHENTICATED', '请先登录后再管理动态')
  }
  const profile = await fetchMe()
  authStore.setUser(profile)
  return profile
}
