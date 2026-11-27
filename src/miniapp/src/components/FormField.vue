<script setup lang="ts">
/**
 * 表单输入项 · 维护人：M05
 *
 * 统一标签、必填标记、错误提示和密码显隐；错误文案由 `utils/validate.ts`
 * 的校验函数返回，页面只负责把结果传给 `error`。
 * 校验时机约定：失焦（blur）与提交时校验，输入过程中清除旧错误，避免边打字边报错。
 */
import { computed, ref } from 'vue'

type FieldType = 'text' | 'password' | 'number'

const props = withDefaults(
  defineProps<{
    /** v-model 值。 */
    modelValue: string
    /** 标签文案。 */
    label?: string
    /** 输入类型，password 会渲染为密文并提供显隐切换。 */
    type?: FieldType
    placeholder?: string
    /** 错误文案；为空表示无错误。 */
    error?: string
    /** 辅助说明，有错误时让位给错误文案。 */
    hint?: string
    required?: boolean
    /** -1 表示不限制长度，与微信 input 的 maxlength 语义一致。 */
    maxlength?: number
    disabled?: boolean
    /** 是否显示"清空"按钮。 */
    clearable?: boolean
  }>(),
  {
    label: '',
    type: 'text',
    placeholder: '请输入',
    error: '',
    hint: '',
    required: false,
    maxlength: -1,
    disabled: false,
    clearable: true,
  }
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'blur', value: string): void
  (event: 'confirm', value: string): void
}>()

/** 密码是否可见，默认不可见。 */
const visible = ref(false)

const inputType = computed(() => (props.type === 'number' ? 'number' : 'text'))
const isPassword = computed(() => props.type === 'password' && !visible.value)
const hasClear = computed(() => props.clearable && props.modelValue.length > 0 && !props.disabled)

function readValue(event: Event): string {
  // 小程序把值放在 `detail.value`；H5 / 单元测试里是 DOM 事件的 `target.value`，两种都支持。
  const detail = (event as unknown as { detail?: { value?: unknown } }).detail
  if (detail && typeof detail.value === 'string') {
    return detail.value
  }
  const target = (event as unknown as { target?: { value?: unknown } }).target
  return target && typeof target.value === 'string' ? target.value : ''
}

function handleInput(event: Event): void {
  emit('update:modelValue', readValue(event))
}

function handleBlur(event: Event): void {
  emit('blur', readValue(event))
}

function handleConfirm(event: Event): void {
  emit('confirm', readValue(event))
}

function handleClear(): void {
  emit('update:modelValue', '')
}

function toggleVisible(): void {
  visible.value = !visible.value
}
</script>

<template>
  <view class="form-field" :class="{ 'form-field--error': !!error, 'form-field--disabled': disabled }">
    <view v-if="label" class="form-field__label">
      <text class="form-field__label-text">{{ label }}</text>
      <text v-if="required" class="form-field__required">*</text>
    </view>

    <view class="form-field__control">
      <input
        class="form-field__input"
        :type="inputType"
        :password="isPassword ? true : undefined"
        :value="modelValue"
        :placeholder="placeholder"
        :placeholder-class="'form-placeholder'"
        :maxlength="maxlength"
        :disabled="disabled"
        @input="handleInput"
        @blur="handleBlur"
        @confirm="handleConfirm"
      />
      <text v-if="type === 'password'" class="form-field__action" @tap="toggleVisible">
        {{ visible ? '隐藏' : '显示' }}
      </text>
      <text v-if="hasClear" class="form-field__action" @tap="handleClear">清空</text>
    </view>

    <text v-if="error" class="form-field__error">{{ error }}</text>
    <text v-else-if="hint" class="form-field__hint">{{ hint }}</text>
  </view>
</template>

<style lang="scss" scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: $space-xs;

  &__label {
    display: flex;
    align-items: center;
  }

  &__label-text {
    font-size: $font-size-body;
    font-weight: $font-weight-medium;
    color: $text-secondary;
  }

  &__required {
    margin-left: 4rpx;
    font-size: $font-size-body;
    color: $color-danger;
  }

  &__control {
    display: flex;
    align-items: center;
    gap: $space-sm;
    height: $control-height;
    padding: 0 $space-md;
    background-color: $bg-muted;
    border: 1px solid $border-color;
    border-radius: $control-radius;
    transition: border-color $duration-fast ease;
  }

  &__input {
    flex: 1;
    min-width: 0;
    font-size: $font-size-subtitle;
    color: $text-primary;
  }

  &__action {
    font-size: $font-size-caption;
    color: $brand-primary;
  }

  &__error {
    font-size: $font-size-caption;
    color: $color-danger;
  }

  &__hint {
    font-size: $font-size-caption;
    color: $text-tertiary;
  }

  &--error &__control {
    border-color: $color-danger;
    background-color: $color-danger-light;
  }

  &--disabled &__control {
    opacity: 0.6;
  }
}
</style>
