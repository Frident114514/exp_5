<script setup lang="ts">
import { ChatDotSquare, Lock, User } from '@element-plus/icons-vue'
import { computed, onScopeDispose, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getReturnPath } from '../../../app/router'
import { auth, isDemo } from '../../../app/services'
import { getErrorMessage } from '../../../common/data/http'

const route = useRoute()
const router = useRouter()
const form = reactive({ account: '', password: '' })
const error = ref('')
const isVerifying = ref(false)
const verifyError = computed(() =>
  typeof route.query.verifyError === 'string' ? route.query.verifyError : '',
)
let isActive = true

onScopeDispose(() => {
  isActive = false
  if (auth.isLoggingIn.value) auth.clearSession()
})

/*
行为：提交登录表单并导航至合法管理页面。
实现：校验必填字段，等待认证完成，失败留在表单且不保存密码。
*/
async function submitLogin(): Promise<void> {
  if (auth.isLoggingIn.value || isVerifying.value) return
  error.value = ''
  if (!form.account.trim() || !form.password) {
    error.value = '请填写账号和密码'
    return
  }
  try {
    await auth.login(form.account, form.password)
    if (!isActive) return
    form.password = ''
    await router.replace(getReturnPath(route.query.returnTo))
  } catch (failure) {
    if (isActive) error.value = getErrorMessage(failure)
  }
}

/*
行为：在身份复核网络失败后继续验证保存的会话。
实现：主动等待本人信息请求，通过后重试目标路由，否则返回可登录状态。
*/
async function retrySession(): Promise<void> {
  if (isVerifying.value) return
  isVerifying.value = true
  error.value = ''
  try {
    if (await auth.ensureSession()) {
      if (isActive) await router.replace(getReturnPath(route.query.returnTo))
    } else {
      if (isActive) error.value = '会话不可用，请重新登录'
    }
  } catch (failure) {
    if (isActive) error.value = getErrorMessage(failure)
  } finally {
    if (isActive) isVerifying.value = false
  }
}

/*
行为：填入用于检查页面的虚构管理员账号。
实现：仅演示模式展示此入口，表单仍经过相同登录流程。
*/
function fillDemoAccount(): void {
  form.account = 'demo_admin'
  form.password = 'demo1234'
}
</script>

<template>
  <div class="login-shell">
    <section class="login-story">
      <div class="login-brand">
        <span class="brand-symbol"
          ><el-icon><ChatDotSquare /></el-icon></span
        ><span>朋友圈管理台</span>
      </div>
      <div class="story-content">
        <span class="eyebrow light">MOMENTS · ADMIN CONSOLE</span>
        <h1>让每一份分享，<br />都有清晰的管理。</h1>
        <p>在一个工作空间中查看用户与动态，<br />让内容管理有序进行。</p>
        <div class="story-preview" aria-hidden="true">
          <div class="preview-heading">
            <span class="status-dot"></span>工作空间<span>● ● ●</span>
          </div>
          <div class="preview-row">
            <span class="preview-avatar">林</span>
            <div><strong>校园日记</strong><span>记录生活里的小事</span></div>
            <span class="preview-tag">校园</span>
          </div>
          <div class="preview-row">
            <span class="preview-avatar warm">陈</span>
            <div><strong>今日学习记录</strong><span>分享新知识与新发现</span></div>
            <span class="preview-tag">学习</span>
          </div>
          <div class="preview-line"></div>
          <div class="preview-line short"></div>
        </div>
      </div>
      <span class="login-story-footer">多媒体朋友圈展示系统</span>
    </section>
    <main class="login-main">
      <div class="login-card">
        <span class="eyebrow">管理员入口</span>
        <h2>欢迎回来</h2>
        <p class="login-description">使用管理员账号登录工作空间。</p>
        <el-alert
          v-if="isDemo"
          class="login-alert"
          title="样例演示模式"
          description="管理员 demo_admin，普通用户 demo_user；密码均为 demo1234。"
          type="warning"
          :closable="false"
        />
        <el-alert
          v-if="auth.notice.value"
          class="login-alert"
          :title="auth.notice.value"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-alert
          v-if="verifyError && !error"
          class="login-alert"
          :title="verifyError"
          type="error"
          :closable="false"
          show-icon
        />
        <el-alert
          v-if="error"
          class="login-alert"
          :title="error"
          type="error"
          :closable="false"
          show-icon
        />
        <el-form :model="form" label-position="top" @submit.prevent="submitLogin">
          <el-form-item label="账号">
            <el-input
              v-model="form.account"
              name="account"
              autocomplete="username"
              placeholder="请输入管理员账号"
              :prefix-icon="User"
              size="large"
              :disabled="auth.isLoggingIn.value"
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              name="password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
              :prefix-icon="Lock"
              size="large"
              :disabled="auth.isLoggingIn.value"
            />
          </el-form-item>
          <el-button
            class="login-submit"
            type="primary"
            size="large"
            native-type="submit"
            :loading="auth.isLoggingIn.value"
            :disabled="isVerifying"
            >登录工作空间</el-button
          >
        </el-form>
        <el-button
          v-if="auth.hasSession.value && verifyError"
          class="login-secondary"
          :loading="isVerifying"
          @click="retrySession"
          >重新验证已有会话</el-button
        >
        <el-button v-if="isDemo" class="login-secondary" text @click="fillDemoAccount"
          >填入演示管理员账号</el-button
        >
        <p class="login-footnote">
          <el-icon><Lock /></el-icon>此入口仅供管理员使用
        </p>
      </div>
      <span class="login-copyright">多媒体朋友圈 · 管理工作空间</span>
    </main>
  </div>
</template>
