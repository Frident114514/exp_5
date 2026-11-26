<template>
  <view class="post-card">
    <view class="post-card__meta">
      <view class="post-card__identity">
        <text class="post-card__id">#{{ post.id }}</text>
        <text class="post-card__author">{{ post.author.nickname }}</text>
        <text class="post-card__author-id">作者 ID {{ post.author.id }}</text>
      </view>
      <text class="post-card__time">{{ formatDateTime(post.createdAt) }}</text>
    </view>

    <text class="post-card__text">{{ post.text }}</text>

    <view class="post-card__footer">
      <view class="post-card__labels">
        <text class="post-card__label">纯文本</text>
        <text v-if="post.updatedAt !== post.createdAt" class="post-card__label post-card__label--edited">
          已编辑
        </text>
      </view>
      <view class="post-card__actions">
        <button class="post-card__button post-card__button--ghost" @tap="emit('edit', post)">
          编辑
        </button>
        <button
          class="post-card__button post-card__button--danger"
          :disabled="deleting"
          @tap="emit('delete', post)"
        >
          {{ deleting ? '删除中…' : '删除' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { PostView } from '@/api/types'
import { formatDateTime } from '@/utils/format'

defineProps<{
  post: PostView
  deleting: boolean
}>()

const emit = defineEmits<{
  edit: [post: PostView]
  delete: [post: PostView]
}>()
</script>

<style scoped>
.post-card {
  padding: 30rpx;
  border: 1rpx solid #e4e8ef;
  border-radius: 26rpx;
  background: #ffffff;
  box-shadow: 0 12rpx 36rpx rgba(27, 39, 71, 0.055);
}

.post-card__meta {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 22rpx;
  margin-bottom: 24rpx;
}

.post-card__identity {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12rpx;
}

.post-card__id {
  padding: 6rpx 12rpx;
  color: #2748ad;
  border-radius: 10rpx;
  background: #edf1ff;
  font-size: 22rpx;
  font-weight: 800;
}

.post-card__author {
  color: #1d2939;
  font-size: 27rpx;
  font-weight: 750;
}

.post-card__author-id,
.post-card__time {
  color: #7a8496;
  font-size: 22rpx;
}

.post-card__time {
  flex: 0 0 auto;
}

.post-card__text {
  display: block;
  color: #20293a;
  font-size: 29rpx;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.post-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-top: 30rpx;
  padding-top: 22rpx;
  border-top: 1rpx solid #edf0f5;
}

.post-card__labels {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.post-card__label {
  padding: 7rpx 13rpx;
  color: #5b6475;
  border-radius: 9rpx;
  background: #f3f5f8;
  font-size: 21rpx;
}

.post-card__label--edited {
  color: #8b5d12;
  background: #fff6df;
}

.post-card__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 14rpx;
}

.post-card__button {
  min-width: 104rpx;
  margin: 0;
  padding: 13rpx 24rpx;
  border-radius: 13rpx;
  font-size: 24rpx;
  font-weight: 650;
  line-height: 1.2;
}

.post-card__button--ghost {
  color: #2748ad;
  background: #edf1ff;
}

.post-card__button--danger {
  color: #b52a36;
  background: #fff0f1;
}

.post-card__button[disabled] {
  opacity: 0.55;
}
</style>