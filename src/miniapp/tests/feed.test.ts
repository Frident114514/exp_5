import test from 'node:test'
import assert from 'node:assert/strict'
import { applyPage, createFeed, isFeedEmpty } from '../src/utils/feed.ts'

interface Item {
  id: number
}

const item = (id: number): Item => ({ id })
const page = (ids: number[], pageNo: number, total: number, pageSize = 10) => ({
  items: ids.map(item),
  page: pageNo,
  pageSize,
  total,
})

test('初始状态：无数据、hasMore 为真等待加载', () => {
  const state = createFeed<Item>(10)
  assert.deepEqual(state.items, [])
  assert.equal(state.page, 0)
  assert.equal(state.hasMore, true)
  assert.equal(isFeedEmpty(state), true)
})

test('第一页写入后仍可继续加载', () => {
  const state = applyPage(createFeed<Item>(10), page([1, 2, 3], 1, 12))
  assert.deepEqual(state.items.map((i) => i.id), [1, 2, 3])
  assert.equal(state.page, 1)
  assert.equal(state.total, 12)
  assert.equal(state.hasMore, true)
})

test('第二页追加且保持服务端顺序', () => {
  let state = applyPage(createFeed<Item>(10), page([1, 2, 3], 1, 5))
  state = applyPage(state, page([4, 5], 2, 5))
  assert.deepEqual(state.items.map((i) => i.id), [1, 2, 3, 4, 5])
  assert.equal(state.hasMore, false)
  assert.equal(isFeedEmpty(state), false)
})

test('分页边界重复 id 会被去重', () => {
  let state = applyPage(createFeed<Item>(10), page([1, 2, 3], 1, 4))
  state = applyPage(state, page([3, 4], 2, 4))
  assert.deepEqual(state.items.map((i) => i.id), [1, 2, 3, 4])
  assert.equal(state.hasMore, false)
})

test('空结果：页面应显示空状态且不再请求下一页', () => {
  const state = applyPage(createFeed<Item>(10), page([], 1, 0))
  assert.equal(state.items.length, 0)
  assert.equal(state.hasMore, false)
  assert.equal(isFeedEmpty(state), true)
})

test('返回空页但 total 未变时不会死循环', () => {
  let state = applyPage(createFeed<Item>(10), page([1, 2], 1, 5))
  state = applyPage(state, page([], 2, 5))
  assert.equal(state.items.length, 2)
  assert.equal(state.hasMore, false)
})

test('缺失 total/items 的异常响应不会抛错', () => {
  const broken = { page: 1 } as unknown as { items: never[]; page: number; pageSize: number; total: number }
  const state = applyPage(createFeed<Item>(10), broken)
  assert.equal(state.items.length, 0)
  assert.equal(state.total, 0)
  assert.equal(state.hasMore, false)
})
