import { onScopeDispose, ref, shallowRef, watch } from 'vue'
import type { Ref } from 'vue'

import { ApiError, getErrorMessage } from '../data/http'
import type { PageResult, UserQuery } from '../data/models'

/*
行为：管理一个列表的已提交条件、分页与请求反馈。
实现：取消旧请求并检查请求序号和会话版本，失败保留上次结果且记录是否未更新。
*/
export function usePagedQuery<T, Q extends UserQuery>(
  initialQuery: Q,
  loadPage: (query: Q, signal: AbortSignal) => Promise<PageResult<T>>,
  sessionVersion: Readonly<Ref<number>>,
) {
  const query = shallowRef<Q>(initialQuery)
  const result = shallowRef<PageResult<T> | null>(null)
  const isLoading = ref(false)
  const error = ref('')
  const errorStatus = ref(0)
  let requestId = 0
  let controller: AbortController | null = null

  /*
  行为：停止本列表请求并使晚到响应失效。
  实现：递增请求序号后取消控制器，统一结束加载状态。
  */
  function cancelQuery(): void {
    requestId += 1
    controller?.abort()
    controller = null
    isLoading.value = false
  }

  /*
  行为：提交查询并更新当前列表结果。
  实现：仅最新请求可提交状态；401 或 403 清空管理数据，其他失败保留旧结果。
  */
  async function runQuery(nextQuery: Q = query.value): Promise<void> {
    cancelQuery()
    query.value = { ...nextQuery }
    const currentId = requestId
    const currentVersion = sessionVersion.value
    const currentController = new AbortController()
    controller = currentController
    isLoading.value = true
    error.value = ''
    errorStatus.value = 0
    try {
      const page = await loadPage(query.value, currentController.signal)
      if (currentId !== requestId || currentVersion !== sessionVersion.value) return
      result.value = page
    } catch (failure) {
      if (
        currentController.signal.aborted ||
        currentId !== requestId ||
        currentVersion !== sessionVersion.value
      )
        return
      error.value = getErrorMessage(failure)
      errorStatus.value = failure instanceof ApiError ? failure.status : 0
      if (errorStatus.value === 401 || errorStatus.value === 403) result.value = null
    } finally {
      if (currentId === requestId && currentVersion === sessionVersion.value) {
        isLoading.value = false
        controller = null
      }
    }
  }

  /*
  行为：按已提交条件切换页码。
  实现：只替换 page，输入框尚未提交的草稿不进入请求。
  */
  async function changePage(page: number): Promise<void> {
    await runQuery({ ...query.value, page })
  }

  /*
  行为：修改每页数量并回到第一页。
  实现：保留已提交过滤条件后重新查询。
  */
  async function changePageSize(pageSize: number): Promise<void> {
    await runQuery({ ...query.value, page: 1, pageSize })
  }

  watch(
    sessionVersion,
    () => {
      cancelQuery()
      result.value = null
    },
    { flush: 'sync' },
  )
  onScopeDispose(cancelQuery)

  return { query, result, isLoading, error, errorStatus, runQuery, changePage, changePageSize }
}
