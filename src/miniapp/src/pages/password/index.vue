<script setup lang="ts">
/**
 * 修改密码页 · 维护人：M05（M05-S2）
 *
 * 依赖：PUT /users/me/password（M01）；需要登录。
 * 约定：改密成功后服务端使旧令牌失效，因此本地立即清理登录态并回到登录页，
 * 必须用新密码重新登录（对应 M05-C03）。
 */
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppButton from '@/components/AppButton.vue'
import FormField from '@/components/FormField.vue'
import { changePassword } from '@/api/auth'
import { isApiError } from '@/api/http'
import { ROUTES } from '@/config/routes'
import { authStore } from '@/stores/auth'
import { toastSuccess } from '@/utils/feedback'
import { requireLogin } from '@/utils/navigate'
import {
  validateNewPassword,
  validatePasswordConfirm,
  validateRequired,
} from '@/utils/validate'

type FieldKey = 'oldPassword' | 'newPassword' | 'confirm'

const form = reactive<Record<FieldKey, string>>({
  oldPassword: '',
  newPassword: '',
  confirm: '',
})
const errors = reactive<Record<FieldKey, string>>({
  oldPassword: '',
  newPassword: '',
  confirm: '',
})
const submitError = ref('')
const submitting = ref(false)

const fieldKeys: FieldKey[] = ['oldPassword', 'newPassword', 'confirm']

// 未登录直接引导登录，登录后回到本页。
onLoad(() => {
  if (!requireLogin()) {
    return
  }
})

const validators: Record<FieldKey, () => string> = {
  oldPassword: () => validateRequired(form.oldPassword, '原密码') ?? '',
  newPassword: () => validateNewPassword(form.newPassword, form.oldPassword) ?? '',
  confirm: () => validatePasswordConfirm(form.confirm, form.newPassword) ?? '',
}

function handleBlur(key: FieldKey): void {
  // 改新密码或原密码后，确认密码的校验结果可能已失效，一并刷新。
  errors[key] = validators[key]()
  if (key !== 'confirm' && form.confirm.length > 0) {
    errors.confirm = validators.confirm()
  }
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
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    authStore.clear()
    toastSuccess('密码已修改，请重新登录')
    setTimeout(() => {
      uni.reLaunch({ url: ROUTES.login.path })
    }, 800)
  } catch (error) {
    if (!authStore.isLoggedIn) {
      // 401：令牌已失效，请求层已清理并跳转，不再重复提示。
      return
    }
    const message = isApiError(error) ? error.message : '修改密码失败，请稍后重试'
    const isOldPasswordWrong = Boolean(isApiError(error) && error.code === 'VALIDATION_ERROR' && message.includes('原密码'))
    if (isOldPasswordWrong) {
      errors.oldPassword = message
    } else {
      submitError.value = message
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="section">
      <text class="section__title">修改密码</text>
      <text class="section__desc">
        新密码需 8–32 位且同时包含字母和数字，不能与原密码相同。修改成功后需要用新密码重新登录。
      </text>

      <view class="stack form">
        <FormField
          v-model="form.oldPassword"
          label="原密码"
          type="password"
          placeholder="请输入当前密码"
          required
          :maxlength="32"
          :error="errors.oldPassword"
          @update:model-value="clearError('oldPassword')"
          @blur="handleBlur('oldPassword')"
        />
        <FormField
          v-model="form.newPassword"
          label="新密码"
          type="password"
          placeholder="8–32 位，需含字母和数字"
          required
          :maxlength="32"
          :error="errors.newPassword"
          @update:model-value="clearError('newPassword')"
          @blur="handleBlur('newPassword')"
        />
        <FormField
          v-model="form.confirm"
          label="确认新密码"
          type="password"
          placeholder="再次输入新密码"
          required
          :maxlength="32"
          :error="errors.confirm"
          @update:model-value="clearError('confirm')"
          @blur="handleBlur('confirm')"
        />

        <text v-if="submitError" class="form-error">{{ submitError }}</text>

        <AppButton type="primary" size="large" :loading="submitting" @click="submit">
          {{ submitting ? '提交中…' : '确认修改' }}
        </AppButton>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.form {
  margin-top: $space-md;
}
</style>
