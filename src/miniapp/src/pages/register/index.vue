<script setup lang="ts">
/**
 * 注册页 · 维护人：M05（M05-S2）
 *
 * 依赖：POST /auth/register（M01），注册成功后跳转登录页并保留 redirect。
 * 字段：账号、密码、确认密码、昵称；服务端把角色固定为 USER。
 */
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppButton from '@/components/AppButton.vue'
import FormField from '@/components/FormField.vue'
import { register } from '@/api/auth'
import { isApiError } from '@/api/http'
import { toastSuccess } from '@/utils/feedback'
import { decodeRouteParam, openRoute } from '@/utils/navigate'
import {
  validateAccount,
  validateNickname,
  validatePasswordConfirm,
  validatePasswordStrength,
} from '@/utils/validate'

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
const submitError = ref('')
const submitting = ref(false)
const redirect = ref('')

const fieldKeys: FieldKey[] = ['account', 'password', 'confirm', 'nickname']

onLoad((options) => {
  const raw = options && typeof options.redirect === 'string' ? options.redirect : ''
  redirect.value = decodeRouteParam(raw)
})

const validators: Record<FieldKey, () => string> = {
  account: () => validateAccount(form.account) ?? '',
  password: () => validatePasswordStrength(form.password) ?? '',
  confirm: () => validatePasswordConfirm(form.confirm, form.password) ?? '',
  nickname: () => validateNickname(form.nickname) ?? '',
}

function handleBlur(key: FieldKey): void {
  errors[key] = validators[key]()
}

function clearError(key: FieldKey): void {
  errors[key] = ''
  submitError.value = ''
}

function validateAll(): boolean {
  let valid = true
  for (const key of fieldKeys) {
    errors[key] = validators[key]()
    if (errors[key]) {
      valid = false
    }
  }
  return valid
}

async function submit(): Promise<void> {
  submitError.value = ''
  if (!validateAll()) {
    return
  }
  submitting.value = true
  try {
    await register({
      account: form.account.trim(),
      password: form.password,
      nickname: form.nickname.trim(),
    })
    toastSuccess('注册成功，请登录')
    // 注册接口只返回用户资料，不返回令牌，因此回到登录页并保留 redirect。
    setTimeout(() => {
      openRoute('login', { redirect: redirect.value || undefined }, { replace: true })
    }, 600)
  } catch (error) {
    submitError.value = isApiError(error) ? error.message : '注册失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function goLogin(): void {
  openRoute('login', { redirect: redirect.value || undefined }, { replace: true })
}
</script>

<template>
  <view class="page">
    <view class="section">
      <text class="section__title">注册账号</text>
      <text class="section__desc">账号注册后角色固定为普通用户；注册成功需要再用新账号登录。</text>

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
          @blur="handleBlur('password')"
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
          @blur="handleBlur('confirm')"
        />
        <FormField
          v-model="form.nickname"
          label="昵称"
          placeholder="1–20 个字符，会显示在动态上"
          required
          :maxlength="20"
          :error="errors.nickname"
          @update:model-value="clearError('nickname')"
          @blur="handleBlur('nickname')"
        />

        <text v-if="submitError" class="form-error">{{ submitError }}</text>

        <AppButton type="primary" size="large" :loading="submitting" @click="submit">
          {{ submitting ? '注册中…' : '注册' }}
        </AppButton>

        <view class="links">
          <text class="links__item" @tap="goLogin">已有账号？去登录</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.form {
  margin-top: $space-md;
}

.links {
  margin-top: $space-xs;

  &__item {
    font-size: $font-size-body;
    color: $brand-primary;
  }
}
</style>
