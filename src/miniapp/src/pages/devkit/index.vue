<script setup lang="ts">
/**
 * 开发参考页：设计与组件基线示例 · 维护人：M05
 *
 * 这是 M05-S1 的示例页，从首页移出后保留为 M06/M07 的开工参照：
 *   - 设计令牌（颜色 / 字号 / 间距）实际取值；
 *   - AppButton 全部状态与"防重复提交"效果；
 *   - FormField + utils/validate 的校验写法（本页不发请求）；
 *   - StateView 的 loading / empty / error / ready 四种状态；
 *   - 全队路由登记表，点按可跳到对应页面。
 * 复制本页的写法即可保证三个成员界面一致。产品首页是 pages/home/index。
 */
import { computed, reactive, ref } from 'vue'
import AppButton from '@/components/AppButton.vue'
import FormField from '@/components/FormField.vue'
import StateView from '@/components/StateView.vue'
import { resolveApiBaseUrl, resolveMediaOrigin, resolveUseMock } from '@/config/env'
import { ROUTES, buildRouteUrl, type RouteDefinition, type RouteName } from '@/config/routes'
import { authStore } from '@/stores/auth'
import { confirmAction, toastInfo, toastSuccess } from '@/utils/feedback'
import { openRoute, requireLogin } from '@/utils/navigate'
import {
  validateAccount,
  validateNickname,
  validatePasswordConfirm,
  validatePasswordStrength,
  type ValidationResult,
} from '@/utils/validate'

/* ---------- 环境信息 ---------- */
const apiBaseUrl = resolveApiBaseUrl()
const mediaOrigin = resolveMediaOrigin()
const useMock = resolveUseMock()

/* ---------- 登录状态 ---------- */
const isLoggedIn = computed(() => authStore.isLoggedIn)
const currentUser = computed(() => authStore.user)
const roleText = computed(() => (authStore.isAdmin ? '管理员' : '普通用户'))

function goLogin(): void {
  openRoute('login')
}

function goProfile(): void {
  if (requireLogin()) {
    openRoute('profile')
  }
}

async function logout(): Promise<void> {
  if (!authStore.isLoggedIn) {
    return
  }
  const confirmed = await confirmAction('退出后需要重新登录才能发布或互动。', '退出登录')
  if (!confirmed) {
    return
  }
  authStore.clear()
  toastInfo('已退出登录')
}

/* ---------- 设计令牌 ---------- */
const colorTokens = [
  { name: '品牌主色', token: '$brand-primary', value: '#2F6FEB' },
  { name: '成功', token: '$color-success', value: '#12A150' },
  { name: '警示', token: '$color-warning', value: '#E08A00' },
  { name: '危险', token: '$color-danger', value: '#E5484D' },
  { name: '主文字', token: '$text-primary', value: '#1D2129' },
  { name: '次文字', token: '$text-secondary', value: '#4E5969' },
  { name: '辅助文字', token: '$text-tertiary', value: '#86909C' },
  { name: '页面底色', token: '$bg-page', value: '#F4F5F7' },
]

const fontSizeTokens = [
  { name: '标题', token: '$font-size-headline', size: '44rpx' },
  { name: '小标题', token: '$font-size-title', size: '36rpx' },
  { name: '正文强调', token: '$font-size-subtitle', size: '32rpx' },
  { name: '正文', token: '$font-size-body', size: '28rpx' },
  { name: '辅助', token: '$font-size-caption', size: '22rpx' },
]

const spacingTokens = [
  { name: 'xs', value: '8rpx' },
  { name: 'sm', value: '16rpx' },
  { name: 'md', value: '24rpx' },
  { name: 'lg', value: '32rpx' },
  { name: 'xl', value: '48rpx' },
]

/* ---------- 按钮 ---------- */
const clickCount = ref(0)
const asyncLoading = ref(false)

function handlePrimaryClick(): void {
  clickCount.value += 1
}

/** 模拟一次耗时请求：加载期间按钮被禁用，连续点击不会重复触发。 */
function handleAsyncDemo(): void {
  asyncLoading.value = true
  setTimeout(() => {
    asyncLoading.value = false
    toastInfo(`模拟请求完成，期间额外点击被忽略`)
  }, 1200)
}

/* ---------- 表单校验 ---------- */
type FieldKey = 'account' | 'password' | 'confirm' | 'nickname'

const form = reactive<Record<FieldKey, string>>({
  account: '',
  password: '',
  confirm: '',
  nickname: '',
})

const errors = reactive<Record<FieldKey, string>>({
  account: '',
  password: '',
  confirm: '',
  nickname: '',
})

const formSubmitting = ref(false)
const touched = ref(false)

