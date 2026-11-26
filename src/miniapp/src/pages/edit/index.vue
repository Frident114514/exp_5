<template>
  <view class="page-shell">
    <AppHeader
      eyebrow="M06 · 编辑动态"
      title="修改正文"
      description="PATCH 只提交 text，省略 mediaIds 与 tags，避免覆盖或误删已有媒体关联。"
    />

    <view class="page-content">
      <view v-if="loadStatus === 'loading'" class="card loading-card">
        <view class="loading-card__spinner" />
        <text class="loading-card__text">正在读取动态…</text>
      </view>

      <template v-else-if="loadStatus === 'error'">
        <StatusPanel tone="error" title="无法编辑此动态" :message="loadError" />
        <button class="secondary-button" @tap="goBack">返回我的动态</button>
      </template>

      <view v-else-if="post" class="card">
        <view class="post-meta">
          <view class="post-meta__id">动态 #{{ post.id }}</view>
          <view class="post-meta__author">
            {{ post.author.nickname }} · 作者 ID {{ post.author.id }}
          </view>
        </view>

        <view class="section-heading">
          <view>
            <text class="section-heading__title">正文内容</text>
            <text class="section-heading__hint">保存成功后返回“我的动态”即可看到修改</text>
          </view>
          <text :class="['counter', { 'counter--danger': textLength > POST_TEXT_MAX_LENGTH }]">
            {{ textLength }} / {{ POST_TEXT_MAX_LENGTH }}
          </text>
        </view>

        <textarea
          v-model="text"
          class="post-input"
          :disabled="saveStatus === 'submitting'"
          :maxlength="-1"
          placeholder="请输入动态正文"
          placeholder-class="post-input__placeholder"
          @input="handleTextInput"
        />
        <text v-if="validationMessage" class="field-error">{{ validationMessage }}</text>

        <view class="patch-preview">
          <text class="patch-preview__label">本次 PATCH 请求体</text>
          <text class="patch-preview__value">{ "text": "{{ trimmedPreview }}" }</text>
          <text class="patch-preview__hint">mediaIds 和 tags 不发送，由后端保留原值。</text>
        </view>

        <StatusPanel
          v-if="saveStatus === 'success'"
          class="feedback"
          tone="success"
          title="修改已保存"
          :message="`动态 #${post.id} 已更新，最后修改时间为 ${formatDateTime(post.updatedAt)}。`"
        />

        <StatusPanel
          v-if="saveStatus === 'error'"
          class="feedback"
          tone="error"
          title="保存失败"
          :message="saveErrorMessage"
        />

        <view v-if="saveStatus !== 'success'" class="form-actions">
          <button class="secondary-button" :disabled="saveStatus === 'submitting'" @tap="goBack">
            取消
          </button>
          <button class="primary-button" :disabled="saveStatus === 'submitting'" @tap="save">
            {{ saveStatus === 'submitting' ? '保存中…' : '保存修改' }}
          </button>
        </view>

        <button v-else class="primary-button" @tap="goBack">返回我的动态</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppHeader from '@/components/AppHeader.vue'
import StatusPanel from '@/components/StatusPanel.vue'
import { getPostApi, mockPostApi } from '@/api/runtime'
import type { PostView } from '@/api/types'
import { countCodePoints, POST_TEXT_MAX_LENGTH, validateEditedText } from '@/utils/postRules'
import { getErrorMessage } from '@/utils/errors'
import { formatDateTime } from '@/utils/format'

type LoadStatus = 'loading' | 'ready' | 'error'
type SaveStatus = 'idle' | 'submitting' | 'success' | 'error'

const currentUser = mockPostApi.getCurrentUser()
const post = ref<PostView | null>(null)
const text = ref('')
const loadStatus = ref<LoadStatus>('loading')
const saveStatus = ref<SaveStatus>('idle')
const loadError = ref('')
const saveErrorMessage = ref('')
const validationAttempted = ref(false)

