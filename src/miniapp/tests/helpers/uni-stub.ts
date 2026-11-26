/**
 * 测试用 uni 运行时替身 · 维护人：M05
 *
 * 单元测试跑在 Node 下，没有小程序运行时；这里提供最小可用的 uni 全局对象，
 * 只实现请求层、登录态和跳转会用到的方法，便于断言"发了什么请求、跳到了哪里"。
 */

export interface UniRequestOptions {
  url: string
  method?: string
  data?: unknown
  header?: Record<string, string>
  timeout?: number
  success?: (res: { statusCode: number; data: unknown }) => void
  fail?: (err: unknown) => void
}

export interface UniNavigationCall {
  type: 'navigateTo' | 'redirectTo' | 'reLaunch' | 'navigateBack'
  url?: string
  delta?: number
}

export type Responder = (options: UniRequestOptions) => void

export interface UniStub {
  calls: {
    requests: UniRequestOptions[]
    navigations: UniNavigationCall[]
  }
  storage: Map<string, unknown>
  /** 设置下一次（及之后）请求的响应方式。 */
  respond: (responder: Responder) => void
  /** 设置当前页面栈，用于测试"当前页是否登录页"的判断。 */
  setPages: (pages: { route: string; options?: Record<string, string> }[]) => void
}

export function installUniStub(options: { respond?: Responder } = {}): UniStub {
  const requests: UniRequestOptions[] = []
  const navigations: UniNavigationCall[] = []
  const storage = new Map<string, unknown>()
  let responder: Responder =
    options.respond ??
    ((request) => {
      request.fail?.({ errMsg: 'no responder configured' })
    })
  let pages: { route: string; options?: Record<string, string> }[] = [
    { route: 'pages/plaza/index', options: {} },
  ]

  const uni = {
    request: (request: UniRequestOptions) => {
      requests.push(request)
      responder(request)
    },
    uploadFile: (request: UniRequestOptions) => {
      requests.push(request)
      responder(request)
    },
    getStorageSync: (key: string) => storage.get(key) ?? '',
    setStorageSync: (key: string, value: unknown) => {
      storage.set(key, value)
    },
    removeStorageSync: (key: string) => {
      storage.delete(key)
    },
    showToast: () => undefined,
    showLoading: () => undefined,
    hideLoading: () => undefined,
    showModal: (modal: { success?: (res: { confirm: boolean }) => void }) => {
      modal.success?.({ confirm: true })
    },
    navigateTo: (nav: { url: string }) => {
      navigations.push({ type: 'navigateTo', url: nav.url })
    },
    redirectTo: (nav: { url: string }) => {
      navigations.push({ type: 'redirectTo', url: nav.url })
    },
    reLaunch: (nav: { url: string }) => {
      navigations.push({ type: 'reLaunch', url: nav.url })
    },
    navigateBack: (nav: { delta?: number } = {}) => {
      navigations.push({ type: 'navigateBack', delta: nav.delta })
    },
  }

  const globalScope = globalThis as unknown as {
    uni: unknown
    getCurrentPages: () => unknown
  }
  globalScope.uni = uni
  globalScope.getCurrentPages = () => pages

  return {
    calls: { requests, navigations },
    storage,
    respond: (next: Responder) => {
      responder = next
    },
    setPages: (next) => {
      pages = next
    },
  }
}