const validators: Record<FieldKey, () => ValidationResult> = {
  account: () => validateAccount(form.account),
  password: () => validatePasswordStrength(form.password),
  confirm: () => validatePasswordConfirm(form.confirm, form.password),
  nickname: () => validateNickname(form.nickname),
}

const fieldKeys = Object.keys(validators) as FieldKey[]

function validateField(key: FieldKey): string {
  return validators[key]() ?? ''
}

/** 失焦校验：符合"不边打字边报错"的约定。 */
function handleFieldBlur(key: FieldKey): void {
  errors[key] = validateField(key)
}

function clearError(key: FieldKey): void {
  errors[key] = ''
}

function submitForm(): void {
  touched.value = true
  let valid = true
  for (const key of fieldKeys) {
    const message = validateField(key)
    errors[key] = message
    if (message) {
      valid = false
    }
  }
  if (!valid) {
    toastInfo('请先修正表单中的错误')
    return
  }
  formSubmitting.value = true
  // 示例页不发真实请求；M05-S2 的注册/登录页会替换为 api/http 调用。
  setTimeout(() => {
    formSubmitting.value = false
    toastSuccess('表单校验通过')
  }, 500)
}

function resetForm(): void {
  for (const key of fieldKeys) {
    form[key] = ''
    errors[key] = ''
  }
  touched.value = false
}

/* ---------- 页面状态 ---------- */
type StateStatus = 'loading' | 'empty' | 'error' | 'ready'

const stateDemo = ref<StateStatus>('ready')
const stateOptions: { label: string; value: StateStatus }[] = [
  { label: '加载中', value: 'loading' },
  { label: '空数据', value: 'empty' },
  { label: '加载失败', value: 'error' },
  { label: '正常', value: 'ready' },
]
const retryCount = ref(0)

function handleRetry(): void {
  retryCount.value += 1
  toastInfo(`第 ${retryCount.value} 次点击重试`)
}

/* ---------- 路由登记表 ---------- */
const routeEntries = (Object.keys(ROUTES) as RouteName[])
  .filter((name) => name !== 'devkit')
  .map((name) => ({ name, route: ROUTES[name] as RouteDefinition }))

function openDemoRoute(name: RouteName): void {
  const params = name === 'postDetail' ? { postId: 1 } : {}
  // 需要登录的页面先做守卫：未登录会跳到登录页并把目标页作为 redirect，登录后回到目标页。
  if (ROUTES[name].requiresAuth && !requireLogin(buildRouteUrl(name, params))) {
    return
  }
  openRoute(name, params)
}
</script>

