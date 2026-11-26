import { describe, expect, it } from 'vitest'
import { POST_ENDPOINTS, requirePostId } from '../src/api/posts'
import { MEDIA_ENDPOINTS } from '../src/api/media'
import { INTERACTION_ENDPOINTS } from '../src/api/interactions'

/**
 * 业务接口路径与 specs/contracts/api.md 的端点清单逐条对齐。
 * 改路径必须同时改契约文档，否则这里会先失败。
 */
describe('动态接口路径与 api.md 一致', () => {
  it('列表、详情、创建、编辑、删除、本人列表', () => {
    expect(POST_ENDPOINTS.list).toBe('/posts')
    expect(POST_ENDPOINTS.detail(5)).toBe('/posts/5')
    expect(POST_ENDPOINTS.create).toBe('/posts')
    expect(POST_ENDPOINTS.patch(5)).toBe('/posts/5')
    expect(POST_ENDPOINTS.remove(5)).toBe('/posts/5')
    expect(POST_ENDPOINTS.mine).toBe('/users/me/posts')
  })
})

describe('媒体接口路径与 api.md 一致', () => {
  it('上传与内容读取', () => {
    expect(MEDIA_ENDPOINTS.upload).toBe('/media')
    expect(MEDIA_ENDPOINTS.content(33)).toBe('/media/33/content')
  })
})

describe('互动接口路径与 api.md 一致', () => {
  it('评分与评论', () => {
    expect(INTERACTION_ENDPOINTS.rating(9)).toBe('/posts/9/rating')
    expect(INTERACTION_ENDPOINTS.comments(9)).toBe('/posts/9/comments')
  })
})

describe('requirePostId', () => {
  it('非正整数直接报错，避免拼出 /posts/undefined', () => {
    expect(requirePostId(1)).toBe(1)
    expect(() => requirePostId(0)).toThrow('postId 必须是正整数')
    expect(() => requirePostId(-1)).toThrow('postId 必须是正整数')
    expect(() => requirePostId(1.5)).toThrow('postId 必须是正整数')
    expect(() => requirePostId(Number.NaN)).toThrow('postId 必须是正整数')
  })
})
