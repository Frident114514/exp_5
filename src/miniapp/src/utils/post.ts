import type { MediaItem, MediaType, PostSummary } from '../api/types.ts'

export function isVideoType(type: MediaType | string | null | undefined): boolean {
  return type === 'VIDEO'
}

export interface MediaSummary {
  total: number
  imageCount: number
  videoCount: number
  hasVideo: boolean
  /** 卡片上最多展示 3 个占位，超出部分显示 +N。 */
  previewCount: number
  extraCount: number
}

export function mediaSummary(media: MediaItem[] | null | undefined, limit = 3): MediaSummary {
  const list = Array.isArray(media) ? media : []
  const imageCount = list.filter((item) => item && item.type === 'IMAGE').length
  const videoCount = list.filter((item) => item && item.type === 'VIDEO').length
  const previewCount = Math.min(list.length, limit)
  return {
    total: list.length,
    imageCount,
    videoCount,
    hasVideo: videoCount > 0,
    previewCount,
    extraCount: list.length - previewCount,
  }
}

/**
 * 卡片摘要：折叠空白与换行，按码点截断，避免把 emoji 截成乱码。
 */
export function postExcerpt(text: string | null | undefined, max = 60): string {
  const normalized = (text ?? '').replace(/\s+/g, ' ').trim()
  if (!normalized) return ''
  const chars = Array.from(normalized)
  if (chars.length <= max) return normalized
  return chars.slice(0, max).join('') + '…'
}

/** 标签规范化为展示用：去空白、去空项、去重、最多 5 个（契约上限）。 */
export function normalizeTags(tags: string[] | null | undefined, limit = 5): string[] {
  const list = Array.isArray(tags) ? tags : []
  const result: string[] = []
  for (const tag of list) {
    const name = (tag ?? '').trim()
    if (!name || result.includes(name)) continue
    result.push(name)
    if (result.length >= limit) break
  }
  return result
}

/** 列表/详情是否属于同一条动态（用于从卡片跳转详情时复用已加载数据）。 */
export function samePost(a: PostSummary | null, b: PostSummary | null): boolean {
  if (!a || !b) return false
  return a.id === b.id
}
