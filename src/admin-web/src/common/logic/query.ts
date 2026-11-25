import { ApiError } from '../data/http'
import type { PostQuery, UserQuery } from '../data/models'

export interface PostFilters {
  keyword: string
  authorId: string
  from: string
  to: string
  tag: string
}

/*
行为：将搜索草稿转为用户查询参数。
实现：去除关键词首尾空白并省略空条件，分页参数由页面交互管理。
*/
export function buildUserQuery(keyword: string, page = 1, pageSize = 10): UserQuery {
  return { keyword: keyword.trim() || undefined, page, pageSize }
}

/*
行为：检查自然日格式和可达日期。
实现：严格解析年月日，比较 UTC 组成部分以拒绝日期自动溢出。
*/
function checkDate(date: string): void {
  if (!date) return
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    throw new ApiError('日期格式须为 YYYY-MM-DD', 400, 'VALIDATION_ERROR')
  }
  const [year = 0, month = 0, day = 0] = date.split('-').map(Number)
  const parsed = new Date(Date.UTC(year, month - 1, day))
  if (
    year < 1000 ||
    year > 9998 ||
    parsed.getUTCFullYear() !== year ||
    parsed.getUTCMonth() !== month - 1 ||
    parsed.getUTCDate() !== day
  ) {
    throw new ApiError('日期无效，请重新选择', 400, 'VALIDATION_ERROR')
  }
}

/*
行为：建立与 M02 实现一致的动态组合查询。
实现：校验作者、自然日和 Unicode 字段长度，省略空条件且保持日期原样发送。
*/
export function buildPostQuery(filters: PostFilters, page = 1, pageSize = 10): PostQuery {
  const keyword = filters.keyword.trim()
  const tag = filters.tag.trim()
  const authorText = filters.authorId.trim()
  const authorId = authorText ? Number(authorText) : undefined
  if (
    authorId !== undefined &&
    (!/^\d+$/.test(authorText) || !Number.isSafeInteger(authorId) || authorId < 1)
  ) {
    throw new ApiError('作者 ID 必须为正整数', 400, 'VALIDATION_ERROR')
  }
  if (Array.from(keyword).length > 100) {
    throw new ApiError('关键词最多 100 个字符', 400, 'VALIDATION_ERROR')
  }
  if (Array.from(tag).length > 20) {
    throw new ApiError('标签最多 20 个字符', 400, 'VALIDATION_ERROR')
  }
  checkDate(filters.from)
  checkDate(filters.to)
  if (filters.from && filters.to && filters.from > filters.to) {
    throw new ApiError('开始日期不能晚于结束日期', 400, 'VALIDATION_ERROR')
  }
  return {
    keyword: keyword || undefined,
    tag: tag || undefined,
    authorId,
    from: filters.from || undefined,
    to: filters.to || undefined,
    page,
    pageSize,
  }
}

/*
行为：按北京时间展示接口时间。
实现：显式使用 Asia/Shanghai，避免客户端所在地改变日期显示。
*/
export function formatTime(timestamp: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(timestamp))
}
