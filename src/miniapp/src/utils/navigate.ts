/**
 * 页面跳转与登录守卫 · 维护人：M05
 *
 * 统一从 `config/routes.ts` 读取路径，避免页面里手写字符串导致路径失配。
 * 需要登录的页面/操作在入口处调用 `requireLogin()`：
 *   - 已登录返回 true，继续执行；
 *   - 未登录跳登录页并把当前页面（含查询串）作为 `redirect` 传过去，
 *     登录成功后由登录页调用 `navigateAfterLogin` 回到原位置（对应 M05-C04）。
 * 公开页面（广场、详情、评论）不要调用，未登录也应能浏览。
 */

import { authStore } from '@/stores/auth'
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

/** 当前页面的完整路径（含查询串），用于登录后回跳。 */
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

/** 只允许跳回站内页面，防止 redirect 参数被伪造成外链或非法路径。 */
export function isSafeInternalPath(path: string): boolean {
  return /^\/pages\/[A-Za-z0-9_-]+\/[A-Za-z0-9_-]+(\?[^#\s]*)?$/.test(path.trim())
}

/** 页面参数可能被编码过一次，解码失败时原样返回，避免报错。 */
export function decodeRouteParam(value?: string | null): string {
  if (!value) {
    return ''
  }
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

/**
 * 需要登录的页面被未登录用户访问时的入口：
 * 带上 redirect 跳到登录页，登录成功后由登录页跳回原位置。
 */
export function redirectToLogin(redirect: string = currentRouteUrl()): void {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  const currentPath = current?.route ? `/${current.route}` : ''
  if (currentPath === ROUTES.login.path) {
    // 已经在登录页，避免重复压栈。
    return
  }
  const target = redirect && isSafeInternalPath(redirect) ? redirect : currentPath
  uni.navigateTo({ url: buildRouteUrl('login', { redirect: target || getRoute('login').path }) })
}

/** 登录页使用：校验通过后回到 redirect 指定的原页面，非法或为空时进入广场。 */
export function navigateAfterLogin(redirect?: string): void {
  const target = (redirect ?? '').trim()
  const url = isSafeInternalPath(target) ? target : ROUTES.plaza.path
  // reLaunch 清空页面栈，避免登录页残留在返回路径里。
  uni.reLaunch({ url })
}

/** 需要登录的页面/操作入口：已登录返回 true，未登录跳登录页并返回 false。 */
export function requireLogin(redirect?: string): boolean {
  if (authStore.isLoggedIn) {
    return true
  }
  redirectToLogin(redirect)
  return false
}
