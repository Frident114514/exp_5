<script setup lang="ts">
import { Refresh, Search, User } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'

import { api, auth } from '../../../app/services'
import ListPanel from '../../../common/components/ListPanel.vue'
import type { UserQuery, UserRow } from '../../../common/data/models'
import { buildUserQuery, formatTime } from '../../../common/logic/query'
import { usePagedQuery } from '../../../common/logic/usePagedQuery'

const keyword = ref('')
const list = usePagedQuery<UserRow, UserQuery>(buildUserQuery(''), api.listUsers, auth.version)
const { query, result, isLoading, error, errorStatus } = list

onMounted(() => void list.runQuery())

/*
行为：应用关键词草稿并从第一页查询用户。
实现：构造已提交条件，沿用用户选择的每页条数。
*/
async function searchUsers(): Promise<void> {
  await list.runQuery(buildUserQuery(keyword.value, 1, query.value.pageSize))
}

/*
行为：清除关键词并重新加载第一页。
实现：重置表单后沿用同一个查询入口。
*/
async function resetUsers(): Promise<void> {
  keyword.value = ''
  await searchUsers()
}
</script>

<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">USER DIRECTORY</span>
      <h1>用户管理</h1>
      <p>查询用户资料，查看账号角色与状态。</p>
    </div>
    <span class="page-icon"
      ><el-icon><User /></el-icon
    ></span>
  </div>
  <section class="filter-panel" aria-label="用户查询条件">
    <el-form label-position="top" class="user-filter-form" @submit.prevent="searchUsers">
      <el-form-item label="关键词"
        ><el-input v-model="keyword" placeholder="输入账号或昵称" clearable :prefix-icon="Search"
      /></el-form-item>
      <div class="filter-actions">
        <el-button type="primary" native-type="submit" :icon="Search">查询</el-button
        ><el-button :icon="Refresh" @click="resetUsers">重置</el-button>
      </div>
    </el-form>
  </section>
  <ListPanel
    title="用户列表"
    subtitle="账号资料与访问角色"
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
      <el-table-column label="用户" min-width="180"
        ><template #default="{ row }"
          ><div class="person-cell">
            <span class="table-avatar">{{ row.nickname.slice(0, 1) }}</span>
            <div>
              <strong>{{ row.nickname }}</strong><span>{{ row.account }}</span>
            </div>
          </div></template
        ></el-table-column
      >
      <el-table-column label="角色" width="130"
        ><template #default="{ row }"
          ><el-tag :type="row.role === 'ADMIN' ? 'success' : 'info'" effect="plain">{{
            row.role === 'ADMIN' ? '管理员' : '普通用户'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="状态" width="120"
        ><template #default="{ row }"
          ><span class="user-status" :class="{ disabled: row.status === 'DISABLED' }"
            ><i></i>{{ row.status === 'ACTIVE' ? '正常' : '已禁用' }}</span
          ></template
        ></el-table-column
      >
      <el-table-column label="注册时间 · 北京时间" min-width="180"
        ><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column
      >
    </el-table>
  </ListPanel>
</template>
