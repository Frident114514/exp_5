import { describe, expect, it } from 'vitest'
import { MockPostApi } from '@/api/mockPostApi'
import { PostApiError, type PostAuthor, type PostView } from '@/api/types'

const currentUser: PostAuthor = { id: 6006, nickname: '测试用户' }
const fixedNow = () => new Date('2026-11-26T08:00:00.000Z')

function createApi(posts: PostView[] = []): MockPostApi {
  return new MockPostApi({
    currentUser,
    initialPosts: posts,
    latencyMs: 0,
    now: fixedNow,
  })
}

function otherPost(): PostView {
  return {
    id: 9001,
    author: { id: 7007, nickname: '其他用户' },
    text: '他人的动态',
    media: [],
    tags: [],
    createdAt: '2026-11-26T01:00:00.000Z',
    updatedAt: '2026-11-26T01:00:00.000Z',
    rating: { average: null, count: 0 },
  }
}

describe('MockPostApi 第一周契约行为', () => {
  it('创建后可在本人列表和详情中读取', async () => {
    const api = createApi()
    const created = await api.create({
      text: '  新动态  ',
      mediaIds: [],
      tags: [],
    })

    expect(created.id).toBe(1001)
    const page = await api.mine(1, 5)
    expect(page.total).toBe(1)
    expect(page.items[0]).toMatchObject({
      id: 1001,
      text: '新动态',
      author: currentUser,
    })
    await expect(api.detail(1001)).resolves.toMatchObject({ text: '新动态' })
  })

  it('我的动态按创建时间倒序分页', async () => {
    const api = createApi()
    for (let index = 1; index <= 6; index += 1) {
      await api.create({ text: `动态 ${index}`, mediaIds: [], tags: [] })
    }

    const first = await api.mine(1, 2)
    const third = await api.mine(3, 2)
    expect(first.items.map((post) => post.id)).toEqual([1006, 1005])
    expect(third.items.map((post) => post.id)).toEqual([1002, 1001])
    expect(first.total).toBe(6)
  })

  it('编辑只更新正文并刷新 updatedAt', async () => {
    const api = createApi()
    const created = await api.create({ text: '原文', mediaIds: [], tags: [] })
    const updated = await api.edit(created.id, { text: '修改后' })

    expect(updated.text).toBe('修改后')
    expect(updated.createdAt).toBe(updated.updatedAt)
    expect((await api.detail(created.id)).text).toBe('修改后')
  })

  it('删除后列表和详情均不可见', async () => {
    const api = createApi()
    const created = await api.create({ text: '待删除', mediaIds: [], tags: [] })
    await api.delete(created.id)

    expect((await api.mine(1, 5)).total).toBe(0)
    await expect(api.detail(created.id)).rejects.toMatchObject({
      code: 'NOT_FOUND',
    })
  })

  it('禁止编辑或删除他人动态', async () => {
    const api = createApi([otherPost()])

    await expect(api.edit(9001, { text: '越权修改' })).rejects.toBeInstanceOf(PostApiError)
    await expect(api.edit(9001, { text: '越权修改' })).rejects.toMatchObject({
      code: 'FORBIDDEN',
    })
    await expect(api.delete(9001)).rejects.toMatchObject({
      code: 'FORBIDDEN',
    })
  })

  it('可以模拟一次网络失败，随后重试成功', async () => {
    const api = createApi()
    api.failNextRequest()

    await expect(
      api.create({ text: '失败重试', mediaIds: [], tags: [] }),
    ).rejects.toMatchObject({ code: 'NETWORK_ERROR' })

    await expect(
      api.create({ text: '失败重试', mediaIds: [], tags: [] }),
    ).resolves.toMatchObject({ id: 1001 })
  })

  it('拒绝非法分页参数', async () => {
    const api = createApi()
    await expect(api.mine(0, 5)).rejects.toMatchObject({ code: 'VALIDATION_ERROR' })
    await expect(api.mine(1, 51)).rejects.toMatchObject({ code: 'VALIDATION_ERROR' })
  })

  it('第一周 Mock 拒绝带媒体或标签的请求', async () => {
    const api = createApi()
    await expect(
      api.create({ text: '图片动态', mediaIds: [1], tags: [] }),
    ).rejects.toMatchObject({ code: 'VALIDATION_ERROR' })
    await expect(
      api.create({ text: '标签动态', mediaIds: [], tags: ['校园'] }),
    ).rejects.toMatchObject({ code: 'VALIDATION_ERROR' })
  })
})