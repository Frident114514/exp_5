<template>
  <view class="page">
    <view class="hero">
      <text class="hero__title">动态广场</text>
      <text class="hero__subtitle">公开浏览 · 按发布时间倒序</text>
      <text v-if="status === 'ready' && items.length" class="hero__count">
        共 {{ total }} 条动态
      </text>
    </view>

    <!-- 首次加载：骨架屏，避免白屏 -->
    <view v-if="isFirstLoading" class="skeletons">
      <view v-for="n in 3" :key="n" class="skeleton">
        <view class="skeleton__head">
          <view class="skeleton__avatar"></view>
          <view class="skeleton__lines">
            <view class="skeleton__bar skeleton__bar--short"></view>
            <view class="skeleton__bar skeleton__bar--tiny"></view>
          </view>
        </view>
        <view class="skeleton__bar skeleton__bar--full"></view>
        <view class="skeleton__bar skeleton__bar--medium"></view>
        <view class="skeleton__media"></view>
      </view>
      <text class="skeletons__hint">正在加载动态…</text>
    </view>

    <!-- 接口失败 -->
    <StatePanel
      v-else-if="status === 'error'"
      variant="error"
      :description="errorMessage"
      action-text="重新加载"
      @action="onRetry"
    />

    <!-- 空结果 -->
    <StatePanel
      v-else-if="isEmpty"
      variant="empty"
      action-text="刷新试试"
      @action="onRetry"
    />

    <!-- 列表 -->
    <view v-else class="list">
      <PostCard
        v-for="post in items"
        :key="post.id"
        :post="post"
        @select="openDetail"
      />

      <view class="list__footer">
        <text v-if="loadingMore" class="list__footer-text">正在加载更多…</text>
        <text v-else-if="loadMoreError" class="list__footer-retry" @tap="onLoadMore">
          {{ loadMoreError }}，点击重试
        </text>
        <text v-else-if="!hasMore" class="list__footer-text">没有更多了</text>
        <text v-else class="list__footer-text">上拉加载更多</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import PostCard from '@/components/PostCard.vue'
import StatePanel from '@/components/StatePanel.vue'
import { usePostList } from '@/composables/usePostList.ts'

const {
  status,
  items,
  total,
  hasMore,
  isEmpty,
  isFirstLoading,
  errorMessage,
  loadingMore,
  loadMoreError,
  loadFirstPage,
  loadNextPage,
} = usePostList()

onLoad(() => {
  void loadFirstPage()
})

onPullDownRefresh(async () => {
  await loadFirstPage()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  void loadNextPage()
})

function onRetry(): void {
  void loadFirstPage()
}

function onLoadMore(): void {
  void loadNextPage()
}

function openDetail(id: number): void {
  uni.navigateTo({
    url: '/pages/detail/index?id=' + id,
  })
}
</script>

<style scoped>
.hero {
  display: flex;
  flex-direction: column;
  padding: 8rpx 8rpx 24rpx;
}

.hero__title {
  font-size: 44rpx;
  font-weight: 700;
  color: #1b2130;
}

.hero__subtitle {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #8a93a5;
}

.hero__count {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #3a6ff2;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.list__footer {
  display: flex;
  justify-content: center;
  padding: 24rpx 0 8rpx;
}

.list__footer-text {
  font-size: 22rpx;
  color: #a0a7b6;
}

.list__footer-retry {
  font-size: 22rpx;
  color: #e5484d;
}

.skeletons {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.skeletons__hint {
  padding: 8rpx 0;
  text-align: center;
  font-size: 22rpx;
  color: #b6bcc8;
}

.skeleton {
  padding: 28rpx;
  background-color: #ffffff;
  border-radius: 20rpx;
}

.skeleton__head {
  display: flex;
  align-items: center;
}

.skeleton__avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background-color: #eef0f4;
}

.skeleton__lines {
  flex: 1;
  margin-left: 18rpx;
}

.skeleton__bar {
  height: 22rpx;
  border-radius: 8rpx;
  background-color: #eef0f4;
}

.skeleton__bar--short {
  width: 30%;
}

.skeleton__bar--tiny {
  width: 20%;
  margin-top: 12rpx;
  height: 16rpx;
}

.skeleton__bar--full {
  width: 100%;
  margin-top: 26rpx;
}

.skeleton__bar--medium {
  width: 62%;
  margin-top: 14rpx;
}

.skeleton__media {
  margin-top: 22rpx;
  height: 220rpx;
  border-radius: 12rpx;
  background-color: #f2f4f8;
}
</style>