import { createAdminApi } from '../common/data/adminApi'
import { createHttpClient } from '../common/data/http'
import { createAuthState } from '../modules/auth/logic/authState'

export const isDemo = import.meta.env.DEV && import.meta.env.MODE === 'demo'
const fetchRequest = isDemo
  ? (await import('../demo/demoFetch')).demoFetch
  : globalThis.fetch.bind(globalThis)

// 应用入口连接请求与会话；请求层不依赖路由和页面提示组件。
const http = createHttpClient({
  baseUrl: import.meta.env.VITE_ADMIN_API_BASE_URL || '/api/v1',
  fetch: fetchRequest,
  getSession: () => authState.getRequestSession(),
  onUnauthorized: (version) => authState.expireSession(version),
})

export const api = createAdminApi(http)
const authState = createAuthState(
  api,
  globalThis.sessionStorage,
  isDemo ? 'moments.admin.demo-session' : undefined,
)
export const auth = authState
