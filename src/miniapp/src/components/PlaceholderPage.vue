<script setup lang="ts">
/**
 * 未实现页面的统一占位 · 维护人：M05
 *
 * 目的：让路由、导航和样式基线在工程里真实可跑，同时明确页面归属。
 * M06/M07 实现自己的页面时，直接用真实内容替换对应的 `pages/<页面>/index.vue`，
 * 不要删除 `config/routes.ts` 与 `pages.json` 中的路由登记。
 */
import StateView from '@/components/StateView.vue'

withDefaults(
  defineProps<{
    /** 页面负责人编号，例如 M06。 */
    owner: string
    /** 页面名称。 */
    title: string
    /** 一句话说明页面职责与依赖接口。 */
    description: string
    /** 该页面要实现的关键点，便于负责人接手。 */
    todos?: string[]
  }>(),
  { todos: () => [] }
)
</script>

<template>
  <view class="page">
    <view class="section">
      <view class="placeholder__head">
        <text class="placeholder__owner">{{ owner }} 负责</text>
        <text class="placeholder__stage">占位页</text>
      </view>
      <text class="section__title">{{ title }}</text>
      <text class="section__desc">{{ description }}</text>

      <view v-if="todos.length > 0" class="divider" />
      <view v-if="todos.length > 0" class="placeholder__todos">
        <text v-for="item in todos" :key="item" class="placeholder__todo">· {{ item }}</text>
      </view>
    </view>

    <view class="section section--flush">
      <StateView
        status="empty"
        compact
        title="页面尚未实现"
        description="路由、全局样式与公共组件已就绪，替换本文件内容即可开始开发。"
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.placeholder {
  &__head {
    @include flex-between;

    margin-bottom: $space-xs;
  }

  &__owner {
    padding: 4rpx $space-sm;
    font-size: $font-size-caption;
    color: $brand-primary;
    background-color: $brand-primary-light;
    border-radius: $radius-pill;
  }

  &__stage {
    font-size: $font-size-caption;
    color: $text-tertiary;
  }

  &__todos {
    display: flex;
    flex-direction: column;
    gap: $space-xs;
  }

  &__todo {
    font-size: $font-size-body;
    line-height: $line-height-base;
    color: $text-secondary;
  }
}
</style>
