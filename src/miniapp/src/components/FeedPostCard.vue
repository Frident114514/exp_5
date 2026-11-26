<template>
  <view class="card" hover-class="card--active" :hover-stay-time="80" @tap="onTap">
    <view class="card__head">
      <view class="card__avatar">{{ avatarText }}</view>
      <view class="card__head-main">
        <text class="card__author">{{ authorName }}</text>
        <text class="card__time">{{ timeText }}</text>
      </view>
      <text class="card__rating">{{ ratingText }}</text>
    </view>

    <text v-if="excerpt" class="card__text">{{ excerpt }}</text>
    <text v-else class="card__text card__text--muted">（无正文，仅媒体内容）</text>

    <MediaPlaceholder v-if="hasMedia" :media="post.media" variant="card" />
    <TagRow v-if="post.tags && post.tags.length" :tags="post.tags" />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PostSummary } from '@/api/types.ts'
import MediaPlaceholder from '@/components/MediaPlaceholder.vue'
import TagRow from '@/components/TagRow.vue'
import { formatRelativeTime, ratingBadge } from '@/utils/format.ts'
import { postExcerpt } from '@/utils/post.ts'

const props = defineProps<{ post: PostSummary }>()

const emit = defineEmits<{ (event: 'select', id: number): void }>()

const authorName = computed(() => props.post.author?.nickname || '匿名用户')
const avatarText = computed(() => Array.from(authorName.value)[0] || '?')
const timeText = computed(() => formatRelativeTime(props.post.createdAt) || '')
const excerpt = computed(() => postExcerpt(props.post.text, 70))
const ratingText = computed(() => ratingBadge(props.post.rating))
const hasMedia = computed(() => Array.isArray(props.post.media) && props.post.media.length > 0)

function onTap(): void {
  emit('select', props.post.id)
}
</script>

<style scoped>
.card {
  padding: 28rpx;
  background-color: #ffffff;
  border-radius: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(23, 30, 45, 0.04);
}

.card--active {
  background-color: #fafbfd;
}

.card__head {
  display: flex;
  align-items: center;
}

.card__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background-color: #e7eeff;
  color: #3a6ff2;
  font-size: 30rpx;
  font-weight: 600;
}

.card__head-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 18rpx;
  overflow: hidden;
}

.card__author {
  font-size: 28rpx;
  font-weight: 600;
  color: #23293a;
}

.card__time {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #9aa2b1;
}

.card__rating {
  flex-shrink: 0;
  font-size: 22rpx;
  color: #f5a623;
}

.card__text {
  display: block;
  margin-top: 20rpx;
  font-size: 28rpx;
  line-height: 1.65;
  color: #39404f;
  word-break: break-all;
}

.card__text--muted {
  color: #a0a7b6;
  font-style: italic;
}
</style>
