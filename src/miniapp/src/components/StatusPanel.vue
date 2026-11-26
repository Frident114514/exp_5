<template>
  <view :class="['status-panel', `status-panel--${tone}`]">
    <view class="status-panel__mark">{{ mark }}</view>
    <view class="status-panel__content">
      <text class="status-panel__title">{{ title }}</text>
      <text class="status-panel__message">{{ message }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  tone: 'success' | 'error' | 'info'
  title: string
  message: string
}>()

const mark = computed(() => {
  if (props.tone === 'success') return '✓'
  if (props.tone === 'error') return '!'
  return 'i'
})
</script>

<style scoped>
.status-panel {
  display: flex;
  gap: 22rpx;
  padding: 26rpx 28rpx;
  border: 1rpx solid transparent;
  border-radius: 22rpx;
}

.status-panel--success {
  color: #075d42;
  border-color: #bcebd8;
  background: #e9f8f1;
}

.status-panel--error {
  color: #a42630;
  border-color: #f4c9cd;
  background: #fff0f1;
}

.status-panel--info {
  color: #344054;
  border-color: #dfe3ea;
  background: #f8f9fc;
}

.status-panel__mark {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 42rpx;
  height: 42rpx;
  border: 2rpx solid currentColor;
  border-radius: 50%;
  font-size: 24rpx;
  font-weight: 800;
}

.status-panel__content {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.status-panel__title {
  font-size: 27rpx;
  font-weight: 750;
}

.status-panel__message {
  font-size: 24rpx;
  line-height: 1.6;
  opacity: 0.82;
}
</style>