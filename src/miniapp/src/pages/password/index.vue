<script setup lang="ts">
/**
 * 修改密码页 · 维护人：M05（M05-S2 实现）
 * 依赖：PUT /api/v1/users/me/password（M01）。改密成功后旧令牌失效，需要重新登录。
 */
import PlaceholderPage from '@/components/PlaceholderPage.vue'
</script>

<template>
  <PlaceholderPage
    owner="M05"
    title="修改密码"
    description="校验原密码与新密码强度，成功后清理登录态并跳转登录页。依赖 PUT /users/me/password。"
    :todos="[
      '原密码错误时显示服务端提示，保留已输入的账号',
      '新密码需 8–32 位且含字母和数字，不得与原密码相同',
      '改密成功后清除本地令牌，按约定要求重新登录',
    ]"
  />
</template>
