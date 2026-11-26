<template>
  <view class="page">
    <StatePanel v-if="status === 'loading'" variant="loading" description="正在加载动态详情…" />

    <StatePanel
      v-else-if="status === 'notfound'"
      variant="notfound"
      title="动态不存在或已删除"
      description="这条动态可能已被作者或管理员删除，无法继续查看"
      action-text="返回广场"
      @action="backToSquare"
    />

    <StatePanel
      v-else-if="status === 'error'"
      variant="error"
      :description="errorMessage"
      action-text="重新加载"
      @action="retry"
    />

    <view v-else-if="post" class="detail">
      <view class="detail__head">
        <view class="detail__avatar">{{ avatarText }}</view>
        <view class="detail__head-main">
          <text class="detail__author">{{ authorName }}</text>
          <text class="detail__time">{{ timeText }}</text>
        </view>
        <text class="detail__id">#{{ post.id }}</text>
      </view>

      <text v-if="bodyText" class="detail__text" selectable>{{ bodyText }}</text>
      <text v-else class="detail__text detail__text--muted">（无正文，仅媒体内容）</text>

      <MediaPlaceholder v-if="hasMedia" :media="post.media" variant="detail" />

      <view class="detail__meta">
        <text class="detail__meta-label">标签</text>
        <TagRow v-if="post.tags && post.tags.length" :tags="post.tags" />
        <text v-else class="detail__meta-empty">未添加标签</text>
      </view>

      <view class="summary">
        <view class="summary__head">
          <text class="summary__title">评分摘要</text>
          <text class="summary__tag">后续阶段开放</text>
        </view>
        <RatingSummary :rating="post.rating" show-hint />
        <text class="summary__hint">评分与评论功能将在后续阶段实现，本页仅展示已有统计</text>
      </view>

      <view class="detail__footer">
        <text class="detail__footer-text">内容由 {{ authorName }} 发布</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import MediaPlaceholder from '@/components/MediaPlaceholder.vue'
import RatingSummary from '@/components/RatingSummary.vue'
import StatePanel from '@/components/StatePanel.vue'
import TagRow from '@/components/TagRow.vue'
import { postsApi } from '@/api/posts.ts'
import { describeError, isNotFound } from '@/api/errors.ts'
import { formatDateTime } from '@/utils/format.ts'
import type { PostDetail } from '@/api/types.ts'

type DetailStatus = 'loading' | 'ready' | 'error' | 'notfound'

const post = ref<PostDetail | null>(null)
const status = ref<DetailStatus>('loading')
const errorMessage = ref('')
let currentPostId = 0
let requestToken = 0

const authorName = computed(() => post.value?.author?.nickname || '匿名用户')
const avatarText = computed(() => Array.from(authorName.value)[0] || '?')
const timeText = computed(() => formatDateTime(post.value?.createdAt))
const bodyText = computed(() => (post.value?.text ?? '').trim())
const hasMedia = computed(
  () => Array.isArray(post.value?.media) && (post.value?.media.length ?? 0) > 0,
)

/** 校验路由参数：非正整数直接判定为“不存在”，不发无效请求。 */
function parsePostId(raw: unknown): number {
  const value = Number(raw)
  if (!Number.isInteger(value) || value <= 0) return 0
  return value
}

async function loadDetail(postId: number): Promise<void> {
  const token = ++requestToken
  status.value = 'loading'
  errorMessage.value = ''
  try {
    const result = await postsApi.detail(postId)
    if (token !== requestToken) return
    post.value = result
    status.value = 'ready'
  } catch (error) {
    if (token !== requestToken) return
    if (isNotFound(error)) {
      post.value = null
      status.value = 'notfound'
      return
    }
    errorMessage.value = describeError(error)
    status.value = 'error'
  }
}

onLoad((query) => {
  const postId = parsePostId(query?.id)
  if (!postId) {
    status.value = 'notfound'
    return
  }
  currentPostId = postId
  void loadDetail(postId)
})

function retry(): void {
  if (currentPostId) void loadDetail(currentPostId)
}

function backToSquare(): void {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: '/pages/square/index' })
}
</script>

<style scoped>
.detail {
  padding: 32rpx;
  background-color: #ffffff;
  border-radius: 20rpx;
}

.detail__head {
  display: flex;
  align-items: center;
}

.detail__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background-color: #e7eeff;
  color: #3a6ff2;
  font-size: 34rpx;
  font-weight: 600;
}

.detail__head-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 20rpx;
}

.detail__author {
  font-size: 32rpx;
  font-weight: 600;
  color: #1b2130;
}

.detail__time {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #9aa2b1;
}

.detail__id {
  font-size: 22rpx;
  color: #c2c8d4;
}

.detail__text {
  display: block;
  margin-top: 28rpx;
  font-size: 30rpx;
  line-height: 1.75;
  color: #2b3242;
  word-break: break-all;
}

.detail__text--muted {
  color: #a0a7b6;
  font-style: italic;
}

.detail__meta {
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #f0f2f6;
}

.detail__meta-label {
  font-size: 24rpx;
  color: #8a93a5;
}

.detail__meta-empty {
  font-size: 24rpx;
  color: #b6bcc8;
}

.summary {
  margin-top: 28rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  background-color: #fafbfd;
}

.summary__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.summary__title {
  font-size: 26rpx;
  font-weight: 600;
  color: #23293a;
}

.summary__tag {
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  background-color: #eef0f4;
  font-size: 20rpx;
  color: #8a93a5;
}

.summary__hint {
  display: block;
  margin-top: 14rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: #a0a7b6;
}

.detail__footer {
  margin-top: 32rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #f0f2f6;
  text-align: center;
}

.detail__footer-text {
  font-size: 22rpx;
  color: #b6bcc8;
}
</style>
