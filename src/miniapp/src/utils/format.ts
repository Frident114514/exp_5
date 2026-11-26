import type { RatingSummary } from '../api/types.ts'

/**
 * 时间格式化统一按北京时间（Asia/Shanghai）输出，与服务端和 M06 的编辑页一致，
 * 不依赖设备所在时区。接受 ISO 8601 字符串（含 Z 或 +08:00）、时间戳和 Date。
 */
const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

function toDate(input: string | number | Date | null | undefined): Date | null {
  if (input === null || input === undefined || input === '') return null
  const date = input instanceof Date ? input : new Date(input)
  if (Number.isNaN(date.getTime())) return null
  return date
}

/**
 * 2026-11-17 10:00（北京时间）。
 * 详情数据未加载时传入 null/undefined 返回空串，页面不会出现 Invalid Date。
 */
export function formatDateTime(input: string | number | Date | null | undefined): string {
  const date = toDate(input)
  if (!date) return ''
  return dateTimeFormatter.format(date).replace(/\//g, '-')
}

/** 2026-11-17（北京时间） */
export function formatDate(input: string | number | Date | null | undefined): string {
  const text = formatDateTime(input)
  return text ? text.slice(0, 10) : ''
}

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * 卡片上的相对时间：刚刚 / x 分钟前 / x 小时前 / x 天前 / 具体日期。
 * now 可注入，便于单元测试固定时钟。
 */
export function formatRelativeTime(
  input: string | number | Date | null | undefined,
  now: number = Date.now(),
): string {
  const date = toDate(input)
  if (!date) return ''
  const diff = now - date.getTime()
  if (diff < MINUTE) return '刚刚'
  if (diff < HOUR) return Math.floor(diff / MINUTE) + ' 分钟前'
  if (diff < DAY) return Math.floor(diff / HOUR) + ' 小时前'
  if (diff < 7 * DAY) return Math.floor(diff / DAY) + ' 天前'
  return formatDate(date)
}

export interface RatingText {
  available: boolean
  averageText: string
  countText: string
  /** 5 星文本，未评分时为空串（不伪造 0 分）。 */
  stars: string
}

/** 评分摘要文案：无评分时明确显示“暂无评分”，不显示 0 分。 */
export function formatRating(rating: RatingSummary | null | undefined): RatingText {
  const average = rating?.average ?? null
  const count = rating?.count ?? 0
  const available = average !== null && count > 0
  if (!available) {
    return { available: false, averageText: '暂无评分', countText: '0 人评分', stars: '' }
  }
  const filled = Math.max(0, Math.min(5, Math.round(average as number)))
  return {
    available: true,
    averageText: (average as number).toFixed(1),
    countText: count + ' 人评分',
    stars: '★'.repeat(filled) + '☆'.repeat(5 - filled),
  }
}

/** 卡片右上角的评分短文案，如 “4.5 分”。 */
export function ratingBadge(rating: RatingSummary | null | undefined): string {
  const text = formatRating(rating)
  return text.available ? text.averageText + ' 分' : '暂无评分'
}
