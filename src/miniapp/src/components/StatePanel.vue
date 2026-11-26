<template>
  <view class="state">
    <view class="state__icon" :class="'state__icon--' + variant">
      <text v-if="variant === 'loading'" class="state__spinner">◌</text>
      <text v-else>{{ iconText }}</text>
    </view>
    <text class="state__title">{{ resolvedTitle }}</text>
    <text v-if="resolvedDescription" class="state__desc">{{ resolvedDescription }}</text>
    <button
      v-if="actionText"
      class="state__action"
      :loading="variant === 'loading'"
      @tap="emit('action')"
    >
      {{ actionText }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    variant: 'loading' | 'empty' | 'error' | 'notfound'
    title?: string
    description?: string
    actionText?: string
  }>(),
  { title: '', description: '', actionText: '' },
)

const emit = defineEmits<{ (event: 'action'): void }>()

const DEFAULT_TITLE: Record<string, string> = {
  loading: '正在加载…',
  empty: '还没有动态',
  error: '加载失败',
  notfound: '动态不存在',
}

const DEFAULT_DESC: Record<string, string> = {
  loading: '首次加载需要一点时间，请稍候',
  empty: '等第一位同学发布动态后，这里就会出现内容',
  error: '请检查网络或服务端是否已启动，然后重试',
  notfound: '这条动态可能已被作者或管理员删除',
}

const ICON: Record<string, string> = {
  loading: '◌',
  empty: '🗒',
  error: '⚠',
  notfound: '🚫',
}

const resolvedTitle = computed(() => props.title || DEFAULT_TITLE[props.variant])
const resolvedDescription = computed(() => props.description || DEFAULT_DESC[props.variant])
const iconText = computed(() => ICON[props.variant] || '•')
</script>

<style scoped>
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 60rpx;
}

.state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background-color: #eef0f4;
  font-size: 48rpx;
  color: #8a93a5;
}

.state__icon--error {
  background-color: #fff1f0;
  color: #e5484d;
}

.state__icon--notfound {
  background-color: #f4f5f7;
}

.state__title {
  margin-top: 28rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #23293a;
}

.state__desc {
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #8a93a5;
  text-align: center;
}

.state__action {
  margin-top: 36rpx;
  padding: 0 56rpx;
  height: 76rpx;
  line-height: 76rpx;
  font-size: 28rpx;
  color: #ffffff;
  background-color: #3a6ff2;
  border-radius: 38rpx;
}

.state__action::after {
  border: none;
}
</style>
