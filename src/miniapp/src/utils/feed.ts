import type { PageResult } from '../api/types.ts'

/** 列表分页状态：page 为已加载到的页码，初始 0。 */
export interface FeedState<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
  hasMore: boolean
}

export function createFeed<T>(pageSize: number): FeedState<T> {
  return { items: [], page: 0, pageSize, total: 0, hasMore: true }
}

/**
 * 合并一页结果：
 * - 按 id 去重，避免分页边界或重复点击造成重复卡片；
 * - hasMore 只在“已加载数 < 总数”且本页非空时为真，防止死循环；
 * - 保持服务端返回顺序（发布时间倒序）。
 */
export function applyPage<T extends { id: number }>(
  state: FeedState<T>,
  result: PageResult<T>,
): FeedState<T> {
  const items = Array.isArray(result?.items) ? result.items : []
  const seen = new Set<number>()
  for (const item of state.items) seen.add(item.id)

  const merged = state.items.slice()
  for (const item of items) {
    if (!item || seen.has(item.id)) continue
    seen.add(item.id)
    merged.push(item)
  }

  const total = typeof result?.total === 'number' ? result.total : merged.length
  const page = typeof result?.page === 'number' && result.page > 0 ? result.page : state.page + 1

  return {
    items: merged,
    page,
    pageSize: state.pageSize,
    total,
    hasMore: merged.length < total && items.length > 0,
  }
}

/** 首屏加载完成且没有任何数据 → 展示空状态。 */
export function isFeedEmpty<T>(state: FeedState<T>): boolean {
  return state.items.length === 0
}
