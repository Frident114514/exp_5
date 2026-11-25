/**
 * 页面路由注册表 · 维护人：M05
 *
 * 这里是全队唯一的页面路径与参数登记处：
 *   - `pages.json` 负责让页面真正被编译进小程序；
 *   - 本文件负责给出带类型的路径、标题、负责人与登录要求，供跳转与联调使用。
 * 两者必须一一对应，`tests/routes.spec.ts` 会自动校验，禁止只改一边。
 *
 * M06/M07 新增页面时的流程（见 specs/contracts/miniapp-module.md）：
 *   1. 在下方登记路由（path/title/owner/requiresAuth/参数）；
 *   2. 在 `src/pages.json` 增加同 path 的页面项；
 *   3. 页面文件放在 `src/pages/<目录>/index.vue`。
 *
 * 参数说明：
 *   - 必填参数缺失时 `buildRouteUrl` 直接抛错，避免带着错误参数进入页面；
 *   - 可选参数为空字符串或 undefined 时不拼接到 URL。
 */

export type RouteOwner = 'M05' | 'M06' | 'M07'

export interface RouteDefinition {
  /** 以 `/` 开头，与 pages.json 中的 `pages[].path` 一一对应（去掉前导 `/`）。 */
  readonly path: string
  /** 导航栏标题，与 pages.json 的 navigationBarTitleText 保持一致。 */
  readonly title: string
  /** 页面负责人，用于避免多人改同一文件。 */
  readonly owner: RouteOwner
  /** true 表示未登录访问时应先引导登录（公开浏览页为 false）。 */
  readonly requiresAuth: boolean
  /** 必填的页面参数名。 */
  readonly requiredParams: readonly string[]
  /** 可选的页面参数名。 */
  readonly optionalParams: readonly string[]
}

export const ROUTES = {
  home: {
    path: '/pages/home/index',
    title: '界面与组件基线',
    owner: 'M05',
    requiresAuth: false,
    requiredParams: [],
    optionalParams: [],
  },
  login: {
    path: '/pages/login/index',
    title: '登录',
    owner: 'M05',
    requiresAuth: false,
    requiredParams: [],
    // redirect：登录成功后要返回的原始页面（含查询串），对应 M05-C04。
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
  plaza: {
    path: '/pages/plaza/index',
    title: '动态广场',
    owner: 'M07',
    // 公开浏览：未登录也能看列表。
    requiresAuth: false,
    requiredParams: [],
    // tag 单个标签；from/to 为 YYYY-MM-DD 的北京时间自然日（含起止）。
    optionalParams: ['tag', 'from', 'to'],
  },
  postDetail: {
    path: '/pages/post-detail/index',
    title: '动态详情',
    owner: 'M07',
    requiresAuth: false,
    // postId：动态 ID（正整数）。评分/评论入口仅在登录且非作者时可用。
    requiredParams: ['postId'],
    optionalParams: [],
  },
  publish: {
    path: '/pages/publish/index',
    title: '发布动态',
    owner: 'M06',
    requiresAuth: true,
    // 无参数为新建；带 postId 为编辑本人动态。
    requiredParams: [],
    optionalParams: ['postId'],
  },
  myPosts: {
    path: '/pages/my-posts/index',
    title: '我的动态',
    owner: 'M06',
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

/**
 * 拼接可跳转的完整路径，例如
 * `buildRouteUrl('postDetail', { postId: 12 })` → `/pages/post-detail/index?postId=12`
 */
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

/** 按 path 反查路由，未知路径返回 undefined，便于统一 404 处理与埋点。 */
export function findRouteByPath(path: string): RouteDefinition | undefined {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return Object.values(ROUTES).find((route) => route.path === normalized)
}
