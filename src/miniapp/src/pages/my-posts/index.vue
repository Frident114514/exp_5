<script setup lang="ts">
/**
 * 我的动态页 · 维护人：M06（M06-S2 实现）
 * 依赖：GET /api/v1/users/me/posts、PATCH/DELETE /posts/{postId}（M02）。需要登录。
 */
import PlaceholderPage from '@/components/PlaceholderPage.vue'
</script>

<template>
  <PlaceholderPage
    owner="M06"
    title="我的动态"
    description="本人动态分页列表，可进入编辑或确认后删除。需要登录。"
    :todos="[
      '分页加载，删除前二次确认',
      '删除或编辑成功后刷新列表',
      '未登录进入时先跳登录页并带 redirect',
    ]"
  />
</template>
