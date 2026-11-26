import { API_ERROR_CODES } from './types.ts'

/** 请求失败的分类，页面据此决定提示文案与是否展示“重试”。 */
export type ApiErrorKind = 'network' | 'timeout' | 'http' | 'business'

export interface ApiErrorInit {
  status?: number
  code?: string
}

/**
 * 统一的前端请求错误。
 * 不使用参数属性写法，保持可被 Node 直接运行（类型擦除）与 Vue 构建共用。
 */
export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status: number
  readonly code: string

  constructor(kind: ApiErrorKind, message: string, init: ApiErrorInit = {}) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.status = init.status ?? 0
    this.code = init.code ?? ''
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

/** 动态不存在或已删除（逻辑删除后详情返回 404）。 */
export function isNotFound(error: unknown): boolean {
  return isApiError(error) && (error.status === 404 || error.code === API_ERROR_CODES.NOT_FOUND)
}

const STATUS_MESSAGE: Record<number, string> = {
  400: '请求参数有误，请稍后重试',
  401: '登录状态已失效，请重新登录',
  403: '没有权限查看该内容',
  404: '内容不存在或已被删除',
  409: '操作冲突，请刷新后重试',
  413: '文件过大，超出上传限制',
  415: '不支持的文件格式',
}

export function httpStatusMessage(status: number): string {
  if (STATUS_MESSAGE[status]) return STATUS_MESSAGE[status]
  if (status >= 500) return '服务端暂时不可用，请稍后重试'
  return '请求失败，请稍后重试'
}

/** 把任意异常转成可展示的提示文案。 */
export function describeError(error: unknown): string {
  if (isApiError(error)) {
    if (error.kind === 'network') return '网络连接失败，请确认本机服务端已启动'
    if (error.kind === 'timeout') return '请求超时，请检查网络后重试'
    return error.message || '加载失败，请稍后重试'
  }
  if (error instanceof Error && error.message) return error.message
  return '加载失败，请稍后重试'
}
