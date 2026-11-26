<template>
  <view class="hero">
    <view class="hero__topline">
      <text class="hero__eyebrow">{{ eyebrow }}</text>
      <view class="hero__mode">
        <view class="hero__dot" />
        <text>Mock 契约演示</text>
      </view>
    </view>
    <text class="hero__title">{{ title }}</text>
    <text class="hero__description">{{ description }}</text>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  eyebrow: string
  title: string
  description: string
}>()
</script>

<style scoped>
.hero {
  padding: 40rpx 36rpx;
  border-radius: 28rpx;
  color: #ffffff;
  background:
    radial-gradient(circle at 100% 0%, rgba(255, 255, 255, 0.24), transparent 38%),
    linear-gradient(135deg, #173365 0%, #274fb4 58%, #4b78ff 100%);
  box-shadow: 0 22rpx 60rpx rgba(31, 65, 155, 0.22);
}

.hero__topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.hero__eyebrow {
  font-size: 24rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
  opacity: 0.82;
}

.hero__mode {
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 8rpx 16rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.28);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.1);
  font-size: 22rpx;
}

.hero__dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #a7f3d0;
}

.hero__title {
  display: block;
  margin-bottom: 14rpx;
  font-size: 48rpx;
  font-weight: 800;
  line-height: 1.2;
}

.hero__description {
  display: block;
  font-size: 26rpx;
  line-height: 1.7;
  opacity: 0.84;
}
</style>