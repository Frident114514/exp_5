<script setup lang="ts">
/**
 * 发布 / 编辑动态页 · 维护人：M06（M06-S2 实现）
 * 依赖：POST /posts、PATCH /posts/{postId}（M02），POST /media 上传（M03）。
 * 路由参数：postId（可选，有值表示编辑本人动态）。
 */
import PlaceholderPage from '@/components/PlaceholderPage.vue'
</script>

<template>
  <PlaceholderPage
    owner="M06"
    title="发布动态"
    description="输入文本、选择图片或 MP4 视频、填写标签并提交。需要登录。"
    :todos="[
      '带 postId 进入时为编辑：回填原内容并保留原媒体 ID',
      '媒体未全部上传成功前阻止发布，上传失败可重试',
      '文本与媒体至少一项；图 1–9 张或 1 个视频；标签最多 5 个',
    ]"
  />
</template>
