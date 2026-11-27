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

/**
 * 合并后路由表与 pages.json 的一致性校验。
 * 三个成员的页面都登记在这里：任一方改了路径或标题却只改一边，本文件即失败。
 */

const pagesConfig = pagesJson as {
  pages: { path: string; style?: { navigationBarTitleText?: string } }[]
}

const routeEntries = Object.entries(ROUTES) as [RouteName, RouteDefinition][]

describe('路由表与 pages.json 一致性', () => {
  it('每个登记路由都在 pages.json 中注册', () => {
    const registered = new Set(pagesConfig.pages.map((page) => page.path))
    for (const [, route] of routeEntries) {
      expect(registered.has(toPagePath(route)), `${route.path} 未在 pages.json 注册`).toBe(true)
    }
  })

  it('pages.json 中没有未登记的路由', () => {
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

  it('页面负责人只能是 M05、M06、M07，且三个成员都有页面', () => {
    const owners = new Set(routeEntries.map(([, route]) => route.owner))
    for (const owner of owners) {
      expect(['M05', 'M06', 'M07']).toContain(owner)
    }
    expect(owners.has('M05')).toBe(true)
    expect(owners.has('M06')).toBe(true)
    expect(owners.has('M07')).toBe(true)
  })

  it('合并后共 10 个页面', () => {
    expect(routeEntries).toHaveLength(10)
    expect(pagesConfig.pages).toHaveLength(10)
  })
})

describe('登录要求', () => {
  it('公开浏览页面不要求登录', () => {
    for (const name of ['home', 'square', 'detail', 'login', 'register'] as RouteName[]) {
      expect(getRoute(name).requiresAuth, `${name} 应允许未登录访问`).toBe(false)
    }
  })

  it('作者流程与账号设置要求登录', () => {
    for (const name of ['publish', 'myPosts', 'edit', 'profile', 'password'] as RouteName[]) {
      expect(getRoute(name).requiresAuth, `${name} 应要求登录`).toBe(true)
    }
  })
})

describe('buildRouteUrl', () => {
  it('无参数时返回纯路径', () => {
    expect(buildRouteUrl('square')).toBe('/pages/square/index')
    expect(buildRouteUrl('home')).toBe('/pages/home/index')
  })

  it('详情与编辑页的必填参数', () => {
    expect(buildRouteUrl('detail', { postId: 12 })).toBe('/pages/detail/index?postId=12')
    expect(buildRouteUrl('edit', { postId: 12 })).toBe('/pages/edit/index?postId=12')
  })

  it('拼接并编码查询参数', () => {
    expect(buildRouteUrl('square', { tag: '校园 生活' })).toBe(
      '/pages/square/index?tag=%E6%A0%A1%E5%9B%AD%20%E7%94%9F%E6%B4%BB'
    )
  })

  it('缺少必填参数时直接报错', () => {
    expect(() => buildRouteUrl('detail')).toThrow('路由 detail 缺少必填参数：postId')
    expect(() => buildRouteUrl('edit', { postId: '' })).toThrow('缺少必填参数')
  })

  it('空的可选参数不拼接', () => {
    expect(buildRouteUrl('login', { redirect: undefined })).toBe('/pages/login/index')
    expect(buildRouteUrl('login', { redirect: '   ' })).toBe('/pages/login/index')
  })
})

describe('findRouteByPath / isRouteName', () => {
  it('按路径反查路由', () => {
    expect(findRouteByPath('/pages/square/index')?.owner).toBe('M07')
    expect(findRouteByPath('pages/square/index')?.owner).toBe('M07')
    expect(findRouteByPath('/pages/publish/index')?.owner).toBe('M06')
    expect(findRouteByPath('/pages/unknown/index')).toBeUndefined()
  })

  it('未知路由名不被误判', () => {
    expect(isRouteName('square')).toBe(true)
    expect(isRouteName('unknown')).toBe(false)
    expect(isRouteName('toString')).toBe(false)
  })
})
