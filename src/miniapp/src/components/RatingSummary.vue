<template>
  <view class="rating" :class="{ 'rating--empty': !text.available }">
    <text v-if="text.available" class="rating__stars">{{ text.stars }}</text>
    <text class="rating__average">{{ text.averageText }}</text>
    <text class="rating__count">{{ text.countText }}</text>
    <text v-if="showHint && !text.available" class="rating__hint">还没有人评分</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RatingSummary } from '@/api/types.ts'
import { formatRating } from '@/utils/format.ts'

const props = withDefaults(
  defineProps<{
    rating?: RatingSummary | null
    showHint?: boolean
  }>(),
  { rating: null, showHint: false },
)

const text = computed(() => formatRating(props.rating))
</script>

<style scoped>
.rating {
  display: flex;
  align-items: baseline;
  gap: 10rpx;
}

.rating__stars {
  font-size: 26rpx;
  color: #f5a623;
  letter-spacing: 2rpx;
}

.rating__average {
  font-size: 30rpx;
  font-weight: 600;
  color: #f5a623;
}

.rating--empty .rating__average {
  font-size: 26rpx;
  font-weight: 400;
  color: #9aa2b1;
}

.rating__count {
  font-size: 22rpx;
  color: #9aa2b1;
}

.rating__hint {
  font-size: 22rpx;
  color: #b6bcc8;
}
</style>
