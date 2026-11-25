import { createClient } from './client.ts'
import type { ApiClient } from './client.ts'
import { API_BASE_URL, DEFAULT_PAGE_SIZE, REQUEST_TIMEOUT } from '../config.ts'
import type { PageResult, PostDetail, PostSummary } from './types.ts'

export interface PostListQuery {
  page?: number
  pageSize?: number
  /** 后续阶段使用：YYYY-MM-DD 日期范围与单标签，第一周不传。 */
  from?: string
  to?: string
  tag?: string
}

export interface PostsApi {
  list(query?: PostListQuery): Promise<PageResult<PostSummary>>
  detail(postId: number): Promise<PostDetail>
}

/** 契约限制：page 从 1 开始，pageSize 默认 10、最大 50（contracts/api.md 第 9 行）。 */
export function normalizePageQuery(query: PostListQuery = {}): { page: number; pageSize: number } {
  const rawPage = Number(query.page ?? 1)
  const rawPageSize = Number(query.pageSize ?? DEFAULT_PAGE_SIZE)
  const page = Number.isFinite(rawPage) && rawPage >= 1 ? Math.floor(rawPage) : 1
  const pageSize = Number.isFinite(rawPageSize)
    ? Math.min(50, Math.max(1, Math.floor(rawPageSize)))
    : DEFAULT_PAGE_SIZE
  return { page, pageSize }
}

export function createPostsApi(client: ApiClient): PostsApi {
  return {
    list(query: PostListQuery = {}) {
      const { page, pageSize } = normalizePageQuery(query)
      return client.get<PageResult<PostSummary>>('/posts', {
        query: {
          page,
          pageSize,
          from: query.from,
          to: query.to,
          tag: query.tag,
        },
      })
    },
    detail(postId: number) {
      return client.get<PostDetail>('/posts/' + postId)
    },
  }
}

const defaultClient = createClient({ baseUrl: API_BASE_URL, timeout: REQUEST_TIMEOUT })

export const postsApi: PostsApi = createPostsApi(defaultClient)