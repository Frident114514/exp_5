<script setup lang="ts">
/**
 * 产品首页 · 维护人：M05（M05-S2）
 *
 * 小程序启动页：展示登录状态并提供四个主要入口。
 * 公开入口（广场）不需要登录；发布/我的动态/个人资料需要登录，
 * 未登录时由 `requireLogin` 引导到登录页并在登录后回到目标页。
 * 产品首页（M05）。合并后作为统一工程入口：登录状态 + 广场/发布/我的动态/个人资料入口。
 * 广场与详情由 M07 实现，发布/我的动态/编辑由 M06 实现，账号页由 M05 实现。
 */
import { computed } from 'vue'
import AppButton from '@/components/AppButton.vue'
import { resolveApiBaseUrl } from '@/config/env'
import { ROUTES, type RouteName } from '@/config/routes'
import { authStore } from '@/stores/auth'
import { confirmAction, toastInfo } from '@/utils/feedback'
import { openRoute, requireLogin } from '@/utils/navigate'

const isLoggedIn = computed(() => authStore.isLoggedIn)
const currentUser = computed(() => authStore.user)
const roleText = computed(() => (authStore.isAdmin ? '管理员' : '普通用户'))

const apiBaseUrl = resolveApiBaseUrl()

const ENTRY_TEXT: Record<string, { title: string; desc: string }> = {
  square: { title: '看广场', desc: '浏览大家发的图文与视频，可按标签和日期筛选' },
  publish: { title: '发布动态', desc: '写文字、选图片或短视频，打上标签' },
  myPosts: { title: '我的动态', desc: '查看、编辑或删除自己发过的内容' },
  profile: { title: '个人资料', desc: '查看账号信息、修改密码' },
}

const entryNames: RouteName[] = ['square', 'publish', 'myPosts', 'profile']
const entries = entryNames.map((name) => ({
  name,
  title: ENTRY_TEXT[name].title,
  desc: ENTRY_TEXT[name].desc,
  requiresAuth: ROUTES[name].requiresAuth,
}))

function openEntry(name: RouteName): void {
  // 需要登录的入口先守卫：未登录会跳登录页，登录后回到目标页。
  if (ROUTES[name].requiresAuth && !requireLogin(ROUTES[name].path)) {
    return
  }
  openRoute(name)
}

function goLogin(): void {
  openRoute('login')
}

function goRegister(): void {
  openRoute('register')
}

async function logout(): Promise<void> {
  const confirmed = await confirmAction('退出后需要重新登录才能发布或互动。', '退出登录')
  if (!confirmed) {
    return
  }
  authStore.clear()
  toastInfo('已退出登录')
}
</script>