<template>
  <view class="page">
    <!-- 1. 工程与环境 -->
    <view class="section">
      <text class="section__title">组件与样式基线（开发参考）</text>
      <text class="section__desc">
        uni-app + Vue 3 + TypeScript 工程骨架。本页演示公共样式、按钮、表单校验与页面状态，
        M06/M07 可直接参照复制；产品首页见 pages/home/index。
      </text>
      <view class="divider" />
      <view class="env-row">
        <text class="env-row__label">API 地址</text>
        <text class="env-row__value">{{ apiBaseUrl }}</text>
      </view>
      <view class="env-row">
        <text class="env-row__label">媒体源</text>
        <text class="env-row__value">{{ mediaOrigin }}</text>
      </view>
      <view class="env-row">
        <text class="env-row__label">模拟接口</text>
        <text class="env-row__value">{{ useMock ? '已开启（本地样例数据）' : '关闭（走真实服务端）' }}</text>
      </view>
    </view>

    <!-- 登录与账号入口 -->
    <view class="section">
      <text class="section__title">当前登录状态</text>
      <template v-if="isLoggedIn">
        <text class="section__desc">
          已登录：{{ currentUser?.nickname }}（{{ roleText }}），账号 {{ currentUser?.account }}。
        </text>
        <view class="session-actions">
          <AppButton type="secondary" :block="false" @click="goProfile">个人资料</AppButton>
          <AppButton type="ghost" :block="false" @click="logout">退出登录</AppButton>
        </view>
      </template>
      <template v-else>
        <text class="section__desc">
          未登录。广场与详情可以直接浏览；发布、评分、评论会先引导登录，并在登录后回到原页面。
        </text>
        <view class="session-actions">
          <AppButton type="primary" :block="false" @click="goLogin">去登录</AppButton>
          <AppButton type="ghost" :block="false" @click="openDemoRoute('plaza')">先看广场</AppButton>
        </view>
      </template>
    </view>

    <!-- 2. 颜色 -->
    <view class="section">
      <text class="section__title">颜色令牌</text>
      <view class="swatches">
        <view v-for="item in colorTokens" :key="item.token" class="swatch">
          <view class="swatch__chip" :style="{ backgroundColor: item.value }" />
          <text class="swatch__name">{{ item.name }}</text>
          <text class="swatch__token">{{ item.token }}</text>
        </view>
      </view>
    </view>

    <!-- 3. 字号与间距 -->
    <view class="section">
      <text class="section__title">字号与间距</text>
      <view class="type-scale">
        <view v-for="item in fontSizeTokens" :key="item.token" class="type-scale__row">
          <text :style="{ fontSize: item.size }" class="type-scale__sample">{{ item.name }}</text>
          <text class="type-scale__token">{{ item.token }}</text>
        </view>
      </view>
      <view class="divider" />
      <view class="spacing-list">
        <view v-for="item in spacingTokens" :key="item.name" class="spacing-list__row">
          <text class="spacing-list__name">{{ item.name }}</text>
          <view class="spacing-list__bar" :style="{ width: item.value }" />
          <text class="spacing-list__value">{{ item.value }}</text>
        </view>
      </view>
    </view>

    <!-- 4. 按钮 -->
    <view class="section">
      <text class="section__title">按钮</text>
      <text class="section__desc">四种视觉类型 + 加载态；加载中或禁用时不响应点击。</text>
      <view class="stack button-demo">
        <AppButton type="primary" @click="handlePrimaryClick">主要按钮（已点击 {{ clickCount }} 次）</AppButton>
        <AppButton type="secondary" @click="toastInfo('次要按钮')">次要按钮</AppButton>
        <AppButton type="ghost" @click="toastInfo('描边按钮')">描边按钮</AppButton>
        <AppButton type="danger" @click="toastInfo('危险操作需要二次确认')">危险按钮</AppButton>
        <AppButton type="primary" :loading="asyncLoading" @click="handleAsyncDemo">
          {{ asyncLoading ? '提交中…' : '防重复提交演示（1.2 秒）' }}
        </AppButton>
        <AppButton type="primary" disabled>禁用状态</AppButton>
      </view>
    </view>

    <!-- 5. 表单与校验 -->
    <view class="section">
      <text class="section__title">表单与校验</text>
      <text class="section__desc">
        失焦时校验，输入时清除旧错误；提交时汇总校验。示例页不发请求。
      </text>
      <view class="stack form-demo">
        <FormField
          v-model="form.account"
          label="账号"
          placeholder="4–20 位字母、数字或下划线"
          required
          :maxlength="20"
          :error="errors.account"
          hint="注册后会用于登录"
          @update:model-value="clearError('account')"
          @blur="handleFieldBlur('account')"
        />
        <FormField
          v-model="form.password"
          label="密码"
          type="password"
          placeholder="8–32 位，需含字母和数字"
          required
          :maxlength="32"
          :error="errors.password"
          @update:model-value="clearError('password')"
          @blur="handleFieldBlur('password')"
        />
        <FormField
          v-model="form.confirm"
          label="确认密码"
          type="password"
          placeholder="再次输入密码"
          required
          :maxlength="32"
          :error="errors.confirm"
          @update:model-value="clearError('confirm')"
          @blur="handleFieldBlur('confirm')"
        />
        <FormField
          v-model="form.nickname"
          label="昵称"
          placeholder="1–20 个字符"
          required
          :maxlength="20"
          :error="errors.nickname"
          @update:model-value="clearError('nickname')"
          @blur="handleFieldBlur('nickname')"
        />
        <view class="form-demo__actions">
          <AppButton type="primary" :loading="formSubmitting" @click="submitForm">校验并提交</AppButton>
          <AppButton type="ghost" :block="false" @click="resetForm">重置</AppButton>
        </view>
        <text v-if="touched" class="text-caption">校验结果会显示在对应输入框下方。</text>
      </view>
    </view>

    <!-- 6. 页面状态 -->
    <view class="section">
      <text class="section__title">页面状态</text>
      <text class="section__desc">列表与详情页统一使用 StateView，失败时必须提供重试入口。</text>
      <view class="state-tabs">
        <view
          v-for="option in stateOptions"
          :key="option.value"
          class="state-tabs__item"
          :class="{ 'state-tabs__item--active': stateDemo === option.value }"
          @tap="stateDemo = option.value"
        >
          <text class="state-tabs__text">{{ option.label }}</text>
        </view>
      </view>

      <StateView
        :status="stateDemo"
        compact
        :description="stateDemo === 'error' ? '网络异常或服务端返回错误时的提示。' : '示例说明文字。'"
        @retry="handleRetry"
      >
        <view class="stack">
          <text class="section__desc">正常状态：这里是页面真实内容。</text>
          <text class="text-caption">已触发重试 {{ retryCount }} 次</text>
        </view>
      </StateView>
    </view>

    <!-- 7. 路由登记 -->
    <view class="section">
      <text class="section__title">路由登记表</text>
      <text class="section__desc">
        与 src/pages.json 一一对应；"需登录"的页面在未登录时先引导登录（M05-S2 实现）。
      </text>
      <view class="divider" />
      <view class="route-list">
        <view
          v-for="item in routeEntries"
          :key="item.name"
          class="route-list__row"
          hover-class="route-list__row--hover"
          @tap="openDemoRoute(item.name)"
        >
          <view class="route-list__main">
            <text class="route-list__title">{{ item.route.title }}</text>
            <text class="route-list__path">{{ item.route.path }}</text>
          </view>
          <view class="route-list__meta">
            <text class="route-list__owner">{{ item.route.owner }}</text>
            <text v-if="item.route.requiresAuth" class="route-list__auth">需登录</text>
          </view>
        </view>
      </view>
    </view>

    <view class="home-footer">
      <text class="text-caption">M05-S1 基线 · 修改公共层前请先看 specs/contracts/miniapp-module.md</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* ---------- 环境信息 ---------- */
