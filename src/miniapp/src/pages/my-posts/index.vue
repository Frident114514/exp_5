<template>
  <view class="page-shell">
    <AppHeader
      eyebrow="M06 · 我的动态"
      title="作者工作台"
      description="查看本人发布的动态，核对动态 ID、正文和作者字段，并进入编辑或删除流程。"
    />

    <view class="page-content">
      <view class="summary-row">
        <view class="summary-card">
          <text class="summary-card__label">当前作者</text>
          <text class="summary-card__value">{{ currentUser.nickname }}</text>
          <text class="summary-card__hint">作者 ID {{ currentUser.id }}</text>
        </view>
        <view class="summary-card summary-card--accent">
          <text class="summary-card__label">本人动态</text>
          <text class="summary-card__value">{{ total }} 条</text>
          <text class="summary-card__hint">GET /users/me/posts</text>
        </view>
      </view>

      <button class="publish-button" @tap="openPublish">
        <text class="publish-button__plus">＋</text>
        <text>发布新动态</text>
      </button>

      <view class="list-heading">
        <view>
          <text class="list-heading__title">动态列表</text>
          <text class="list-heading__hint">按发布时间倒序，pageSize = {{ PAGE_SIZE }}</text>
        </view>
        <button class="refresh-button" :disabled="loading" @tap="loadPosts(page)">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </view>

      <view v-if="loading" class="skeleton-list">
        <view v-for="index in 2" :key="index" class="skeleton-card">
          <view class="skeleton-card__line skeleton-card__line--short" />
          <view class="skeleton-card__line" />
          <view class="skeleton-card__line skeleton-card__line--medium" />
        </view>
      </view>

      <StatusPanel
        v-else-if="errorMessage"
        tone="error"
        title="列表加载失败"
        :message="errorMessage"
      />

      <view v-if="!loading && errorMessage" class="error-actions">
        <button class="secondary-button" @tap="loadPosts(page)">重新加载</button>
      </view>

      <view v-else-if="!loading && posts.length === 0" class="empty-state">
        <view class="empty-state__mark">文</view>
        <text class="empty-state__title">还没有发布动态</text>
        <text class="empty-state__hint">发布第一条纯文本动态后，它会出现在这里。</text>
        <button class="empty-state__button" @tap="openPublish">发布第一条动态</button>
      </view>

      <view v-else-if="!loading" class="post-list">
        <PostCard
          v-for="post in posts"
          :key="post.id"
          :post="post"
          :deleting="deletingId === post.id"
          @edit="openEdit"
          @delete="confirmDelete"
        />
      </view>

      <view v-if="!loading && !errorMessage && total > 0" class="pagination">
        <button class="pagination__button" :disabled="page <= 1" @tap="goToPage(page - 1)">
          上一页
        </button>
        <view class="pagination__position">
          <text>第 {{ page }} / {{ totalPages }} 页</text>
          <text class="pagination__total">共 {{ total }} 条</text>
        </view>
        <button
          class="pagination__button"
          :disabled="page >= totalPages"
          @tap="goToPage(page + 1)"
        >
          下一页
        </button>
      </view>

      <view class="contract-footer">
        <text class="contract-footer__title">第一周边界</text>
        <text class="contract-footer__text">
          当前仅演示纯文本动态。图片、视频、上传进度和标签将在第二周继续实现。
        </text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppHeader from '@/components/AppHeader.vue'
import PostCard from '@/components/PostCard.vue'
import StatusPanel from '@/components/StatusPanel.vue'
import { getPostApi, CURRENT_USER, mockPostApi } from '@/api/runtime'
import type { PostView } from '@/api/types'
import { getErrorMessage } from '@/utils/errors'

const PAGE_SIZE = 5
const currentUser = mockPostApi.getCurrentUser()
const posts = ref<PostView[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const deletingId = ref<number | null>(null)
let requestSequence = 0

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function loadPosts(targetPage = page.value): Promise<void> {
  const sequence = ++requestSequence
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getPostApi().mine(Math.max(1, targetPage), PAGE_SIZE)
    if (sequence !== requestSequence) return

    posts.value = result.items
    page.value = result.page
    total.value = result.total
  } catch (error) {
    if (sequence !== requestSequence) return
    posts.value = []
    errorMessage.value = getErrorMessage(error)
  } finally {
    if (sequence === requestSequence) {
      loading.value = false
    }
  }
}

onShow(() => {
  void loadPosts(page.value)
})

function goToPage(targetPage: number): void {
  if (targetPage < 1 || targetPage > totalPages.value || loading.value) return
  void loadPosts(targetPage)
}

function openPublish(): void {
  uni.navigateTo({ url: '/pages/publish/index' })
}

function openEdit(post: PostView): void {
  uni.navigateTo({ url: `/pages/edit/index?id=${post.id}` })
}

