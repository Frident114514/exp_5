<template>
  <view class="page-shell">
    <AppHeader
      eyebrow="M06 · 发布动态"
      title="写下这一刻"
      description="第一周先完成纯文本发布，提交、成功与失败状态均可在 mock 模式下演示。"
    />

    <view class="page-content">
      <view class="card">
        <view class="section-heading">
          <view>
            <text class="section-heading__title">动态正文</text>
            <text class="section-heading__hint">发布成功后会进入“我的动态”列表</text>
          </view>
          <text :class="['counter', { 'counter--danger': textLength > POST_TEXT_MAX_LENGTH }]">
            {{ textLength }} / {{ POST_TEXT_MAX_LENGTH }}
          </text>
        </view>

        <view class="mock-note">
          <view class="mock-note__dot" />
          <text>
            当前使用符合 POST /api/v1/posts 契约的 Mock 服务；真实接口由 M02 合入后替换。
          </text>
        </view>

        <view class="form-field">
          <textarea
            v-model="text"
            class="post-input"
            :disabled="status === 'submitting'"
            :maxlength="-1"
            placeholder="分享今天发生的事情……"
            placeholder-class="post-input__placeholder"
            @input="handleTextInput"
          />
          <text v-if="validationMessage" class="field-error">{{ validationMessage }}</text>
        </view>

        <view class="contract-preview">
          <text class="contract-preview__label">本次提交字段</text>
          <text class="contract-preview__value">
            text、mediaIds: []、tags: []
          </text>
        </view>

        <view class="failure-toggle">
          <view class="failure-toggle__copy">
            <text class="failure-toggle__title">模拟下一次网络失败</text>
            <text class="failure-toggle__hint">仅用于课堂演示失败提示与重试，不影响真实接口</text>
          </view>
          <switch
            :checked="simulateFailure"
            color="#335cff"
            :disabled="status === 'submitting'"
            @change="handleFailureToggle"
          />
        </view>

        <StatusPanel
          v-if="status === 'success'"
          class="feedback"
          tone="success"
          title="发布成功"
          :message="`动态 #${createdPostId} 已创建，可返回我的动态查看。`"
        />

        <StatusPanel
          v-if="status === 'error'"
          class="feedback"
          tone="error"
          title="发布失败"
          :message="apiErrorMessage"
        />

        <button
          v-if="status !== 'success'"
          class="primary-button"
          :disabled="status === 'submitting'"
          @tap="submit"
        >
          {{ status === 'submitting' ? '正在提交…' : status === 'error' ? '重新提交' : '发布动态' }}
        </button>

        <view v-if="status === 'success'" class="success-actions">
          <button class="primary-button" @tap="openMyPosts">查看我的动态</button>
          <button class="secondary-button" @tap="continuePublishing">继续发布</button>
        </view>
      </view>

      <button class="text-button" @tap="openMyPosts">返回我的动态</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import AppHeader from '@/components/AppHeader.vue'
import StatusPanel from '@/components/StatusPanel.vue'
import { getPostApi, isUsingMock, mockPostApi } from '@/api/runtime'
import { countCodePoints, POST_TEXT_MAX_LENGTH, validateTextOnlyPost } from '@/utils/postRules'
import { getErrorMessage } from '@/utils/errors'

type RequestStatus = 'idle' | 'submitting' | 'success' | 'error'

const text = ref('')
const status = ref<RequestStatus>('idle')
const createdPostId = ref<number | null>(null)
const apiErrorMessage = ref('')
const simulateFailure = ref(false)
const validationAttempted = ref(false)

const textLength = computed(() => countCodePoints(text.value))
const validationMessage = computed(() => {
  if (!validationAttempted.value) return ''
  const result = validateTextOnlyPost(text.value)
  return result.valid ? '' : result.message
})

function handleTextInput(): void {
  if (status.value === 'error') {
    status.value = 'idle'
    apiErrorMessage.value = ''
  }
}

function handleFailureToggle(event: Event): void {
  const switchEvent = event as unknown as { detail: { value: boolean } }
  simulateFailure.value = switchEvent.detail.value
}

