/**
 * 页面跳转封装 · 维护人：M05
 *
 * 统一从 `config/routes.ts` 读取路径，避免页面里手写字符串导致路径失配。
 * 页面栈超过 10 层时 uni.navigateTo 会失败，这里自动降级为 redirectTo。
 */

import { buildRouteUrl, getRoute, ROUTES, type RouteName, type RouteParams } from '@/config/routes'

export interface OpenRouteOptions {
  /** true 时使用 redirectTo（替换当前页），否则 navigateTo（保留返回栈）。 */
  replace?: boolean
}

export function openRoute(name: RouteName, params: RouteParams = {}, options: OpenRouteOptions = {}): void {
  const url = buildRouteUrl(name, params)
  if (options.replace) {
    uni.redirectTo({ url })
    return
  }
  uni.navigateTo({
    url,
    fail: () => {
      // 页面栈达到上限（10 层）时降级，保证流程不中断。
      uni.redirectTo({ url })
    },
  })
}

/** 返回上一页；没有上一页（例如从分享进入）时回到首页。 */
export function goBack(): void {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack({ delta: 1 })
    return
  }
  uni.reLaunch({ url: ROUTES.home.path })
}

/** 登录相关页需要的 redirect 参数：当前页面的完整路径（含查询串）。 */
export function currentRouteUrl(): string {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  if (!current) {
    return ROUTES.home.path
  }
  const path = `/${current.route ?? ''}`
  const options = (current as { options?: Record<string, string> }).options ?? {}
  const search = Object.entries(options)
    .filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&')
  return search ? `${path}?${search}` : path
}

/**
 * 需要登录的页面被未登录用户访问时的入口：
 * 带上 redirect 跳到登录页，登录成功后由 M05-S2 的登录页跳回原位置。
 */
export function redirectToLogin(redirect: string = currentRouteUrl()): void {
  const login = getRoute('login')
  uni.navigateTo({ url: buildRouteUrl('login', { redirect: redirect || login.path }) })
}
