import type { PostRow, UserRow } from '../common/data/models'

const users: UserRow[] = Array.from({ length: 26 }, (_, index) => ({
  id: index + 1,
  account: index === 0 ? 'demo_admin' : `student_${String(index).padStart(3, '0')}`,
  nickname:
    index === 0 ? '演示管理员' : ['林晓', '陈雨', '周宁', '李然'][index % 4] + String(index),
  role: index === 0 ? 'ADMIN' : 'USER',
  status: index > 0 && index % 7 === 0 ? 'DISABLED' : 'ACTIVE',
  createdAt: `2026-11-${String(1 + (index % 20)).padStart(2, '0')}T02:00:00Z`,
}))

const posts: PostRow[] = Array.from({ length: 24 }, (_, index): PostRow => ({
  id: 101 + index,
  author: {
    id: 2 + (index % 4),
    nickname: ['林晓', '陈雨', '周宁', '李然'][index % 4] || '演示用户',
  },
  text:
    [
      '校园日记：午后去图书馆，读完了计划中的章节。',
      '记录运动场上的晚霞。',
      '今天的课程笔记与小组讨论。',
    ][index % 3] || '',
  tags: index % 2 === 0 ? ['校园', '日常'] : ['学习'],
  media:
    index % 3 === 0
      ? [{ id: 501 + index, type: 'IMAGE', url: `/api/v1/media/${501 + index}/content` }]
      : [],
  createdAt: `2026-11-${String(20 + (index % 6)).padStart(2, '0')}T04:30:00Z`,
  updatedAt: `2026-11-${String(20 + (index % 6)).padStart(2, '0')}T04:30:00Z`,
  rating: { average: index % 4 === 0 ? null : 4.5, count: index % 4 === 0 ? 0 : 2 },
})).sort((left, right) => right.createdAt.localeCompare(left.createdAt) || right.id - left.id)

/*
行为：生成遵守项目响应信封的演示响应。
实现：将虚构对象序列化为 JSON，并保留调用方指定的 HTTP 状态。
*/
function reply(data: unknown, status = 200, code = 'OK', message = ''): Response {
  return new Response(JSON.stringify({ code, data, message }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/*
行为：模拟可取消的网络等待。
实现：使用短定时器，完成或取消后清理监听，取消时返回 AbortError。
*/
async function waitForResponse(signal?: AbortSignal | null): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('请求已取消', 'AbortError'))
      return
    }
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', cancel)
      resolve()
    }, 350)
    /*
    行为：停止演示请求等待。
    实现：取消定时器和监听，再报告取消原因。
    */
    function cancel(): void {
      clearTimeout(timer)
      signal?.removeEventListener('abort', cancel)
      reject(new DOMException('请求已取消', 'AbortError'))
    }
    signal?.addEventListener('abort', cancel, { once: true })
  })
}

/*
行为：为界面检查提供显式演示数据，模拟登录、身份与分页接口。
实现：只供开发 demo 模式装配，使用虚构账号，失败场景保持失败响应。
*/
export const demoFetch: typeof globalThis.fetch = async (input, init) => {
  await waitForResponse(init?.signal)
  const url = new URL(String(input), 'http://demo.local')
  const token = new Headers(init?.headers).get('Authorization')
  const isAdmin = token === 'Bearer demo-admin'
  const actor = isAdmin ? users[0] : token === 'Bearer demo-user' ? users[1] : undefined
  if (url.pathname.endsWith('/auth/login')) {
    const body: unknown = JSON.parse(String(init?.body))
    if (
      typeof body !== 'object' ||
      body === null ||
      !('account' in body) ||
      !('password' in body)
    ) {
      return reply(null, 400, 'VALIDATION_ERROR', '账号和密码不能为空')
    }
    if (
      body.password !== 'demo1234' ||
      !['demo_admin', 'demo_user'].includes(String(body.account))
    ) {
      return reply(null, 401, 'UNAUTHENTICATED', '账号或密码错误')
    }
    const adminLogin = body.account === 'demo_admin'
    return reply({
      accessToken: adminLogin ? 'demo-admin' : 'demo-user',
      expiresAt: new Date(Date.now() + 12 * 60 * 60 * 1000).toISOString(),
      user: adminLogin ? users[0] : users[1],
    })
  }
  if (!actor) return reply(null, 401, 'UNAUTHENTICATED', '登录已失效，请重新登录')
  if (url.pathname.endsWith('/users/me')) return reply(actor)
  if (!isAdmin) return reply(null, 403, 'FORBIDDEN', '当前账号无后台访问权限')

  const keyword = url.searchParams.get('keyword') || ''
  if (keyword === '请求失败') return reply(null, 500, 'INTERNAL_ERROR', '演示请求失败，请重试')
  const page = Number(url.searchParams.get('page') || 1)
  const pageSize = Number(url.searchParams.get('pageSize') || 10)
  let rows: (UserRow | PostRow)[]
  if (url.pathname.endsWith('/admin/users')) {
    rows = users.filter((user) => user.account.includes(keyword) || user.nickname.includes(keyword))
  } else if (url.pathname.endsWith('/admin/posts')) {
    const authorId = url.searchParams.get('authorId')
    const from = url.searchParams.get('from')
    const to = url.searchParams.get('to')
    const tag = url.searchParams.get('tag')
    rows = posts.filter((post) => {
      const day = new Date(Date.parse(post.createdAt) + 8 * 60 * 60 * 1000)
        .toISOString()
        .slice(0, 10)
      return (
        post.text.includes(keyword) &&
        (!authorId || post.author.id === Number(authorId)) &&
        (!from || day >= from) &&
        (!to || day <= to) &&
        (!tag || post.tags.includes(tag))
      )
    })
  } else {
    return reply(null, 404, 'NOT_FOUND', '接口不存在')
  }
  return reply({
    items: rows.slice((page - 1) * pageSize, page * pageSize),
    page,
    pageSize,
    total: rows.length,
  })
}
