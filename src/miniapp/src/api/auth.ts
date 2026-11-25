/**
 * 账号认证接口登记 · 维护人：M05
 *
 * 路径相对 `VITE_MINIAPP_API_BASE_URL`（已包含 `/api/v1`），完整地址由
 * `config/env.ts` 的 `buildApiUrl` 拼接。请求执行封装（token 携带、401 处理）
 * 在 M05-S2 的 `api/http.ts` 中实现，本文件只登记"方法与路径 + 出入参类型"。
 *
 * 对接人：M01（实现认证接口）。任何路径或字段变更需先改
 * specs/contracts/api.md 与 miniapp-module.md，再同步这里。
 */

import type {
  AuthUser,
  ChangePasswordRequest,
  LoginRequest,
  LoginResult,
  RegisterRequest,
  RegisterResult,
} from './types'

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

/** 端点 → 出入参类型，供 M05-S2 的请求封装与 M06/M07 复用。 */
export interface AuthEndpointTypes {
  register: { request: RegisterRequest; response: RegisterResult }
  login: { request: LoginRequest; response: LoginResult }
  me: { request: undefined; response: AuthUser }
  changePassword: { request: ChangePasswordRequest; response: null }
}

/** 登录令牌有效期（小时），与 api.md 一致，用于本地过期判断。 */
export const TOKEN_EXPIRE_HOURS = 12

/** 本地缓存的登录态键名，统一由 auth store 读写。 */
export const AUTH_STORAGE_KEY = 'moments.auth.v1'
