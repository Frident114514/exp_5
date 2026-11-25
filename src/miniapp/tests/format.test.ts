import test from 'node:test'
import assert from 'node:assert/strict'
import { formatDate, formatDateTime, formatRating, formatRelativeTime, ratingBadge } from '../src/utils/format.ts'

const NOW = new Date('2026-11-17T12:00:00').getTime()

test('formatDateTime 输出 YYYY-MM-DD HH:mm', () => {
  assert.equal(formatDateTime('2026-11-17T10:05:00'), '2026-11-17 10:05')
  assert.equal(formatDateTime(new Date(2026, 0, 2, 3, 4)), '2026-01-02 03:04')
})

test('formatDateTime 对非法输入返回空串，不产生 Invalid Date', () => {
  assert.equal(formatDateTime(''), '')
  assert.equal(formatDateTime(null), '')
  assert.equal(formatDateTime(undefined), '')
  assert.equal(formatDateTime('not-a-date'), '')
})

test('formatDate 只返回日期', () => {
  assert.equal(formatDate('2026-11-17T23:59:00'), '2026-11-17')
  assert.equal(formatDate('bad'), '')
})

test('formatRelativeTime 按区间返回中文相对时间', () => {
  assert.equal(formatRelativeTime(NOW - 30 * 1000, NOW), '刚刚')
  assert.equal(formatRelativeTime(NOW - 5 * 60 * 1000, NOW), '5 分钟前')
  assert.equal(formatRelativeTime(NOW - 3 * 60 * 60 * 1000, NOW), '3 小时前')
  assert.equal(formatRelativeTime(NOW - 2 * 24 * 60 * 60 * 1000, NOW), '2 天前')
  assert.equal(formatRelativeTime(NOW - 10 * 24 * 60 * 60 * 1000, NOW), '2026-11-07')
  // 时钟误差导致的“未来时间”不显示负数
  assert.equal(formatRelativeTime(NOW + 5000, NOW), '刚刚')
})

test('formatRating 有评分时保留 1 位小数与人数', () => {
  const text = formatRating({ average: 4.5, count: 2 })
  assert.equal(text.available, true)
  assert.equal(text.averageText, '4.5')
  assert.equal(text.countText, '2 人评分')
  assert.equal(text.stars, '★★★★★')
})

test('formatRating 无评分时不伪造 0 分', () => {
  for (const rating of [{ average: null, count: 0 }, null, undefined]) {
    const text = formatRating(rating)
    assert.equal(text.available, false)
    assert.equal(text.averageText, '暂无评分')
    assert.equal(text.stars, '')
  }
  assert.equal(ratingBadge({ average: null, count: 0 }), '暂无评分')
  assert.equal(ratingBadge({ average: 3.04, count: 1 }), '3.0 分')
})

test('formatRating 星级按四舍五入取整', () => {
  assert.equal(formatRating({ average: 3.4, count: 1 }).stars, '★★★☆☆')
  assert.equal(formatRating({ average: 3.6, count: 1 }).stars, '★★★★☆')
  assert.equal(formatRating({ average: 5, count: 9 }).stars, '★★★★★')
})
