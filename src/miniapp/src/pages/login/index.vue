<script setup lang="ts">
/**
 * 登录页 · 维护人：M05（M05-S2 实现）
 * 依赖：POST /api/v1/auth/login（M01），成功后写入登录态并跳回 redirect。
 */
import PlaceholderPage from '@/components/PlaceholderPage.vue'
</script>

<template>
  <PlaceholderPage
    owner="M05"
    title="登录"
    description="账号密码登录，成功后写入令牌并返回来源页面。依赖 POST /auth/login。"
    :todos="[
      '账号、密码必填校验（utils/validate.ts）',
      '失败提示直接使用服务端 message，按钮进入 loading 防重复提交',
      '登录成功后按 redirect 参数返回原页面，无 redirect 时回到广场',
    ]"
  />
</template>
