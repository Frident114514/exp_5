<script setup lang="ts">
/**
 * 动态详情页 · 维护人：M07（M07-S2 实现）
 * 依赖：GET /api/v1/posts/{postId}、评分与评论接口（M02/M04），媒体地址（M03）。
 * 路由参数：postId（必填）。
 */
import PlaceholderPage from '@/components/PlaceholderPage.vue'
</script>

<template>
  <PlaceholderPage
    owner="M07"
    title="动态详情"
    description="展示文本、图片、MP4 视频、标签与评分摘要，提供评分和评论入口。未登录可浏览。"
    :todos="[
      '从 onLoad 读取 postId，缺失或非法时显示错误状态',
      '已删除动态（404）显示明确状态而不是空白页',
      '作者或管理员不显示评分评论按钮，与服务端权限一致',
    ]"
  />
</template>
