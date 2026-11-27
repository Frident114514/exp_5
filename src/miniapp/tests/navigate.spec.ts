import { beforeEach, describe, expect, it } from 'vitest'
import { installUniStub, type UniStub } from './helpers/uni-stub'
import { authStore, resetAuthStoreForTest } from '../src/stores/auth'
import {
  currentRouteUrl,
  decodeRouteParam,
  isSafeInternalPath,
  navigateAfterLogin,
  redirectToLogin,
  requireLogin,
} from '../src/utils/navigate'

let stub: UniStub

beforeEach(() => {
  stub = installUniStub()
  resetAuthStoreForTest()
  stub.storage.clear()
  authStore.clear()
})

function login(): void {
  authStore.setSession({
    accessToken: 'token-abc',
    expiresAt: new Date(Date.now() + 3600_000).toISOString(),
    user: { id: 1, account: 'demo', nickname: '示例用户', role: 'USER' },
  })
}

describe('isSafeInternalPath', () => {
  it('只接受站内页面路径，可带查询串', () => {
    expect(isSafeInternalPath('/pages/square/index')).toBe(true)
    expect(isSafeInternalPath('/pages/post-detail/index?postId=1')).toBe(true)
    expect(isSafeInternalPath('/pages/publish/index?postId=3&mode=edit')).toBe(true)
  })

  it('拒绝外链、协议相对地址、缺前导斜杠和页内锚点', () => {
    expect(isSafeInternalPath('https://evil.example.com/pages/square/index')).toBe(false)
    expect(isSafeInternalPath('//evil.example.com')).toBe(false)
    expect(isSafeInternalPath('pages/square/index')).toBe(false)
    expect(isSafeInternalPath('/pages/square/index#section')).toBe(false)
    expect(isSafeInternalPath('/admin/users')).toBe(false)
    expect(isSafeInternalPath('')).toBe(false)
  })
})

describe('navigateAfterLogin', () => {
  it('回到合法 redirect 指定的页面', () => {
    navigateAfterLogin('/pages/publish/index')
    expect(stub.calls.navigations.at(-1)).toEqual({ type: 'reLaunch', url: '/pages/publish/index' })
  })

  it('redirect 非法或为空时进入广场', () => {
    navigateAfterLogin('https://evil.example.com')
    expect(stub.calls.navigations.at(-1)).toEqual({ type: 'reLaunch', url: '/pages/square/index' })

    navigateAfterLogin()
    expect(stub.calls.navigations.at(-1)).toEqual({ type: 'reLaunch', url: '/pages/square/index' })
  })
})

describe('requireLogin', () => {
  it('未登录：返回 false 并跳登录页，带回跳地址', () => {
    expect(requireLogin()).toBe(false)

    const navigation = stub.calls.navigations.at(-1)
    expect(navigation?.type).toBe('navigateTo')
    expect(navigation?.url).toBe('/pages/login/index?redirect=%2Fpages%2Fsquare%2Findex')
  })

  it('未登录且指定目标页时，回跳地址使用目标页', () => {
    expect(requireLogin('/pages/publish/index')).toBe(false)
    expect(stub.calls.navigations.at(-1)?.url).toBe(
      '/pages/login/index?redirect=%2Fpages%2Fpublish%2Findex'
    )
  })

  it('已登录：返回 true 且不跳转', () => {
    login()
    expect(requireLogin()).toBe(true)
    expect(stub.calls.navigations).toHaveLength(0)
  })

  it('令牌过期视为未登录', () => {
    authStore.setSession({
      accessToken: 'token-abc',
      expiresAt: new Date(Date.now() - 1000).toISOString(),
      user: { id: 1, account: 'demo', nickname: '示例用户', role: 'USER' },
    })
    expect(requireLogin()).toBe(false)
    expect(stub.calls.navigations).toHaveLength(1)
  })
})

describe('redirectToLogin', () => {
  it('已经在登录页时不再压栈', () => {
    stub.setPages([{ route: 'pages/login/index', options: {} }])
    redirectToLogin()
    expect(stub.calls.navigations).toHaveLength(0)
  })

  it('拒绝非站内回跳地址，退回当前页', () => {
    redirectToLogin('https://evil.example.com')
    expect(stub.calls.navigations.at(-1)?.url).toBe(
      '/pages/login/index?redirect=%2Fpages%2Fsquare%2Findex'
    )
  })
})

describe('currentRouteUrl / decodeRouteParam', () => {
  it('把当前页面查询串编码进 redirect', () => {
    stub.setPages([{ route: 'pages/square/index', options: { tag: '校园' } }])
    expect(currentRouteUrl()).toBe('/pages/square/index?tag=%E6%A0%A1%E5%9B%AD')
  })

  it('解码页面参数，非法编码时原样返回', () => {
    expect(decodeRouteParam('%E6%A0%A1%E5%9B%AD')).toBe('校园')
    expect(decodeRouteParam('100%')).toBe('100%')
    expect(decodeRouteParam('')).toBe('')
    expect(decodeRouteParam(undefined)).toBe('')
  })
})