const textLength = computed(() => countCodePoints(text.value))
const trimmedPreview = computed(() => text.value.trim().slice(0, 28).replace(/"/g, '\\"'))
const validationMessage = computed(() => {
  if (!validationAttempted.value || !post.value) return ''
  const result = validateEditedText(text.value, post.value.media.length)
  return result.valid ? '' : result.message
})

onLoad((options) => {
  const rawId = options?.id
  const postId = Number(rawId)
  if (!rawId || !Number.isInteger(postId) || postId < 1) {
    loadStatus.value = 'error'
    loadError.value = '缺少有效的动态 ID'
    return
  }
  void loadPost(postId)
})

async function loadPost(postId: number): Promise<void> {
  loadStatus.value = 'loading'
  loadError.value = ''

  try {
    const result = await getPostApi().detail(postId)
    if (result.author.id !== currentUser.id) {
      throw new Error('只能编辑本人动态')
    }
    post.value = result
    text.value = result.text
    loadStatus.value = 'ready'
  } catch (error) {
    loadError.value = getErrorMessage(error)
    loadStatus.value = 'error'
  }
}

function handleTextInput(): void {
  if (saveStatus.value === 'error') {
    saveStatus.value = 'idle'
    saveErrorMessage.value = ''
  }
}

async function save(): Promise<void> {
  if (!post.value || saveStatus.value === 'submitting') return

  validationAttempted.value = true
  const validation = validateEditedText(text.value, post.value.media.length)
  if (!validation.valid) return

  saveStatus.value = 'submitting'
  saveErrorMessage.value = ''

  try {
    const updated = await getPostApi().edit(post.value.id, {
      text: validation.normalized,
    })
    post.value = updated
    text.value = updated.text
    saveStatus.value = 'success'
  } catch (error) {
    saveErrorMessage.value = getErrorMessage(error)
    saveStatus.value = 'error'
  }
}

function goBack(): void {
  uni.navigateBack({
    fail: () => {
      uni.redirectTo({ url: '/pages/my-posts/index' })
    },
  })
}
</script>

<style scoped>
.page-shell {
  min-height: 100vh;
  padding: 28rpx 28rpx 54rpx;
  background:
    radial-gradient(circle at 0% 100%, rgba(89, 122, 255, 0.07), transparent 34%),
    #f5f7fb;
}

.page-content {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  margin-top: 24rpx;
}

.card {
  padding: 34rpx;
  border: 1rpx solid #e4e8ef;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 18rpx 60rpx rgba(27, 39, 71, 0.08);
}

.loading-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  min-height: 180rpx;
}

.loading-card__spinner {
  width: 30rpx;
  height: 30rpx;
  border: 4rpx solid #dce4ff;
  border-top-color: #335cff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-card__text {
  color: #667085;
  font-size: 24rpx;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.post-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 28rpx;
}

.post-meta__id {
  padding: 7rpx 13rpx;
  color: #2748ad;
  border-radius: 10rpx;
  background: #edf1ff;
  font-size: 22rpx;
  font-weight: 800;
}

.post-meta__author {
  color: #7a8496;
  font-size: 22rpx;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  margin-bottom: 18rpx;
}

.section-heading__title,
.section-heading__hint {
  display: block;
}

.section-heading__title {
  color: #172033;
  font-size: 31rpx;
  font-weight: 800;
}

.section-heading__hint {
  margin-top: 7rpx;
  color: #7a8496;
  font-size: 22rpx;
  line-height: 1.5;
}

.counter {
  flex: 0 0 auto;
  color: #7a8496;
  font-size: 22rpx;
}

.counter--danger {
  color: #c4323d;
  font-weight: 700;
}

.post-input {
  width: 100%;
  min-height: 330rpx;
  padding: 26rpx;
  color: #20293a;
  border: 1rpx solid #d8deea;
  border-radius: 20rpx;
  background: #fbfcff;
  font-size: 29rpx;
  line-height: 1.7;
}

.post-input:focus {
  border-color: #7f9bff;
  background: #ffffff;
}

.post-input__placeholder {
  color: #a1a9b6;
}

.field-error {
  display: block;
  margin-top: 12rpx;
  color: #c4323d;
  font-size: 23rpx;
}

.patch-preview {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  margin-top: 24rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: #f5f7fb;
}

.patch-preview__label,
.patch-preview__hint {
  color: #7a8496;
  font-size: 21rpx;
}

.patch-preview__value {
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 21rpx;
  line-height: 1.55;
  word-break: break-all;
}

.patch-preview__hint {
  line-height: 1.5;
}

.feedback {
  margin-top: 24rpx;
}

.form-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 28rpx;
}

.form-actions button,
.card > .primary-button,
.secondary-button {
  flex: 1;
  margin: 0;
  padding: 22rpx 26rpx;
  border-radius: 17rpx;
  font-size: 26rpx;
  font-weight: 750;
}

.primary-button {
  color: #ffffff;
  background: linear-gradient(135deg, #3159f4, #557cff);
  box-shadow: 0 12rpx 28rpx rgba(51, 92, 255, 0.2);
}

.primary-button[disabled],
.secondary-button[disabled] {
  opacity: 0.56;
}

.secondary-button {
  color: #40506c;
  background: #eef1f6;
}

.card > .primary-button {
  width: 100%;
  margin-top: 28rpx;
}
</style>