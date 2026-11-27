<script setup lang="ts">
/**
 * 登录页 · 维护人：M05（M05-S2）
 *
 * 依赖：POST /auth/login（M01）。成功后写入登录态，并回到 `redirect` 指定的原页面。
 * 失败提示直接使用服务端 message；提交期间按钮进入加载态，避免重复提交。
 */
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppButton from '@/components/AppButton.vue'
import FormField from '@/components/FormField.vue'
import { login } from '@/api/auth'
import { isApiError } from '@/api/http'
import { ROUTES } from '@/config/routes'
import { authStore } from '@/stores/auth'
import { toastSuccess } from '@/utils/feedback'
import { decodeRouteParam, navigateAfterLogin, openRoute } from '@/utils/navigate'
import { validateAccount, validateLoginPassword } from '@/utils/validate'

type FieldKey = 'account' | 'password'

const form = reactive<Record<FieldKey, string>>({ account: '', password: '' })
const errors = reactive<Record<FieldKey, string>>({ account: '', password: '' })
const submitError = ref('')
const submitting = ref(false)
const redirect = ref('')

onLoad((options) => {
  const raw = options && typeof options.redirect === 'string' ? options.redirect : ''
  redirect.value = decodeRouteParam(raw)
})

function validateField(key: FieldKey): string {
  return (key === 'account' ? validateAccount(form.account) : validateLoginPassword(form.password)) ?? ''
}

function handleBlur(key: FieldKey): void {
  errors[key] = validateField(key)
}

function clearError(key: FieldKey): void {
  errors[key] = ''
  submitError.value = ''
}

function validateAll(): boolean {
  errors.account = validateField('account')
  errors.password = validateField('password')
  return errors.account.length === 0 && errors.password.length === 0
}

async function submit(): Promise<void> {
  submitError.value = ''
  if (!validateAll()) {
    return
  }
  submitting.value = true
  try {
    const result = await login({ account: form.account.trim(), password: form.password })
    authStore.setSession(result)
    toastSuccess('登录成功')
    navigateAfterLogin(redirect.value)
  } catch (error) {
    submitError.value = isApiError(error) ? error.message : '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function goRegister(): void {
  openRoute('register', { redirect: redirect.value || undefined })
}

function browseWithoutLogin(): void {
  uni.reLaunch({ url: ROUTES.square.path })
}
</script>

<template>
  <view class="page">
    <view class="section">
      <text class="section__title">账号登录</text>
      <text class="section__desc">登录后可发布动态、评分和评论；不登录也可以浏览广场与详情。</text>

      <view class="stack form">
        <FormField
          v-model="form.account"
          label="账号"
          placeholder="4–20 位字母、数字或下划线"
          required
          :maxlength="20"
          :error="errors.account"
          @update:model-value="clearError('account')"
          @blur="handleBlur('account')"
          @confirm="submit"
        />
        <FormField
          v-model="form.password"
          label="密码"
          type="password"
          placeholder="请输入密码"
          required
          :maxlength="32"
          :error="errors.password"
          @update:model-value="clearError('password')"
          @blur="handleBlur('password')"
          @confirm="submit"
        />

        <text v-if="submitError" class="form-error">{{ submitError }}</text>

        <AppButton type="primary" size="large" :loading="submitting" @click="submit">
          {{ submitting ? '登录中…' : '登录' }}
        </AppButton>

        <view class="links">
          <text class="links__item" @tap="goRegister">还没有账号？去注册</text>
          <text class="links__item" @tap="browseWithoutLogin">先随便看看</text>
        </view>
      </view>
    </view>

    <view class="section">
      <text class="section__title">接口说明</text>
      <text class="section__desc">
        登录调用 POST /api/v1/auth/login，返回 accessToken、expiresAt 与用户资料；
        令牌有效期 12 小时，过期后受保护操作会引导重新登录。
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.form {
  margin-top: $space-md;
}

.links {
  display: flex;
  justify-content: space-between;
  margin-top: $space-xs;

  &__item {
    font-size: $font-size-body;
    color: $brand-primary;
  }
}
</style>
