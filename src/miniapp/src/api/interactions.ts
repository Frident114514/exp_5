/**
 * 互动接口：评分与评论 · 维护人：M05（接口层），页面由 M07 实现
 *
 * 对应 specs/contracts/api.md 的 U05 行：
 *   - `PUT /posts/{postId}/rating`：1–5 整数，再次提交更新原评分，仅非作者；
 *   - `GET /posts/{postId}/comments`：公开分页；
 *   - `POST /posts/{postId}/comments`：1–500 字符，仅非作者。
 */

import { get, post, put } from './http'
import type {
  Comment,
  CommentPayload,
  PageQuery,
  PageResult,
  RatingPayload,
  RatingResult,
} from './types'
import { requirePostId } from './posts'

export const INTERACTION_ENDPOINTS = {
  rating: (postId: number) => `/posts/${postId}/rating`,
  comments: (postId: number) => `/posts/${postId}/comments`,
} as const

/** 提交或修改本人对某条动态的评分，返回最新平均分与人数。 */
export function ratePost(postId: number, payload: RatingPayload): Promise<RatingResult> {
  return put<RatingResult>(INTERACTION_ENDPOINTS.rating(requirePostId(postId)), payload)
}

/** 公开评论列表，按时间正序分页。 */
export function listComments(postId: number, query: PageQuery = {}): Promise<PageResult<Comment>> {
  return get<PageResult<Comment>>(INTERACTION_ENDPOINTS.comments(requirePostId(postId)), {
    page: query.page,
    pageSize: query.pageSize,
  })
}

/** 发表评论，成功后应刷新评论列表。 */
export function createComment(postId: number, payload: CommentPayload): Promise<Comment> {
  return post<Comment>(INTERACTION_ENDPOINTS.comments(requirePostId(postId)), payload)
}
