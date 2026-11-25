<script setup lang="ts">
/**
 * 通用按钮 · 维护人：M05
 *
 * 统一高度、圆角、字号与点击反馈，并内置"防重复提交"：
 * 加载中或禁用时不响应点击，两次有效点击之间默认至少间隔 400ms。
 * 提交类操作请把 `loading` 绑定到请求状态，而不是自己写防抖。
 */
import { computed, ref } from 'vue'

type ButtonType = 'primary' | 'secondary' | 'ghost' | 'danger'
type ButtonSize = 'normal' | 'large'

const props = withDefaults(
  defineProps<{
    /** 视觉类型：主要 / 次要 / 描边 / 危险。 */
    type?: ButtonType
    /** 尺寸：normal 88rpx，large 96rpx。 */
    size?: ButtonSize
    disabled?: boolean
    /** 为 true 时显示加载态并屏蔽点击。 */
    loading?: boolean
    /** 是否占满一行，默认占满。 */
    block?: boolean
    /** 连续点击的最小间隔（毫秒），0 表示不做间隔限制。 */
    throttle?: number
    /** 无插槽内容时的文字。 */
    text?: string
  }>(),
  {
    type: 'primary',
    size: 'normal',
    disabled: false,
    loading: false,
    block: true,
    throttle: 400,
    text: '',
  }
)

const emit = defineEmits<{ (event: 'click'): void }>()

const lastClickAt = ref(0)
const isBusy = computed(() => props.disabled || props.loading)

function handleTap(): void {
  if (isBusy.value) {
    return
  }
  const now = Date.now()
  if (props.throttle > 0 && now - lastClickAt.value < props.throttle) {
    return
  }
  lastClickAt.value = now
  emit('click')
}
</script>

<template>
  <button
    class="app-button"
    :class="[
      `app-button--${type}`,
      `app-button--${size}`,
      {
        'app-button--block': block,
        'app-button--busy': isBusy,
      },
    ]"
    :disabled="isBusy"
    hover-class="app-button--hover"
    :hover-stay-time="80"
    @tap="handleTap"
  >
    <view v-if="loading" class="app-button__spinner" />
    <text class="app-button__text">
      <slot>{{ text }}</slot>
    </text>
  </button>
</template>

<style lang="scss" scoped>
.app-button {
  @include button-reset;
  @include flex-center;

  gap: $space-xs;
  height: $control-height;
  padding: 0 $space-lg;
  font-size: $font-size-subtitle;
  border-radius: $control-radius;
  transition: opacity $duration-fast ease, background-color $duration-fast ease;

  &--block {
    width: 100%;
  }

  &--large {
    height: $control-height-lg;
    font-size: $font-size-title;
  }

  &--primary {
    color: $text-inverse;
    background-color: $brand-primary;
  }

  &--secondary {
    color: $brand-primary;
    background-color: $brand-primary-light;
  }

  &--ghost {
    color: $text-primary;
    background-color: transparent;
    border: 1px solid $border-color;
  }

  &--danger {
    color: $text-inverse;
    background-color: $color-danger;
  }

  &--hover {
    opacity: 0.86;
  }

  &--busy {
    opacity: 0.6;
  }

  &__spinner {
    width: 28rpx;
    height: 28rpx;
    border: 3rpx solid rgba(255, 255, 255, 0.45);
    border-top-color: $text-inverse;
    border-radius: 50%;
    animation: app-button-spin 800ms linear infinite;
  }

  &__text {
    line-height: 1;
  }

  /* 小程序 button 默认带 1px 边框伪元素，必须去掉才能与设计稿一致 */
  &::after {
    border: none;
  }
}

/* 浅色按钮上的加载圈需要更深的颜色 */
.app-button--secondary .app-button__spinner,
.app-button--ghost .app-button__spinner {
  border-color: rgba(47, 111, 235, 0.25);
  border-top-color: $brand-primary;
}

@keyframes app-button-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