async function submit(): Promise<void> {
  if (status.value === 'submitting') return

  validationAttempted.value = true
  const validation = validateTextOnlyPost(text.value)
  if (!validation.valid) {
    status.value = 'idle'
    return
  }

  if (simulateFailure.value && isUsingMock) {
    mockPostApi.failNextRequest()
    simulateFailure.value = false
  }

  status.value = 'submitting'
  apiErrorMessage.value = ''

  try {
    const created = await getPostApi().create({
      text: validation.normalized,
      mediaIds: [],
      tags: [],
    })
    createdPostId.value = created.id
    status.value = 'success'
    text.value = ''
    validationAttempted.value = false
  } catch (error) {
    apiErrorMessage.value = getErrorMessage(error)
    status.value = 'error'
  }
}

function continuePublishing(): void {
  status.value = 'idle'
  createdPostId.value = null
  apiErrorMessage.value = ''
}

function openMyPosts(): void {
  const pages = getCurrentPages()
  const previous = pages[pages.length - 2]

  if (previous?.route === 'pages/my-posts/index') {
    uni.navigateBack()
    return
  }

  uni.redirectTo({ url: '/pages/my-posts/index' })
}
</script>

<style scoped>
.page-shell {
  min-height: 100vh;
  padding: 28rpx 28rpx 54rpx;
  background:
    radial-gradient(circle at 0% 0%, rgba(89, 122, 255, 0.08), transparent 30%),
    #f5f7fb;
}

.page-content {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-top: 24rpx;
}

.card {
  padding: 34rpx;
  border: 1rpx solid #e4e8ef;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 18rpx 60rpx rgba(27, 39, 71, 0.08);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  margin-bottom: 24rpx;
}

.section-heading__title,
.section-heading__hint {
  display: block;
}

.section-heading__title {
  color: #172033;
  font-size: 32rpx;
  font-weight: 800;
}

.section-heading__hint {
  margin-top: 8rpx;
  color: #7a8496;
  font-size: 23rpx;
  line-height: 1.5;
}

.counter {
  flex: 0 0 auto;
  color: #7a8496;
  font-size: 22rpx;
  font-variant-numeric: tabular-nums;
}

.counter--danger {
  color: #c4323d;
  font-weight: 700;
}

.mock-note {
  display: flex;
  gap: 14rpx;
  align-items: flex-start;
  margin-bottom: 24rpx;
  padding: 18rpx 20rpx;
  color: #48536a;
  border: 1rpx solid #dfe5f4;
  border-radius: 16rpx;
  background: #f6f8ff;
  font-size: 22rpx;
  line-height: 1.6;
}

.mock-note__dot {
  flex: 0 0 auto;
  width: 12rpx;
  height: 12rpx;
  margin-top: 11rpx;
  border-radius: 50%;
  background: #335cff;
}

.form-field {
  margin-bottom: 22rpx;
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

.contract-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 18rpx 20rpx;
  border-radius: 15rpx;
  background: #f5f7fb;
  font-size: 22rpx;
}

.contract-preview__label {
  color: #7a8496;
}

.contract-preview__value {
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}

.failure-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22rpx;
  margin-top: 20rpx;
  padding: 22rpx 0 2rpx;
  border-top: 1rpx solid #edf0f5;
}

.failure-toggle__copy {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.failure-toggle__title {
  color: #344054;
  font-size: 24rpx;
  font-weight: 700;
}

.failure-toggle__hint {
  color: #8b95a5;
  font-size: 21rpx;
  line-height: 1.5;
}

.feedback {
  margin-top: 24rpx;
}

.primary-button,
.secondary-button {
  width: 100%;
  margin: 26rpx 0 0;
  padding: 24rpx 30rpx;
  border-radius: 18rpx;
  font-size: 28rpx;
  font-weight: 750;
  line-height: 1.2;
}

.primary-button {
  color: #ffffff;
  background: linear-gradient(135deg, #3159f4, #557cff);
  box-shadow: 0 12rpx 28rpx rgba(51, 92, 255, 0.22);
}

.primary-button[disabled] {
  opacity: 0.58;
  box-shadow: none;
}

.secondary-button {
  color: #2748ad;
  background: #edf1ff;
}

.success-actions {
  display: flex;
  gap: 16rpx;
}

.success-actions .primary-button,
.success-actions .secondary-button {
  flex: 1;
}

.text-button {
  margin: 0;
  padding: 18rpx;
  color: #58647a;
  background: transparent;
  font-size: 24rpx;
}
</style>