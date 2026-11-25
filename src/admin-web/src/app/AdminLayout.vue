<script setup lang="ts">
import { ChatDotSquare, Document, SwitchButton, User } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { auth, isDemo } from './services'

const route = useRoute()
const router = useRouter()
const title = computed(() => String(route.meta.title || '管理台'))

/*
行为：退出当前标签页的管理员会话。
实现：清除认证状态和在途请求版本，使用替换导航回到登录页。
*/
async function logout(): Promise<void> {
  auth.clearSession()
  await router.replace('/login')
}
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <RouterLink to="/users" class="brand">
        <span class="brand-symbol"
          ><el-icon><ChatDotSquare /></el-icon
        ></span>
        <span>朋友圈<span class="brand-caption">ADMIN CONSOLE</span></span>
      </RouterLink>
      <p class="nav-label">工作空间</p>
      <nav aria-label="后台导航" class="side-nav">
        <RouterLink to="/users" :class="{ active: route.path === '/users' }">
          <el-icon><User /></el-icon><span>用户管理</span>
        </RouterLink>
        <RouterLink to="/posts" :class="{ active: route.path === '/posts' }">
          <el-icon><Document /></el-icon><span>动态管理</span>
        </RouterLink>
      </nav>
      <div class="sidebar-note">
        <span class="status-dot"></span>管理员工作空间<span>多媒体朋友圈展示系统</span>
      </div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="breadcrumb">
          管理台 <span>/</span> <strong>{{ title }}</strong>
        </div>
        <div class="account-actions">
          <span v-if="isDemo" class="demo-chip">样例演示</span>
          <span class="account-avatar">{{ auth.user.value?.nickname.slice(0, 1) || '管' }}</span>
          <span class="account-name">{{ auth.user.value?.nickname }}</span>
          <el-button text aria-label="退出登录" @click="logout"
            ><el-icon><SwitchButton /></el-icon><span>退出</span></el-button
          >
        </div>
      </header>
      <main class="workspace">
        <el-alert
          v-if="isDemo"
          class="workspace-notice"
          title="当前为样例演示，所有账号和动态均为虚构数据。"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-alert
          v-if="auth.notice.value"
          class="workspace-notice"
          :title="auth.notice.value"
          type="warning"
          :closable="false"
          show-icon
        />
        <RouterView />
      </main>
      <footer class="workspace-footer">多媒体朋友圈展示系统<span>管理员工作空间</span></footer>
    </div>
  </div>
</template>