function confirmDelete(post: PostView): void {
  if (deletingId.value !== null) return

  uni.showModal({
    title: '确认删除动态',
    content: `将删除动态 #${post.id}。删除后列表和详情都不再显示。`,
    confirmText: '删除',
    confirmColor: '#c4323d',
    success: async (result) => {
      if (!result.confirm) return

      deletingId.value = post.id
      try {
        await getPostApi().delete(post.id)
        uni.showToast({ title: '删除成功', icon: 'success' })
        const nextPage = posts.value.length === 1 && page.value > 1 ? page.value - 1 : page.value
        await loadPosts(nextPage)
      } catch (error) {
        uni.showToast({
          title: getErrorMessage(error),
          icon: 'none',
          duration: 2600,
        })
      } finally {
        deletingId.value = null
      }
    },
  })
}
</script>

<style scoped>
.page-shell {
  min-height: 100vh;
  padding: 28rpx 28rpx 54rpx;
  background:
    radial-gradient(circle at 100% 0%, rgba(70, 107, 255, 0.09), transparent 28%),
    #f5f7fb;
}

.page-content {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  margin-top: 24rpx;
}

.summary-row {
  display: flex;
  gap: 18rpx;
}

.summary-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 7rpx;
  padding: 24rpx;
  border: 1rpx solid #e4e8ef;
  border-radius: 21rpx;
  background: #ffffff;
}

.summary-card--accent {
  border-color: #dce4ff;
  background: #f5f7ff;
}

.summary-card__label {
  color: #7a8496;
  font-size: 21rpx;
}

.summary-card__value {
  color: #1d2939;
  font-size: 29rpx;
  font-weight: 780;
}

.summary-card__hint {
  color: #8c95a3;
  font-size: 19rpx;
}

.publish-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  width: 100%;
  margin: 0;
  padding: 24rpx 30rpx;
  color: #ffffff;
  border-radius: 19rpx;
  background: linear-gradient(135deg, #3159f4, #557cff);
  box-shadow: 0 14rpx 32rpx rgba(51, 92, 255, 0.22);
  font-size: 28rpx;
  font-weight: 750;
}

.publish-button__plus {
  font-size: 34rpx;
  font-weight: 400;
}

.list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-top: 8rpx;
}

.list-heading__title,
.list-heading__hint {
  display: block;
}

.list-heading__title {
  color: #172033;
  font-size: 31rpx;
  font-weight: 800;
}

.list-heading__hint {
  margin-top: 6rpx;
  color: #8992a2;
  font-size: 21rpx;
}

.refresh-button {
  margin: 0;
  padding: 12rpx 22rpx;
  color: #40506c;
  border-radius: 12rpx;
  background: #ffffff;
  font-size: 23rpx;
}

.refresh-button[disabled] {
  opacity: 0.55;
}

.post-list,
.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.skeleton-card {
  padding: 30rpx;
  border: 1rpx solid #e7eaf0;
  border-radius: 26rpx;
  background: #ffffff;
  animation: pulse 1.4s ease-in-out infinite;
}

.skeleton-card__line {
  height: 22rpx;
  margin-top: 18rpx;
  border-radius: 999rpx;
  background: #edf0f5;
}

.skeleton-card__line:first-child {
  margin-top: 0;
}

.skeleton-card__line--short {
  width: 34%;
}

.skeleton-card__line--medium {
  width: 68%;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.58;
  }
  50% {
    opacity: 1;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 32rpx;
  border: 1rpx dashed #cfd7e6;
  border-radius: 26rpx;
  background: #ffffff;
  text-align: center;
}

.empty-state__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 86rpx;
  height: 86rpx;
  margin-bottom: 22rpx;
  color: #3159f4;
  border-radius: 24rpx;
  background: #edf1ff;
  font-size: 36rpx;
  font-weight: 800;
}

.empty-state__title {
  color: #1d2939;
  font-size: 30rpx;
  font-weight: 780;
}

.empty-state__hint {
  margin-top: 10rpx;
  color: #7c8798;
  font-size: 23rpx;
  line-height: 1.6;
}

.empty-state__button,
.secondary-button {
  margin: 26rpx 0 0;
  padding: 18rpx 30rpx;
  color: #2748ad;
  border-radius: 15rpx;
  background: #edf1ff;
  font-size: 24rpx;
  font-weight: 700;
}

.error-actions {
  display: flex;
  justify-content: flex-end;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 20rpx 0;
}

.pagination__button {
  min-width: 138rpx;
  margin: 0;
  padding: 16rpx 22rpx;
  color: #334155;
  border: 1rpx solid #dde3ec;
  border-radius: 14rpx;
  background: #ffffff;
  font-size: 23rpx;
}

.pagination__button[disabled] {
  color: #abb3c0;
  background: #f3f5f8;
}

.pagination__position {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5rpx;
  color: #566176;
  font-size: 22rpx;
}

.pagination__total {
  color: #8b95a5;
  font-size: 19rpx;
}

.contract-footer {
  margin-top: 8rpx;
  padding: 24rpx 26rpx;
  border-radius: 20rpx;
  background: #edf1ff;
}

.contract-footer__title,
.contract-footer__text {
  display: block;
}

.contract-footer__title {
  color: #2748ad;
  font-size: 23rpx;
  font-weight: 750;
}

.contract-footer__text {
  margin-top: 8rpx;
  color: #52617f;
  font-size: 22rpx;
  line-height: 1.65;
}
</style>