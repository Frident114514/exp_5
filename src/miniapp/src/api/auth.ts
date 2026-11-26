/**
 * 账号认证接口 · 维护人：M05
 *
 * 路径相对 `VITE_MINIAPP_API_BASE_URL`（已包含 `/api/v1`），请求执行统一走 `api/http.ts`：
 * 自动附加令牌、解析统一响应外壳、401 清理登录态。
 * 对接人：M01（实现认证接口）。任何路径或字段变更需先改 specs/contracts/api.md
 * 与 miniapp-module.md，再同步这里和调用页面。
 */

import { get, post, put } from './http'
import {
  AUTH_STORAGE_KEY,
  TOKEN_EXPIRE_HOURS,
  type AuthUser,
  type ChangePasswordRequest,
  type LoginRequest,
  type LoginResult,
  type RegisterRequest,
  type RegisterResult,
} from './types'

export { AUTH_STORAGE_KEY, TOKEN_EXPIRE_HOURS }

export interface AuthEndpoint {
  /** HTTP 方法。 */
  readonly method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  /** 相对 API 基础地址的路径。 */
  readonly path: string
  /** 是否需要携带登录令牌。 */
  readonly requiresAuth: boolean
}

/** 账号相关端点，与 api.md「端点清单」的 U01 行对应。 */
export const AUTH_ENDPOINTS = {
  register: { method: 'POST', path: '/auth/register', requiresAuth: false },
  login: { method: 'POST', path: '/auth/login', requiresAuth: false },
  me: { method: 'GET', path: '/users/me', requiresAuth: true },
  changePassword: { method: 'PUT', path: '/users/me/password', requiresAuth: true },
} as const satisfies Record<string, AuthEndpoint>

export type AuthEndpointName = keyof typeof AUTH_ENDPOINTS

/** 端点 → 出入参类型，供请求封装与调用方复用。 */
export interface AuthEndpointTypes {
  register: { request: RegisterRequest; response: RegisterResult }
  login: { request: LoginRequest; response: LoginResult }
  me: { request: undefined; response: AuthUser }
  changePassword: { request: ChangePasswordRequest; response: null }
}

/** `POST /auth/register`：角色由服务端固定为 USER。 */
export function register(payload: RegisterRequest): Promise<RegisterResult> {
  return post<RegisterResult>(AUTH_ENDPOINTS.register.path, payload, { withAuth: false })
}

/** `POST /auth/login`：成功返回令牌、过期时间与用户资料。 */
export function login(payload: LoginRequest): Promise<LoginResult> {
  return post<LoginResult>(AUTH_ENDPOINTS.login.path, payload, { withAuth: false })
}

/** `GET /users/me`：需要登录，用于进入个人资料页时刷新资料。 */
export function fetchMe(): Promise<AuthUser> {
  return get<AuthUser>(AUTH_ENDPOINTS.me.path)
}

/** `PUT /users/me/password`：改密成功后服务端使旧令牌失效。 */
export function changePassword(payload: ChangePasswordRequest): Promise<null> {
  return put<null>(AUTH_ENDPOINTS.changePassword.path, payload)
}
