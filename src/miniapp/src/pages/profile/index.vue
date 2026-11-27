<script setup lang="ts">
/**
 * 个人资料页 · 维护人：M05（M05-S2）
 *
 * 依赖：GET /users/me（M01）；需要登录。
 * 只展示账号、昵称、角色，不展示密码哈希等敏感字段；
 * 退出登录清空本地登录态，改密入口跳转修改密码页。
 */
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppButton from '@/components/AppButton.vue'
import StateView from '@/components/StateView.vue'
import { fetchMe } from '@/api/auth'
import { isApiError } from '@/api/http'
import { ROUTES } from '@/config/routes'
import { authStore } from '@/stores/auth'
import { confirmAction, toastSuccess } from '@/utils/feedback'
import { openRoute, redirectToLogin, requireLogin } from '@/utils/navigate'

const loading = ref(true)
const loadError = ref('')

const user = computed(() => authStore.user)
const roleText = computed(() => (authStore.isAdmin ? '管理员' : '普通用户'))
const statusText = computed(() => (user.value?.status === 'DISABLED' ? '已禁用' : '正常'))

async function refresh(): Promise<void> {
  if (!authStore.isLoggedIn) {
    redirectToLogin()
    return
  }
  loading.value = authStore.user === null
  loadError.value = ''
  try {
    const me = await fetchMe()
    authStore.setUser(me)
  } catch (error) {
    // 401 已由请求层清理登录态并跳登录页，这里不再重复提示。
    if (!authStore.isLoggedIn) {
      return
    }
    loadError.value = isApiError(error) ? error.message : '加载个人资料失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 每次进入页面都刷新，保证改密或后台禁用后状态及时反映。
onShow(() => {
  void refresh()
})

async function logout(): Promise<void> {
  const confirmed = await confirmAction('退出后需要重新登录才能发布或互动。', '退出登录')
  if (!confirmed) {
    return
  }
  authStore.clear()
  toastSuccess('已退出登录')
  uni.reLaunch({ url: ROUTES.home.path })
}

function goPassword(): void {
  if (requireLogin()) {
    openRoute('password')
  }
}

function goMyPosts(): void {
  if (requireLogin()) {
    openRoute('myPosts')
  }
}

function goPublish(): void {
  if (requireLogin()) {
    openRoute('publish')
  }
}

function goPlaza(): void {
  openRoute('square')
}
</script>

<template>
  <view class="page">
    <StateView
      :status="loading ? 'loading' : loadError ? 'error' : 'ready'"
      :description="loadError"
      retry-text="重新加载"
      @retry="refresh"
    >
      <view class="section">
        <view class="head">
          <view class="avatar">
            <text class="avatar__text">{{ (user?.nickname ?? '?').slice(0, 1) }}</text>
          </view>
          <view class="head__info">
            <text class="head__name">{{ user?.nickname ?? '未登录' }}</text>
            <text class="head__account">账号：{{ user?.account ?? '—' }}</text>
          </view>
        </view>

        <view class="divider" />

        <view class="info-row">
          <text class="info-row__label">角色</text>
          <text class="info-row__value">{{ roleText }}</text>
        </view>
        <view class="info-row">
          <text class="info-row__label">账号状态</text>
          <text class="info-row__value">{{ statusText }}</text>
        </view>
        <view class="info-row">
          <text class="info-row__label">用户 ID</text>
          <text class="info-row__value">{{ user?.id ?? '—' }}</text>
        </view>
      </view>

      <view class="section">
        <text class="section__title">账号操作</text>
        <view class="stack actions">
          <AppButton type="secondary" @click="goMyPosts">我的动态</AppButton>
          <AppButton type="secondary" @click="goPublish">发布动态</AppButton>
          <AppButton type="ghost" @click="goPlaza">去广场看看</AppButton>
          <AppButton type="ghost" @click="goPassword">修改密码</AppButton>
          <AppButton type="danger" @click="logout">退出登录</AppButton>
        </view>
      </view>
    </StateView>
  </view>
</template>

<style lang="scss" scoped>
.head {
  display: flex;
  align-items: center;
  gap: $space-md;

  &__info {
    display: flex;
    flex-direction: column;
    gap: 4rpx;
    min-width: 0;
  }

  &__name {
    font-size: $font-size-title;
    font-weight: $font-weight-bold;
    color: $text-primary;
  }

  &__account {
    font-size: $font-size-caption;
    color: $text-tertiary;
  }
}

.avatar {
  @include flex-center;

  width: 112rpx;
  height: 112rpx;
  background-color: $brand-primary-light;
  border-radius: 50%;

  &__text {
    font-size: $font-size-headline;
    font-weight: $font-weight-bold;
    color: $brand-primary;
  }
}

.info-row {
  @include flex-between;

  padding: $space-sm 0;
  border-bottom: 1px solid $divider-color;

  &:last-child {
    border-bottom: none;
  }

  &__label {
    font-size: $font-size-body;
    color: $text-secondary;
  }

  &__value {
    font-size: $font-size-body;
    color: $text-primary;
  }
}

.actions {
  margin-top: $space-sm;
}
</style>
