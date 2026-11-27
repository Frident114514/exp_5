import { describe, expect, it } from 'vitest'
import { AUTH_ENDPOINTS, AUTH_STORAGE_KEY, TOKEN_EXPIRE_HOURS } from '../src/api/auth'
import { API_ERROR_CODES, PAGE_QUERY_DEFAULTS, SUCCESS_CODE, isAdmin } from '../src/api/types'
import { buildApiUrl } from '../src/config/env'

/**
 * 这些断言把 specs/contracts/api.md 的表格"翻译"成测试：
 * 任何人改错路径、方法或错误码，都会在这里失败，而不是等到联调才发现。
 */
describe('账号认证端点与 api.md 一致', () => {
  it('路径与 U01 行一致（相对 /api/v1 基础地址）', () => {
    expect(AUTH_ENDPOINTS.register.path).toBe('/auth/register')
    expect(AUTH_ENDPOINTS.login.path).toBe('/auth/login')
    expect(AUTH_ENDPOINTS.me.path).toBe('/users/me')
    expect(AUTH_ENDPOINTS.changePassword.path).toBe('/users/me/password')
  })

  it('方法与权限标记一致', () => {
    expect(AUTH_ENDPOINTS.register).toMatchObject({ method: 'POST', requiresAuth: false })
    expect(AUTH_ENDPOINTS.login).toMatchObject({ method: 'POST', requiresAuth: false })
    expect(AUTH_ENDPOINTS.me).toMatchObject({ method: 'GET', requiresAuth: true })
    expect(AUTH_ENDPOINTS.changePassword).toMatchObject({ method: 'PUT', requiresAuth: true })
  })

  it('拼出的完整地址符合 /api/v1 前缀约定', () => {
    expect(buildApiUrl(AUTH_ENDPOINTS.login.path, 'http://127.0.0.1:8080/api/v1')).toBe(
      'http://127.0.0.1:8080/api/v1/auth/login'
    )
  })

  it('令牌有效期 12 小时，本地缓存键有版本后缀便于失效', () => {
    expect(TOKEN_EXPIRE_HOURS).toBe(12)
    expect(AUTH_STORAGE_KEY).toMatch(/\.v\d+$/)
  })
})

describe('通用响应与错误码与 api.md 一致', () => {
  it('成功 code 固定为 OK', () => {
    expect(SUCCESS_CODE).toBe('OK')
  })

  it('错误码覆盖 api.md「错误与权限边界」的七种情况', () => {
    expect(Object.keys(API_ERROR_CODES).sort()).toEqual(
      [
        'CONFLICT',
        'FILE_TOO_LARGE',
        'FORBIDDEN',
        'NOT_FOUND',
        'UNAUTHENTICATED',
        'UNSUPPORTED_MEDIA_TYPE',
        'VALIDATION_ERROR',
      ].sort()
    )
  })

  it('分页默认值与上限与 api.md 一致', () => {
    expect(PAGE_QUERY_DEFAULTS.page).toBe(1)
    expect(PAGE_QUERY_DEFAULTS.pageSize).toBe(10)
    expect(PAGE_QUERY_DEFAULTS.maxPageSize).toBe(50)
  })

  it('只有 ADMIN 角色被识别为管理员', () => {
    expect(isAdmin({ id: 1, account: 'a', nickname: 'A', role: 'ADMIN' })).toBe(true)
    expect(isAdmin({ id: 2, account: 'b', nickname: 'B', role: 'USER' })).toBe(false)
    expect(isAdmin(null)).toBe(false)
  })
})
