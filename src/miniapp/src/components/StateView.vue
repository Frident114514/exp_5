<script setup lang="ts">
/**
 * 页面状态容器 · 维护人：M05
 *
 * 加载 / 空 / 错误 / 正常 四种状态的统一外观，避免每个页面各写一套。
 * 用法：
 *   <StateView :status="state" :error-message="error" @retry="load">
 *     <view>正常内容</view>
 *   </StateView>
 *
 * 约定：列表页首次加载用 loading；结果为空用 empty 并提示如何产生内容；
 * 失败必须给出 retry 入口，不能只弹一个 toast 后停在空白页。
 */
import { computed } from 'vue'
import AppButton from './AppButton.vue'

type StateStatus = 'loading' | 'empty' | 'error' | 'ready'

const props = withDefaults(
  defineProps<{
    /** 当前状态。 */
    status: StateStatus
    /** 覆盖默认标题。 */
    title?: string
    /** 补充说明，例如失败原因或下一步操作。 */
    description?: string
    /** 错误态的重试按钮文案。 */
    retryText?: string
    /** 紧凑模式：用于页面内小区域，而不是整页状态。 */
    compact?: boolean
  }>(),
  {
    title: '',
    description: '',
    retryText: '重新加载',
    compact: false,
  }
)

const emit = defineEmits<{ (event: 'retry'): void }>()

const DEFAULT_TITLES: Record<Exclude<StateStatus, 'ready'>, string> = {
  loading: '加载中…',
  empty: '暂无内容',
  error: '加载失败',
}

const resolvedTitle = computed(() => {
  if (props.title) {
    return props.title
  }
  return props.status === 'ready' ? '' : DEFAULT_TITLES[props.status]
})
</script>

<template>
  <view class="state-view" :class="{ 'state-view--compact': compact }">
    <template v-if="status === 'ready'">
      <slot />
    </template>

    <view v-else class="state-view__body">
      <view v-if="status === 'loading'" class="state-view__spinner" />
      <view v-else class="state-view__glyph" :class="`state-view__glyph--${status}`">
        <text class="state-view__glyph-text">{{ status === 'empty' ? '空' : '!' }}</text>
      </view>

      <text class="state-view__title">{{ resolvedTitle }}</text>
      <text v-if="description" class="state-view__desc">{{ description }}</text>

      <view v-if="status === 'error'" class="state-view__actions">
        <AppButton type="secondary" size="normal" :block="false" @click="emit('retry')">
          {{ retryText }}
        </AppButton>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.state-view {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 480rpx;
  padding: $space-xl $space-lg;

  &--compact {
    min-height: 240rpx;
    padding: $space-lg;
  }

  &__body {
    @include flex-center;

    flex-direction: column;
    gap: $space-sm;
    text-align: center;
  }

  &__spinner {
    width: 56rpx;
    height: 56rpx;
    margin-bottom: $space-xs;
    border: 4rpx solid $brand-primary-light;
    border-top-color: $brand-primary;
    border-radius: 50%;
    animation: state-view-spin 800ms linear infinite;
  }

  &__glyph {
    @include flex-center;

    width: 96rpx;
    height: 96rpx;
    margin-bottom: $space-xs;
    border-radius: 50%;

    &--empty {
      background-color: $bg-muted;
    }

    &--error {
      background-color: $color-danger-light;
    }
  }

  &__glyph-text {
    font-size: $font-size-title;
    font-weight: $font-weight-bold;
    color: $text-tertiary;
  }

  &__glyph--error &__glyph-text {
    color: $color-danger;
  }

  &__title {
    font-size: $font-size-subtitle;
    font-weight: $font-weight-medium;
    color: $text-primary;
  }

  &__desc {
    max-width: 520rpx;
    font-size: $font-size-body;
    line-height: $line-height-base;
    color: $text-tertiary;
  }

  &__actions {
    margin-top: $space-xs;
  }
}

@keyframes state-view-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
