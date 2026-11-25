<script setup lang="ts">
defineProps<{
  title: string
  subtitle: string
  loading: boolean
  error: string
  errorStatus: number
  hasResult: boolean
  rowCount: number
  total: number
  page: number
  pageSize: number
}>()

defineEmits<{
  retry: []
  changePage: [page: number]
  changePageSize: [pageSize: number]
}>()
</script>

<template>
  <section class="list-panel" :aria-busy="loading">
    <div class="panel-heading">
      <div>
        <h2>{{ title }}</h2>
        <p>{{ subtitle }}</p>
      </div>
      <span v-if="hasResult" class="record-count">{{ total }} 条记录</span>
    </div>
    <div v-if="error" class="query-feedback" role="alert">
      <el-alert
        :title="error"
        :description="hasResult ? '刷新失败，当前为上次查询结果。' : undefined"
        type="error"
        :closable="false"
        show-icon
      />
      <el-button
        v-if="errorStatus !== 401 && errorStatus !== 403"
        :disabled="loading"
        @click="$emit('retry')"
        >重试</el-button
      >
    </div>
    <div v-loading="loading" class="table-area" element-loading-text="正在查询…">
      <slot v-if="hasResult && rowCount > 0" />
      <el-empty
        v-else-if="!loading && hasResult"
        description="没有找到符合条件的记录"
        :image-size="100"
      />
      <div v-else class="table-placeholder">
        <span>{{ loading ? '正在获取查询结果…' : '查询未完成，请根据上方提示重试。' }}</span>
      </div>
    </div>
    <div v-if="hasResult" class="panel-pagination">
      <span class="page-summary">共 {{ total }} 条 · 第 {{ page }} 页</span>
      <el-pagination
        background
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        :disabled="loading || !!error"
        layout="sizes, prev, pager, next"
        @update:current-page="$emit('changePage', $event)"
        @update:page-size="$emit('changePageSize', $event)"
      />
    </div>
  </section>
</template>
