<script setup lang="ts">
import { computed } from 'vue'

import { auth } from '../../app/services'

const props = defineProps<{ kind: 'forbidden' | 'notFound' }>()
const title = computed(() => (props.kind === 'forbidden' ? '没有访问权限' : '页面不存在'))
const description = computed(() =>
  props.kind === 'forbidden'
    ? '请使用管理员账号进入工作空间。'
    : '请检查地址，或返回工作空间继续操作。',
)
</script>

<template>
  <main class="status-page">
    <el-result
      :icon="kind === 'forbidden' ? 'warning' : 'info'"
      :title="title"
      :sub-title="description"
    >
      <template #extra
        ><RouterLink :to="auth.hasSession.value ? '/users' : '/login'"
          ><el-button type="primary">{{
            auth.hasSession.value ? '返回工作空间' : '返回登录'
          }}</el-button></RouterLink
        ></template
      >
    </el-result>
  </main>
</template>
