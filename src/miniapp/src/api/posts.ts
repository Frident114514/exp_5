/**
 * 动态接口 · 维护人：M05（接口层），页面由 M06/M07 实现
 *
 * 对应 specs/contracts/api.md 的 U02/U03/U04/U05 行与 M02 的 posts-module.md。
 * M06/M07 只需调用这里的函数，不要自己拼路径或调 uni.request。
 * 字段变更请先改契约文档，再改这里和 api/types.ts。
 */

import { del, get, patch, post } from './http'
import type { PageQuery, PageResult, Post, PostListQuery, PostPatchPayload, PostPayload } from './types'

export const POST_ENDPOINTS = {
  list: '/posts',
  detail: (postId: number) => `/posts/${postId}`,
  create: '/posts',
  patch: (postId: number) => `/posts/${postId}`,
  remove: (postId: number) => `/posts/${postId}`,
  mine: '/users/me/posts',
} as const

/** `GET /posts`：公开列表，支持日期与单标签筛选、分页，按发布时间倒序。 */
export function listPosts(query: PostListQuery = {}): Promise<PageResult<Post>> {
  return get<PageResult<Post>>(POST_ENDPOINTS.list, {
    page: query.page,
    pageSize: query.pageSize,
    from: query.from,
    to: query.to,
    tag: query.tag,
  })
}

/** `GET /posts/{postId}`：公开详情；已删除或不存在返回 404 `NOT_FOUND`。 */
export function getPost(postId: number): Promise<Post> {
  return get<Post>(POST_ENDPOINTS.detail(requirePostId(postId)))
}

/** `POST /posts`：需要登录。文本与媒体至少一项，媒体 1–9 张图或 1 个视频，标签最多 5 个。 */
export function createPost(payload: PostPayload): Promise<Post> {
  return post<Post>(POST_ENDPOINTS.create, payload)
}

/** `PATCH /posts/{postId}`：仅作者。省略的字段保持不变，传空数组表示清空媒体/标签。 */
export function updatePost(postId: number, payload: PostPatchPayload): Promise<Post> {
  return patch<Post>(POST_ENDPOINTS.patch(requirePostId(postId)), payload)
}

/** `DELETE /posts/{postId}`：仅作者；成功后页面应刷新本人列表。 */
export function deletePost(postId: number): Promise<null> {
  return del<null>(POST_ENDPOINTS.remove(requirePostId(postId)))
}

/** `GET /users/me/posts`：需要登录，本人动态分页。 */
export function listMyPosts(query: PageQuery = {}): Promise<PageResult<Post>> {
  return get<PageResult<Post>>(POST_ENDPOINTS.mine, { page: query.page, pageSize: query.pageSize })
}

/** 统一校验动态 ID，避免拼出 `/posts/undefined` 之类的请求。 */
export function requirePostId(postId: number): number {
  if (!Number.isInteger(postId) || postId <= 0) {
    throw new Error(`postId 必须是正整数，收到：${String(postId)}`)
  }
  return postId
}