<template>
  <view class="page">
    <!-- 欢迎区 -->
    <view class="section hero">
      <text class="hero__title">多媒体朋友圈</text>
      <text class="hero__subtitle">随手记录校园里的图文与视频，和同学互相评分、评论。</text>

      <view class="hero__status">
        <template v-if="isLoggedIn">
          <view class="hero__avatar">
            <text class="hero__avatar-text">{{ (currentUser?.nickname ?? '?').slice(0, 1) }}</text>
          </view>
          <view class="hero__who">
            <text class="hero__name">{{ currentUser?.nickname }}</text>
            <text class="hero__meta">{{ roleText }} · {{ currentUser?.account }}</text>
          </view>
        </template>
        <template v-else>
          <text class="hero__guest">当前未登录，广场和详情可以直接浏览。</text>
        </template>
      </view>

      <view class="hero__actions">
        <template v-if="isLoggedIn">
          <AppButton type="primary" :block="false" @click="openEntry('profile')">个人资料</AppButton>
          <AppButton type="ghost" :block="false" @click="logout">退出登录</AppButton>
        </template>
        <template v-else>
          <AppButton type="primary" :block="false" @click="goLogin">登录</AppButton>
          <AppButton type="ghost" :block="false" @click="goRegister">注册</AppButton>
        </template>
      </view>
    </view>

    <!-- 主要入口 -->
    <view class="section">
      <text class="section__title">开始使用</text>
      <view class="entry-list">
        <view
          v-for="item in entries"
          :key="item.name"
          class="entry"
          hover-class="entry--hover"
          @tap="openEntry(item.name)"
        >
          <view class="entry__main">
            <view class="entry__head">
              <text class="entry__title">{{ item.title }}</text>
              <text v-if="item.requiresAuth" class="entry__badge">需登录</text>
            </view>
            <text class="entry__desc">{{ item.desc }}</text>
          </view>
          <text class="entry__arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 运行环境（联调用，产品上可隐藏） -->
    <view class="section">
      <text class="section__title">运行环境</text>
      <view class="env-row">
        <text class="env-row__label">API 地址</text>
        <text class="env-row__value">{{ apiBaseUrl }}</text>
      </view>
    </view>

    <view class="footer">
      <text class="text-caption">M05 · 账号与公共层已就绪，发布/广场/详情由 M06、M07 实现</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.hero {
  &__title {
    font-size: 52rpx;
    font-weight: $font-weight-bold;
    color: $text-primary;
  }

  &__subtitle {
    display: block;
    margin-top: $space-xs;
    font-size: $font-size-body;
    line-height: $line-height-base;
    color: $text-secondary;
  }

  &__status {
    display: flex;
    align-items: center;
    gap: $space-sm;
    padding: $space-md;
    margin-top: $space-md;
    background-color: $bg-muted;
    border-radius: $radius-md;
  }

  &__avatar {
    @include flex-center;

    width: 80rpx;
    height: 80rpx;
    background-color: $brand-primary-light;
    border-radius: 50%;
  }

  &__avatar-text {
    font-size: $font-size-title;
    font-weight: $font-weight-bold;
    color: $brand-primary;
  }

  &__who {
    display: flex;
    flex-direction: column;
    gap: 2rpx;
    min-width: 0;
  }

  &__name {
    font-size: $font-size-subtitle;
    font-weight: $font-weight-medium;
    color: $text-primary;
  }

  &__meta {
    font-size: $font-size-caption;
    color: $text-tertiary;
  }

  &__guest {
    font-size: $font-size-body;
    color: $text-secondary;
  }

  &__actions {
    display: flex;
    gap: $space-sm;
    margin-top: $space-md;
  }
}

.entry-list {
  display: flex;
  flex-direction: column;
  margin-top: $space-xs;
}

.entry {
  @include flex-between;

  gap: $space-sm;
  padding: $space-md 0;
  border-bottom: 1px solid $divider-color;

  &:last-child {
    border-bottom: none;
  }

  &--flat {
    padding: $space-sm 0;
  }

  &--hover {
    background-color: $bg-muted;
  }

  &__main {
    display: flex;
    flex-direction: column;
    gap: 4rpx;
    min-width: 0;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: $space-xs;
  }

  &__title {
    font-size: $font-size-subtitle;
    font-weight: $font-weight-medium;
    color: $text-primary;
  }

  &__badge {
    padding: 2rpx $space-xs;
    font-size: 20rpx;
    color: $color-warning;
    background-color: $color-warning-light;
    border-radius: $radius-pill;
  }

  &__desc {
    font-size: $font-size-caption;
    line-height: $line-height-base;
    color: $text-tertiary;
  }

  &__arrow {
    font-size: $font-size-title;
    color: $text-placeholder;
  }
}

.env-row {
  @include flex-between;

  gap: $space-sm;
  padding: 6rpx 0;

  &__label {
    font-size: $font-size-caption;
    color: $text-tertiary;
  }

  &__value {
    flex: 1;
    font-size: $font-size-caption;
    color: $text-secondary;
    text-align: right;
    word-break: break-all;
  }
}

.footer {
  padding: $space-md 0 $space-xl;
  text-align: center;
}
</style>
