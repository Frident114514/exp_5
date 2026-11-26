<template>
  <view class="media" :class="'media--' + variant">
    <view class="media__grid" :class="gridClass">
      <view v-for="(item, index) in visible" :key="item.id" class="media__tile">
        <text class="media__kind">{{ isVideoType(item.type) ? '视频' : '图片' }}</text>
        <text v-if="isVideoType(item.type)" class="media__play">▶</text>
        <text class="media__index">{{ index + 1 }}/{{ total }}</text>
      </view>
      <view v-if="extraCount > 0" class="media__tile media__tile--more">
        <text class="media__more">+{{ extraCount }}</text>
      </view>
    </view>
    <text v-if="variant === 'detail'" class="media__hint">媒体预览与播放将在后续阶段开放</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MediaItem } from '@/api/types.ts'
import { isVideoType } from '@/utils/post.ts'

const props = withDefaults(
  defineProps<{
    media: MediaItem[]
    /** card：列表缩略图占位；detail：详情大图占位 */
    variant?: 'card' | 'detail'
  }>(),
  { variant: 'card' },
)

const limit = computed(() => (props.variant === 'detail' ? 9 : 3))
const total = computed(() => props.media.length)

/** 超出上限时让最后一个格子显示 +N，保证每行铺满、不出现残缺行。 */
const visible = computed(() => {
  const hasExtra = total.value > limit.value
  const count = hasExtra ? Math.max(0, limit.value - 1) : Math.min(total.value, limit.value)
  return props.media.slice(0, count)
})

const extraCount = computed(() => Math.max(0, total.value - visible.value.length))
const isSingleVideo = computed(
  () => total.value === 1 && visible.value.length === 1 && isVideoType(visible.value[0].type),
)

const gridClass = computed(() => {
  const count = visible.value.length + (extraCount.value > 0 ? 1 : 0)
  if (isSingleVideo.value) return 'is-video'
  if (count <= 1) return 'is-single'
  if (count === 2) return 'is-double'
  return 'is-triple'
})
</script>

<style scoped>
.media {
  margin-top: 16rpx;
}

.media__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.media__tile {
  position: relative;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #eef0f4;
  border: 1rpx solid #e2e6ee;
  border-radius: 12rpx;
  overflow: hidden;
}

.media__grid.is-single .media__tile,
.media__grid.is-video .media__tile {
  width: 100%;
  height: 300rpx;
}

.media__grid.is-double .media__tile {
  width: calc(50% - 4rpx);
  height: 220rpx;
}

.media__grid.is-triple .media__tile {
  width: calc(33.33% - 6rpx);
  height: 180rpx;
}

.media--detail .media__grid.is-single .media__tile,
.media--detail .media__grid.is-video .media__tile {
  height: 420rpx;
}

.media--detail .media__grid.is-double .media__tile {
  height: 260rpx;
}

.media--detail .media__grid.is-triple .media__tile {
  height: 220rpx;
}

.media__kind {
  font-size: 24rpx;
  color: #8a93a5;
}

.media__play {
  position: absolute;
  width: 84rpx;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  border-radius: 50%;
  background-color: rgba(23, 30, 45, 0.55);
  color: #ffffff;
  font-size: 34rpx;
}

.media__index {
  position: absolute;
  right: 10rpx;
  bottom: 8rpx;
  font-size: 20rpx;
  color: #6f7789;
}

.media__tile--more {
  background-color: #e6e9f0;
}

.media__more {
  font-size: 30rpx;
  color: #59616f;
}

.media__hint {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #a0a7b6;
}
</style>