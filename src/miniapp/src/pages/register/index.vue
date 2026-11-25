<script setup lang="ts">
/**
 * 注册页 · 维护人：M05（M05-S2 实现）
 * 依赖：POST /api/v1/auth/register（M01），注册成功后跳转登录。
 */
import PlaceholderPage from '@/components/PlaceholderPage.vue'
</script>

<template>
  <PlaceholderPage
    owner="M05"
    title="注册"
    description="填写账号、密码与昵称完成注册，角色由服务端固定为 USER。依赖 POST /auth/register。"
    :todos="[
      '账号、密码强度、确认密码、昵称四项校验',
      '重复账号返回 409 时提示「账号已被注册」',
      '注册成功跳转登录页并保留 redirect',
    ]"
  />
</template>
