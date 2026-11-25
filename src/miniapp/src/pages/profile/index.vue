<script setup lang="ts">
/**
 * 个人资料页 · 维护人：M05（M05-S2 实现）
 * 依赖：GET /api/v1/users/me（M01）。需要登录。
 */
import PlaceholderPage from '@/components/PlaceholderPage.vue'
</script>

<template>
  <PlaceholderPage
    owner="M05"
    title="个人资料"
    description="展示本人账号、昵称与角色，提供修改密码和退出登录入口。依赖 GET /users/me。"
    :todos="[
      '未登录进入时先跳登录页并带 redirect',
      '只展示账号、昵称、角色，不展示密码哈希等敏感字段',
      '退出登录清理本地令牌与用户信息',
    ]"
  />
</template>
