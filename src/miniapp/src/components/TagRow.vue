<template>
  <view v-if="list.length" class="tags">
    <text v-for="tag in list" :key="tag" class="tags__item"># {{ tag }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { normalizeTags } from '@/utils/post.ts'

const props = withDefaults(
  defineProps<{
    tags: string[]
    limit?: number
  }>(),
  { limit: 5 },
)

const list = computed(() => normalizeTags(props.tags, props.limit))
</script>

<style scoped>
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.tags__item {
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background-color: #eef3ff;
  color: #3a6ff2;
  font-size: 22rpx;
}
</style>
