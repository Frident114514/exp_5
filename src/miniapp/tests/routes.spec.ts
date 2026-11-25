import { describe, expect, it } from 'vitest'
import pagesJson from '../src/pages.json'
import {
  ROUTES,
  buildRouteUrl,
  findRouteByPath,
  getRoute,
  isRouteName,
  toPagePath,
  type RouteDefinition,
  type RouteName,
} from '../src/config/routes'

const pagesConfig = pagesJson as {
  pages: { path: string; style?: { navigationBarTitleText?: string } }[]
}

const routeEntries = Object.entries(ROUTES) as [RouteName, RouteDefinition][]

describe('路由表与 pages.json 一致性', () => {
  it('每个登记路由都在 pages.json 中注册，否则跳转会失败', () => {
    const registered = new Set(pagesConfig.pages.map((page) => page.path))
    for (const [, route] of routeEntries) {
      expect(registered.has(toPagePath(route)), `${route.path} 未在 pages.json 注册`).toBe(true)
    }
  })

  it('pages.json 中没有未登记的路由，避免出现无人维护的页面', () => {
    const defined = new Set(routeEntries.map(([, route]) => toPagePath(route)))
    for (const page of pagesConfig.pages) {
      expect(defined.has(page.path), `pages.json 中的 ${page.path} 未在 routes.ts 登记`).toBe(true)
    }
  })

  it('导航栏标题与路由表标题一致', () => {
    for (const [, route] of routeEntries) {
      const page = pagesConfig.pages.find((item) => item.path === toPagePath(route))
      expect(page?.style?.navigationBarTitleText, `${route.path} 标题不一致`).toBe(route.title)
    }
  })

  it('页面负责人只能是 M05、M06、M07', () => {
    for (const [, route] of routeEntries) {
      expect(['M05', 'M06', 'M07']).toContain(route.owner)
    }
  })
})

describe('登录要求', () => {
  it('公开浏览页面不要求登录', () => {
    for (const name of ['home', 'login', 'register', 'plaza', 'postDetail'] as RouteName[]) {
      expect(getRoute(name).requiresAuth, `${name} 应允许未登录访问`).toBe(false)
    }
  })

  it('账号设置与作者操作页面要求登录', () => {
    for (const name of ['profile', 'password', 'publish', 'myPosts'] as RouteName[]) {
      expect(getRoute(name).requiresAuth, `${name} 应要求登录`).toBe(true)
    }
  })
})

describe('buildRouteUrl', () => {
  it('无参数时返回纯路径', () => {
    expect(buildRouteUrl('plaza')).toBe('/pages/plaza/index')
    expect(buildRouteUrl('home')).toBe('/pages/home/index')
  })

  it('拼接并编码查询参数', () => {
    expect(buildRouteUrl('postDetail', { postId: 12 })).toBe('/pages/post-detail/index?postId=12')
    expect(buildRouteUrl('plaza', { tag: '校园 生活' })).toBe(
      '/pages/plaza/index?tag=%E6%A0%A1%E5%9B%AD%20%E7%94%9F%E6%B4%BB'
    )
    expect(buildRouteUrl('plaza', { tag: '校园', from: '2026-11-01', to: '2026-11-25' })).toBe(
      '/pages/plaza/index?tag=%E6%A0%A1%E5%9B%AD&from=2026-11-01&to=2026-11-25'
    )
  })

  it('可选参数为空时不拼接到 URL', () => {
    expect(buildRouteUrl('publish', { postId: undefined })).toBe('/pages/publish/index')
    expect(buildRouteUrl('publish', { postId: '' })).toBe('/pages/publish/index')
    expect(buildRouteUrl('login', { redirect: '   ' })).toBe('/pages/login/index')
  })

  it('缺少必填参数时直接报错，避免带着错误参数进入页面', () => {
    expect(() => buildRouteUrl('postDetail')).toThrow('路由 postDetail 缺少必填参数：postId')
    expect(() => buildRouteUrl('postDetail', { postId: '' })).toThrow('缺少必填参数')
  })
})

describe('findRouteByPath / isRouteName', () => {
  it('按路径反查路由，兼容带不带前导斜杠', () => {
    expect(findRouteByPath('/pages/plaza/index')?.owner).toBe('M07')
    expect(findRouteByPath('pages/plaza/index')?.owner).toBe('M07')
    expect(findRouteByPath('/pages/unknown/index')).toBeUndefined()
  })

  it('未知路由名不被误判', () => {
    expect(isRouteName('plaza')).toBe(true)
    expect(isRouteName('unknown')).toBe(false)
    expect(isRouteName('toString')).toBe(false)
  })
})