.env-row {
  @include flex-between;

  gap: $space-sm;
  margin-bottom: $space-xs;

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

/* ---------- 登录状态 ---------- */
.session-actions {
  display: flex;
  gap: $space-sm;
  margin-top: $space-md;
}

/* ---------- 颜色 ---------- */
.swatches {
  display: flex;
  flex-wrap: wrap;
  gap: $space-sm;
  margin-top: $space-sm;
}

.swatch {
  width: calc(50% - #{$space-xs});
  display: flex;
  flex-direction: column;
  gap: 2rpx;

  &__chip {
    height: 56rpx;
    margin-bottom: $space-xs;
    border: 1px solid $border-color;
    border-radius: $radius-sm;
  }

  &__name {
    font-size: $font-size-caption;
    color: $text-primary;
  }

  &__token {
    font-size: 20rpx;
    color: $text-tertiary;
  }
}

/* ---------- 字号 ---------- */
.type-scale {
  display: flex;
  flex-direction: column;
  gap: $space-xs;

  &__row {
    @include flex-between;
  }

  &__sample {
    color: $text-primary;
  }

  &__token {
    font-size: $font-size-caption;
    color: $text-tertiary;
  }
}

/* ---------- 间距 ---------- */
.spacing-list {
  display: flex;
  flex-direction: column;
  gap: $space-xs;

  &__row {
    display: flex;
    align-items: center;
    gap: $space-sm;
  }

  &__name {
    width: 48rpx;
    font-size: $font-size-caption;
    color: $text-tertiary;
  }

  &__bar {
    height: 20rpx;
    background-color: $brand-primary-light;
    border-radius: $radius-sm;
  }

  &__value {
    font-size: $font-size-caption;
    color: $text-tertiary;
  }
}

/* ---------- 按钮 ---------- */
.button-demo {
  margin-top: $space-sm;
}

/* ---------- 表单 ---------- */
.form-demo {
  margin-top: $space-sm;

  &__actions {
    display: flex;
    align-items: center;
    gap: $space-sm;
  }
}

/* ---------- 状态 ---------- */
.state-tabs {
  display: flex;
  gap: $space-xs;
  padding: $space-xs;
  margin: $space-sm 0;
  background-color: $bg-muted;
  border-radius: $radius-pill;

  &__item {
    flex: 1;
    padding: $space-xs 0;
    text-align: center;
    border-radius: $radius-pill;
  }

  &__item--active {
    background-color: $bg-card;
    box-shadow: $shadow-card;
  }

  &__text {
    font-size: $font-size-caption;
    color: $text-secondary;
  }
}

/* ---------- 路由 ---------- */
.route-list {
  display: flex;
  flex-direction: column;

  &__row {
    @include flex-between;

    padding: $space-md 0;
    border-bottom: 1px solid $divider-color;

    &:last-child {
      border-bottom: none;
    }
  }

  &__row--hover {
    background-color: $bg-muted;
  }

  &__main {
    display: flex;
    flex-direction: column;
    gap: 2rpx;
    min-width: 0;
  }

  &__title {
    font-size: $font-size-body;
    color: $text-primary;
  }

  &__path {
    font-size: 20rpx;
    color: $text-tertiary;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: $space-xs;
  }

  &__owner {
    font-size: $font-size-caption;
    color: $text-secondary;
  }

  &__auth {
    padding: 2rpx $space-xs;
    font-size: 20rpx;
    color: $color-warning;
    background-color: $color-warning-light;
    border-radius: $radius-pill;
  }
}

/* ---------- 页脚 ---------- */
.home-footer {
  padding: $space-md 0 $space-xl;
  text-align: center;
}
</style>
