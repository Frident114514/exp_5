/**
 * 页面路由注册表（合并后共 10 个页面）
 *
 * 这里与 `src/pages.json` 一一对应，`tests/routes.spec.ts` 会自动校验：
 * 路径、导航标题、负责人、登录要求任一不一致，测试直接失败。
 *
 * 归属：M05 账号页与首页，M06 作者流程，M07 广场与详情。
 * 新增页面流程：① 本文件登记；② pages.json 增加同路径页面项与标题；③ 新建页面文件。
 */

export type RouteOwner = 'M05' | 'M06' | 'M07'

export interface RouteDefinition {
  /** 以 `/` 开头，与 pages.json 的 `pages[].path` 对应（去掉前导 `/`）。 */
  readonly path: string
  /** 导航栏标题，与 pages.json 的 navigationBarTitleText 保持一致。 */
  readonly title: string
  readonly owner: RouteOwner
  /** true 表示未登录访问时应先引导登录（公开浏览页为 false）。 */
  readonly requiresAuth: boolean
  readonly requiredParams: readonly string[]
  readonly optionalParams: readonly string[]
}

export const ROUTES = {
  home: {
    path: '/pages/home/index',
    title: '多媒体朋友圈',
    owner: 'M05',
    requiresAuth: false,
    requiredParams: [],
    optionalParams: [],
  },
  square: {
    path: '/pages/square/index',
    title: '动态广场',
    owner: 'M07',
    requiresAuth: false,
    requiredParams: [],
    // 后续阶段：from/to 为 YYYY-MM-DD 的北京时间自然日，tag 为单个标签。
    optionalParams: ['tag', 'from', 'to'],
  },
  detail: {
    path: '/pages/detail/index',
    title: '动态详情',
    owner: 'M07',
    requiresAuth: false,
    // postId：动态 ID（正整数）。
    requiredParams: ['postId'],
    optionalParams: [],
  },
  publish: {
    path: '/pages/publish/index',
    title: '发布动态',
    owner: 'M06',
    requiresAuth: true,
    requiredParams: [],
    optionalParams: [],
  },
  myPosts: {
    path: '/pages/my-posts/index',
    title: '我的动态',
    owner: 'M06',
    requiresAuth: true,
    requiredParams: [],
    optionalParams: [],
  },
  edit: {
    path: '/pages/edit/index',
    title: '编辑动态',
    owner: 'M06',
    requiresAuth: true,
    requiredParams: ['postId'],
    optionalParams: [],
  },
  login: {
    path: '/pages/login/index',
    title: '登录',
    owner: 'M05',
    requiresAuth: false,
    requiredParams: [],
    // redirect：登录成功后要返回的站内路径（对应 M05-C04）。
    optionalParams: ['redirect'],
  },
  register: {
    path: '/pages/register/index',
    title: '注册',
    owner: 'M05',
    requiresAuth: false,
    requiredParams: [],
    optionalParams: ['redirect'],
  },
  profile: {
    path: '/pages/profile/index',
    title: '个人资料',
    owner: 'M05',
    requiresAuth: true,
    requiredParams: [],
    optionalParams: [],
  },
  password: {
    path: '/pages/password/index',
    title: '修改密码',
    owner: 'M05',
    requiresAuth: true,
    requiredParams: [],
    optionalParams: [],
  },
} as const satisfies Record<string, RouteDefinition>

export type RouteName = keyof typeof ROUTES
export type RouteParamValue = string | number | undefined
export type RouteParams = Record<string, RouteParamValue>

export function isRouteName(value: string): value is RouteName {
  return Object.prototype.hasOwnProperty.call(ROUTES, value)
}

export function getRoute(name: RouteName): RouteDefinition {
  return ROUTES[name]
}

/** pages.json 使用的相对路径（去掉前导 `/`）。 */
export function toPagePath(route: RouteDefinition): string {
  return route.path.replace(/^\//, '')
}

/** 拼接可跳转的完整路径，例如 `buildRouteUrl('detail', { postId: 12 })`。 */
export function buildRouteUrl(name: RouteName, params: RouteParams = {}): string {
  const route = getRoute(name)

  for (const key of route.requiredParams) {
    const value = params[key]
    if (value === undefined || value === null || String(value).trim() === '') {
      throw new Error(`路由 ${name} 缺少必填参数：${key}`)
    }
  }

  const search: string[] = []
  for (const [key, rawValue] of Object.entries(params)) {
    if (rawValue === undefined || rawValue === null || String(rawValue).trim() === '') {
      continue
    }
    search.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(rawValue))}`)
  }

  return search.length === 0 ? route.path : `${route.path}?${search.join('&')}`
}

/** 按 path 反查路由，未知路径返回 undefined。 */
export function findRouteByPath(path: string): RouteDefinition | undefined {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return Object.values(ROUTES).find((route) => route.path === normalized)
}
