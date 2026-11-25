<script setup lang="ts">
import { Document, Refresh, Search } from '@element-plus/icons-vue'
import { onMounted, reactive, ref } from 'vue'

import { api, auth } from '../../../app/services'
import ListPanel from '../../../common/components/ListPanel.vue'
import { getErrorMessage } from '../../../common/data/http'
import type { PostQuery, PostRow } from '../../../common/data/models'
import { buildPostQuery, formatTime } from '../../../common/logic/query'
import type { PostFilters } from '../../../common/logic/query'
import { usePagedQuery } from '../../../common/logic/usePagedQuery'

const emptyFilters: PostFilters = { keyword: '', authorId: '', from: '', to: '', tag: '' }
const filters = reactive<PostFilters>({ ...emptyFilters })
const filterError = ref('')
const list = usePagedQuery<PostRow, PostQuery>(
  buildPostQuery(emptyFilters),
  api.listPosts,
  auth.version,
)
const { query, result, isLoading, error, errorStatus } = list

onMounted(() => void list.runQuery())

/*
行为：校验并提交动态组合筛选。
实现：无效条件只提示不发请求，合法条件从第一页加载。
*/
async function searchPosts(): Promise<void> {
  filterError.value = ''
  try {
    const nextQuery = buildPostQuery(filters, 1, query.value.pageSize)
    await list.runQuery(nextQuery)
  } catch (failure) {
    filterError.value = getErrorMessage(failure)
  }
}

/*
行为：清空动态筛选并重新加载第一页。
实现：恢复表单字段后使用统一提交流程。
*/
async function resetPosts(): Promise<void> {
  Object.assign(filters, emptyFilters)
  await searchPosts()
}
</script>

<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">CONTENT DIRECTORY</span>
      <h1>动态管理</h1>
      <p>按作者、日期和标签查找分享内容。</p>
    </div>
    <span class="page-icon"
      ><el-icon><Document /></el-icon
    ></span>
  </div>
  <section class="filter-panel" aria-label="动态查询条件">
    <el-form label-position="top" class="post-filter-form" @submit.prevent="searchPosts">
      <el-form-item label="正文关键词"
        ><el-input
          v-model="filters.keyword"
          placeholder="搜索动态正文"
          clearable
          :prefix-icon="Search"
      /></el-form-item>
      <el-form-item label="作者 ID"
        ><el-input v-model="filters.authorId" placeholder="例如 2" inputmode="numeric" clearable
      /></el-form-item>
      <el-form-item label="标签"
        ><el-input v-model="filters.tag" placeholder="输入一个标签" clearable
      /></el-form-item>
      <el-form-item label="开始日期"
        ><el-date-picker
          :model-value="filters.from"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择开始日期"
          @update:model-value="filters.from = $event || ''"
      /></el-form-item>
      <el-form-item label="结束日期"
        ><el-date-picker
          :model-value="filters.to"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择结束日期"
          @update:model-value="filters.to = $event || ''"
      /></el-form-item>
      <div class="filter-bottom">
        <span>日期按北京时间计算，包含起止日。</span>
        <div class="filter-actions">
          <el-button type="primary" native-type="submit" :icon="Search">查询</el-button
          ><el-button :icon="Refresh" @click="resetPosts">重置</el-button>
        </div>
      </div>
    </el-form>
    <el-alert
      v-if="filterError"
      :title="filterError"
      class="filter-error"
      type="error"
      :closable="false"
      show-icon
    />
  </section>
  <ListPanel
    title="动态列表"
    subtitle="已发布且当前可见的动态"
    :loading="isLoading"
    :error="error"
    :error-status="errorStatus"
    :has-result="result !== null"
    :row-count="result?.items.length || 0"
    :total="result?.total || 0"
    :page="result?.page || query.page"
    :page-size="result?.pageSize || query.pageSize"
    @retry="list.runQuery()"
    @change-page="list.changePage"
    @change-page-size="list.changePageSize"
  >
    <el-table :data="result?.items" row-key="id" class="management-table">
      <el-table-column prop="id" label="ID" width="78" />
      <el-table-column label="作者" min-width="130"
        ><template #default="{ row }"
          ><div class="author-cell">
            <strong>{{ row.author.nickname }}</strong><span>ID {{ row.author.id }}</span>
          </div></template
        ></el-table-column
      >
      <el-table-column label="动态内容" min-width="260"
        ><template #default="{ row }"
          ><p class="post-excerpt">{{ row.text || '仅含媒体的动态' }}</p>
          <span class="media-summary">{{
            row.media.length ? `${row.media.length} 个媒体` : '纯文字'
          }}</span></template
        ></el-table-column
      >
      <el-table-column label="标签" min-width="155"
        ><template #default="{ row }"
          ><div class="tag-list">
            <el-tag v-for="tag in row.tags" :key="tag" type="info" effect="plain" size="small">{{
              tag
            }}</el-tag><span v-if="!row.tags.length" class="muted">—</span>
          </div></template
        ></el-table-column
      >
      <el-table-column label="发布时间 · 北京时间" min-width="180"
        ><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column
      >
    </el-table>
  </ListPanel>
</template>
