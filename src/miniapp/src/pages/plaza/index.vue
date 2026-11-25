<script setup lang="ts">
/**
 * 动态广场页 · 维护人：M07（M07-S2 实现）
 * 依赖：GET /api/v1/posts（M02，公开），媒体地址由 M03 的 /media/{id}/content 提供。
 */
import PlaceholderPage from '@/components/PlaceholderPage.vue'
</script>

<template>
  <PlaceholderPage
    owner="M07"
    title="动态广场"
    description="公开动态列表，支持日期与单标签筛选、下拉刷新和分页。未登录可浏览。"
    :todos="[
      '筛选参数：from / to 为 YYYY-MM-DD，tag 单个标签，切换筛选时重置 page',
      '列表状态使用 StateView，加载失败提供重试',
      '卡片点击进入详情（必带 postId），未登录互动入口引导登录',
    ]"
  />
</template>
