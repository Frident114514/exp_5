import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import type { Router } from 'vue-router'

import App from '../src/App.vue'
import type { AuthState } from '../src/modules/auth/logic/authState'

let appRouter: Router
let auth: AuthState
let wrapper: ReturnType<typeof mount> | undefined

/*
行为：在完整前端页面中登录演示管理员。
实现：填写表单并提交，等待真实前端认证与路由流程完成。
*/
async function loginAdmin(): Promise<void> {
  await wrapper!.find('input[name="account"]').setValue('demo_admin')
  await wrapper!.find('input[name="password"]').setValue('demo1234')
  await wrapper!.find('form').trigger('submit')
  await vi.waitFor(() => expect(appRouter.currentRoute.value.path).toBe('/users'), {
    timeout: 2000,
  })
  await vi.waitFor(() => expect(wrapper!.findAll('.el-table__body tbody tr')).toHaveLength(10), {
    timeout: 2000,
  })
}

beforeAll(async () => {
  vi.stubEnv('DEV', true)
  vi.stubEnv('MODE', 'demo')
  sessionStorage.clear()
  appRouter = (await import('../src/app/router')).router
  auth = (await import('../src/app/services')).auth
})

afterEach(async () => {
  auth.clearSession()
  await appRouter.replace('/login')
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

afterAll(() => vi.unstubAllEnvs())

/*
行为：挂载使用实际路由和 Element Plus 的后台应用。
实现：先导航目标地址，再将根组件挂到测试 DOM，等待异步页面解析。
*/
async function openApp(path = '/login'): Promise<void> {
  await appRouter.push(path)
  wrapper = mount(App, {
    attachTo: document.body,
    global: { plugins: [[ElementPlus, { locale: zhCn }], appRouter] },
  })
  await flushPromises()
}

describe('后台页面与路由集成', () => {
  it('未登录直接输入管理 URL 时显示登录页', async () => {
    await openApp('/posts')
    expect(appRouter.currentRoute.value.path).toBe('/login')
    expect(wrapper!.text()).toContain('欢迎回来')
    expect(wrapper!.find('.admin-shell').exists()).toBe(false)
  })

  it('普通用户与错误密码均留在登录页', async () => {
    await openApp()
    await wrapper!.find('input[name="account"]').setValue('demo_user')
    await wrapper!.find('input[name="password"]').setValue('demo1234')
    await wrapper!.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper!.text()).toContain('当前账号无后台访问权限'), {
      timeout: 2000,
    })
    expect(auth.hasSession.value).toBe(false)
    await wrapper!.find('input[name="password"]').setValue('wrong-password')
    await wrapper!.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper!.text()).toContain('账号或密码错误'), { timeout: 2000 })
    expect(appRouter.currentRoute.value.path).toBe('/login')
  })

  it('管理员查询和分页可操作，空结果与失败显示不同反馈', async () => {
    await openApp()
    await loginAdmin()
    expect(wrapper!.text()).toContain('26 条记录')
    const next = wrapper!.find('button.btn-next')
    await next.trigger('click')
    await vi.waitFor(() => expect(wrapper!.text()).toContain('第 2 页'), { timeout: 2000 })
    const search = wrapper!.find('input[placeholder="输入账号或昵称"]')
    await search.setValue('没有这个用户')
    await wrapper!.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper!.text()).toContain('没有找到符合条件的记录'), {
      timeout: 2000,
    })
    expect(wrapper!.text()).toContain('第 1 页')
    await search.setValue('请求失败')
    await wrapper!.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper!.text()).toContain('演示请求失败'), { timeout: 2000 })
    expect(wrapper!.text()).toContain('刷新失败，当前为上次查询结果')
  })

  it('动态组合筛选与退出流程可操作', async () => {
    await openApp()
    await loginAdmin()
    await appRouter.push('/posts')
    await flushPromises()
    await vi.waitFor(() => expect(wrapper!.text()).toContain('24 条记录'), { timeout: 2000 })
    await wrapper!.find('input[placeholder="搜索动态正文"]').setValue('校园')
    await wrapper!.find('input[placeholder="例如 2"]').setValue('2')
    await wrapper!.find('input[placeholder="输入一个标签"]').setValue('校园')
    await wrapper!.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper!.text()).toContain('2 条记录'), { timeout: 2000 })
    expect(wrapper!.findAll('.el-table__body tbody tr')).toHaveLength(2)
    await wrapper!.find('button[aria-label="退出登录"]').trigger('click')
    await vi.waitFor(() => expect(appRouter.currentRoute.value.path).toBe('/login'))
    expect(wrapper!.find('.management-table').exists()).toBe(false)
    expect(auth.hasSession.value).toBe(false)
  })
})
