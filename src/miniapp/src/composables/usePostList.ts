import { computed, ref } from 'vue'
import { postsApi } from '../api/posts.ts'
import type { PostListQuery } from '../api/posts.ts'
import { describeError } from '../api/errors.ts'
import { applyPage, createFeed, isFeedEmpty } from '../utils/feed.ts'
import type { FeedState } from '../utils/feed.ts'
import type { PostSummary } from '../api/types.ts'
import { DEFAULT_PAGE_SIZE } from '../config/env.ts'

export type ListStatus = 'idle' | 'loading' | 'ready' | 'error'

/**
 * 广场列表的加载状态机（第一周只含首次加载、下拉刷新、触底分页）。
 *
 * requestToken 用于丢弃过期响应：刷新或重新加载时自增，
 * 旧请求返回后即使成功也不会覆盖新数据（对应 M07-03 的“旧请求不覆盖新筛选”）。
 */
export function usePostList(pageSize: number = DEFAULT_PAGE_SIZE) {
  const feed = ref<FeedState<PostSummary>>(createFeed<PostSummary>(pageSize))
  const status = ref<ListStatus>('loading')
  const errorMessage = ref('')
  const loadingMore = ref(false)
  const loadMoreError = ref('')
  let requestToken = 0

  const items = computed(() => feed.value.items)
  const total = computed(() => feed.value.total)
  const hasMore = computed(() => feed.value.hasMore)
  const isEmpty = computed(() => status.value === 'ready' && isFeedEmpty(feed.value))
  const isFirstLoading = computed(() => status.value === 'loading' && feed.value.items.length === 0)

  async function loadFirstPage(query: PostListQuery = {}): Promise<void> {
    const token = ++requestToken
    status.value = 'loading'
    errorMessage.value = ''
    loadMoreError.value = ''
    loadingMore.value = false
    try {
      const result = await postsApi.list({ ...query, page: 1, pageSize })
      if (token !== requestToken) return
      feed.value = applyPage(createFeed<PostSummary>(pageSize), result)
      status.value = 'ready'
    } catch (error) {
      if (token !== requestToken) return
      errorMessage.value = describeError(error)
      status.value = 'error'
    }
  }

  async function loadNextPage(): Promise<void> {
    if (loadingMore.value || !feed.value.hasMore || status.value !== 'ready') return
    const token = requestToken
    loadingMore.value = true
    loadMoreError.value = ''
    try {
      const result = await postsApi.list({
        page: feed.value.page + 1,
        pageSize: feed.value.pageSize,
      })
      if (token !== requestToken) return
      feed.value = applyPage(feed.value, result)
    } catch (error) {
      if (token !== requestToken) return
      loadMoreError.value = describeError(error)
    } finally {
      loadingMore.value = false
    }
  }

  async function refresh(): Promise<void> {
    await loadFirstPage()
  }

  return {
    feed,
    status,
    items,
    total,
    hasMore,
    isEmpty,
    isFirstLoading,
    errorMessage,
    loadingMore,
    loadMoreError,
    loadFirstPage,
    loadNextPage,
    refresh,
  }
}
