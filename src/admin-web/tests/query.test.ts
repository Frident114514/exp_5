import { effectScope, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '../src/common/data/http'
import type { PageResult, UserQuery } from '../src/common/data/models'
import { buildPostQuery, buildUserQuery } from '../src/common/logic/query'
import { usePagedQuery } from '../src/common/logic/usePagedQuery'

describe('查询条件与分页状态', () => {
  it('保留北京时间日期且按 Unicode 码点校验关键词', () => {
    const query = buildPostQuery({
      keyword: ' 校园 ',
      authorId: '7',
      from: '2026-11-25',
      to: '2026-11-25',
      tag: ' 学习 ',
    })
    expect(query).toEqual({
      keyword: '校园',
      authorId: 7,
      from: '2026-11-25',
      to: '2026-11-25',
      tag: '学习',
      page: 1,
      pageSize: 10,
    })
    expect(
      buildPostQuery({ keyword: '😀'.repeat(100), authorId: '', from: '', to: '', tag: '' })
        .keyword,
    ).toHaveLength(200)
    expect(() =>
      buildPostQuery({ keyword: '😀'.repeat(101), authorId: '', from: '', to: '', tag: '' }),
    ).toThrow('最多 100')
  })

  it.each([
    { authorId: '-1', from: '', to: '' },
    { authorId: '1.5', from: '', to: '' },
    { authorId: '', from: '2026-02-30', to: '' },
    { authorId: '', from: '2026-11-25', to: '2026-11-20' },
  ])('拒绝无效作者或自然日 $authorId $from $to', (filters) => {
    expect(() => buildPostQuery({ keyword: '', tag: '', ...filters })).toThrow()
  })

  it('较早请求晚返回时只保留最新条件的结果', async () => {
    const scope = effectScope()
    const sessionVersion = ref(1)
    const resolvers: ((page: PageResult<string>) => void)[] = []
    const load = vi.fn(
      (_query: UserQuery, _signal: AbortSignal) =>
        new Promise<PageResult<string>>((resolve) => {
          resolvers.push(resolve)
        }),
    )
    const list = scope.run(() => usePagedQuery(buildUserQuery(''), load, sessionVersion))!
    const first = list.runQuery(buildUserQuery('旧条件'))
    const second = list.runQuery(buildUserQuery('新条件'))
    resolvers[1]?.({ items: ['新结果'], page: 1, pageSize: 10, total: 1 })
    await second
    resolvers[0]?.({ items: ['旧结果'], page: 1, pageSize: 10, total: 1 })
    await first
    expect(list.result.value?.items).toEqual(['新结果'])
    expect(list.query.value.keyword).toBe('新条件')
    expect(load.mock.calls[0]?.[1].aborted).toBe(true)
    scope.stop()
  })

  it('翻页保留已提交条件，修改页大小回第一页', async () => {
    const scope = effectScope()
    const load = vi.fn(async (query: UserQuery) => ({ ...query, items: ['用户'], total: 50 }))
    const list = scope.run(() =>
      usePagedQuery<string, UserQuery>(buildUserQuery('校园'), load, ref(1)),
    )!
    await list.changePage(3)
    expect(list.query.value).toEqual({ keyword: '校园', page: 3, pageSize: 10 })
    await list.changePageSize(20)
    expect(list.query.value).toEqual({ keyword: '校园', page: 1, pageSize: 20 })
    scope.stop()
  })

  it('网络失败保留旧结果，403 清除管理数据', async () => {
    const scope = effectScope()
    const load = vi
      .fn()
      .mockResolvedValueOnce({ items: ['旧用户'], page: 1, pageSize: 10, total: 1 })
      .mockRejectedValueOnce(new ApiError('连接失败'))
      .mockRejectedValueOnce(new ApiError('无权限', 403, 'FORBIDDEN'))
    const list = scope.run(() =>
      usePagedQuery<string, UserQuery>(buildUserQuery(''), load, ref(1)),
    )!
    await list.runQuery()
    await list.runQuery()
    expect(list.result.value?.items).toEqual(['旧用户'])
    expect(list.error.value).toBe('连接失败')
    await list.runQuery()
    expect(list.result.value).toBeNull()
    expect(list.errorStatus.value).toBe(403)
    scope.stop()
  })

  it('注销会话后取消查询并忽略其返回', async () => {
    const scope = effectScope()
    const version = ref(1)
    let resolvePage: (page: PageResult<string>) => void = () => undefined
    const load = vi.fn(
      () =>
        new Promise<PageResult<string>>((resolve) => {
          resolvePage = resolve
        }),
    )
    const list = scope.run(() =>
      usePagedQuery<string, UserQuery>(buildUserQuery(''), load, version),
    )!
    const pending = list.runQuery()
    version.value += 1
    resolvePage({ items: ['过期结果'], page: 1, pageSize: 10, total: 1 })
    await pending
    expect(list.result.value).toBeNull()
    expect(list.isLoading.value).toBe(false)
    scope.stop()
  })
})
