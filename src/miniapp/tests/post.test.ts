import test from 'node:test'
import assert from 'node:assert/strict'
import { mediaSummary, normalizeTags, postExcerpt } from '../src/utils/post.ts'

test('postExcerpt 折叠换行与多余空白', () => {
  assert.equal(postExcerpt('  你好\n\n 世界  ', 10), '你好 世界')
  assert.equal(postExcerpt(null), '')
  assert.equal(postExcerpt('   '), '')
})

test('postExcerpt 超长按码点截断并加省略号', () => {
  assert.equal(postExcerpt('abcdef', 3), 'abc…')
  assert.equal(postExcerpt('abc', 3), 'abc')
  const emoji = postExcerpt('😀😀😀😀😀', 2)
  assert.equal(emoji, '😀😀…')
})

test('mediaSummary 统计图片与视频', () => {
  const images = Array.from({ length: 4 }, (_, i) => ({ id: i + 1, type: 'IMAGE' as const, url: '/x' }))
  const summary = mediaSummary(images)
  assert.equal(summary.total, 4)
  assert.equal(summary.imageCount, 4)
  assert.equal(summary.videoCount, 0)
  assert.equal(summary.hasVideo, false)
  assert.equal(summary.previewCount, 3)
  assert.equal(summary.extraCount, 1)

  const video = mediaSummary([{ id: 9, type: 'VIDEO', url: '/v' }])
  assert.equal(video.hasVideo, true)
  assert.equal(video.extraCount, 0)

  const none = mediaSummary([])
  assert.equal(none.total, 0)
  assert.equal(none.hasVideo, false)
  assert.equal(mediaSummary(null).total, 0)
})

test('normalizeTags 去空白、去重并限制 5 个', () => {
  assert.deepEqual(normalizeTags([' 校园 ', '校园', '', '周末']), ['校园', '周末'])
  assert.deepEqual(normalizeTags(['1', '2', '3', '4', '5', '6']), ['1', '2', '3', '4', '5'])
  assert.deepEqual(normalizeTags(null), [])
})
