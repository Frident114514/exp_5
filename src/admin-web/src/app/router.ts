import { watch } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

import { getErrorMessage } from '../common/data/http'
import AdminLayout from './AdminLayout.vue'
import { auth } from './services'

/*
行为：选择登录后可访问的内部管理页面。
实现：只接受用户页与动态页路径，其他输入回到默认用户页。
*/
export function getReturnPath(input: unknown): string {
  if (typeof input !== 'string') return '/users'
  return /^\/(users|posts)(?:\?[^#]*)?$/.test(input) ? input : '/users'
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../modules/auth/pages/LoginPage.vue'),
    },
    {
      path: '/',
      component: AdminLayout,
      meta: { requiresAdmin: true },
      children: [
        { path: '', redirect: '/users' },
        {
          path: 'users',
          name: 'users',
          component: () => import('../modules/users/pages/UsersPage.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'posts',
          name: 'posts',
          component: () => import('../modules/posts/pages/PostsPage.vue'),
          meta: { title: '动态管理' },
        },
      ],
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('../common/components/StatusPage.vue'),
      props: { kind: 'forbidden' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'notFound',
      component: () => import('../common/components/StatusPage.vue'),
      props: { kind: 'notFound' },
    },
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAdmin) return true
  try {
    if (await auth.ensureSession()) return true
    return { name: 'login', query: { returnTo: getReturnPath(to.fullPath) } }
  } catch (error) {
    // 身份复核失败时阻止后台挂载，同时保留会话供用户主动重试。
    return {
      name: 'login',
      query: { returnTo: getReturnPath(to.fullPath), verifyError: getErrorMessage(error) },
    }
  }
})

watch(auth.hasSession, (hasSession) => {
  if (!hasSession && router.currentRoute.value.meta.requiresAdmin) {
    void router.replace({
      name: 'login',
      query: { returnTo: getReturnPath(router.currentRoute.value.fullPath) },
    })
  }
})
