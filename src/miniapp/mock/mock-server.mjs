/**
 * M07 第一周本地联调用的 Mock 服务端（零依赖，仅用于前端自测）。
 *
 * 用途：M02/M03 的真实 Spring Boot 服务就绪前，让小程序跑通
 *       “正常列表 / 空结果 / 接口失败 / 已删除详情”四种状态。
 * 说明：只实现 contracts/api.md 里第一周用到的 GET /posts 与 GET /posts/{id}，
 *       响应形状与契约完全一致；真实后端就绪后直接停掉本服务、改 VITE_API_BASE_URL 即可。
 *
 * 启动：
 *   node mock/mock-server.mjs                 # 正常数据，端口 8080
 *   $env:PORT=8081; node mock/mock-server.mjs # 换端口
 *   $env:MOCK_SCENARIO="empty"; node mock/mock-server.mjs   # 空结果
 *   $env:MOCK_SCENARIO="error"; node mock/mock-server.mjs   # 500 失败
 *   $env:MOCK_SCENARIO="slow";  node mock/mock-server.mjs   # 延迟 1.5s，验证加载态
 *
 * 也可在浏览器直接加查询参数试：/api/v1/posts?scenario=empty
 */

import http from 'node:http'

const PORT = Number(process.env.PORT || 8080)
const DEFAULT_SCENARIO = process.env.MOCK_SCENARIO || 'normal'

const pad = (n) => String(n).padStart(2, '0')
const isoAt = (daysAgo, hour, minute) => {
  const d = new Date()
  d.setDate(d.getDate() - daysAgo)
  d.setHours(hour, minute, 0, 0)
  const offset = -d.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const abs = Math.abs(offset)
  return (
    d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
    'T' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':00' +
    sign + pad(Math.floor(abs / 60)) + ':' + pad(abs % 60)
  )
}

const image = (id) => ({ id, type: 'IMAGE', url: '/api/v1/media/' + id + '/content' })
const video = (id) => ({ id, type: 'VIDEO', url: '/api/v1/media/' + id + '/content' })

/** 12 条演示动态：纯文本 / 单图 / 多图 / 视频 / 无评分，覆盖第一周展示分支。 */
function buildPosts() {
  const posts = [
    {
      id: 101,
      author: { id: 7, nickname: '小明' },
      text: '周末的校园，银杏黄了一整条路。和室友从图书馆走到东门，风很轻，适合把这一周的事情都放下。',
      media: [image(1001), image(1002), image(1003), image(1004)],
      tags: ['校园', '周末', '随手拍'],
      createdAt: isoAt(0, 10, 0),
      rating: { average: 4.5, count: 2 },
    },
    {
      id: 102,
      author: { id: 12, nickname: '阿泽' },
      text: '随手录的一段晚霞，原声没有处理。',
      media: [video(2001)],
      tags: ['视频', '晚霞'],
      createdAt: isoAt(0, 19, 20),
      rating: { average: null, count: 0 },
    },
    {
      id: 103,
      author: { id: 21, nickname: '林同学' },
      text: '今天终于把课程设计的第一版页面搭完了，记录一下。纯文本动态用于检查没有媒体时的排版。',
      media: [],
      tags: ['学习', '记录'],
      createdAt: isoAt(1, 22, 5),
      rating: { average: 5, count: 1 },
    },
    {
      id: 104,
      author: { id: 30, nickname: '小圆' },
      text: '',
      media: [image(1005), image(1006)],
      tags: ['摄影'],
      createdAt: isoAt(2, 8, 40),
      rating: { average: 3.5, count: 4 },
    },
    {
      id: 105,
      author: { id: 33, nickname: '陈陈' },
      text: '食堂新出的窗口排队有点长，味道还不错，推荐番茄牛腩。',
      media: [image(1007)],
      tags: ['食堂', '校园'],
      createdAt: isoAt(3, 12, 15),
      rating: { average: 4, count: 1 },
    },
    {
      id: 106,
      author: { id: 41, nickname: '老张' },
      text: '实验室的猫又睡在键盘上了。',
      media: [image(1008), image(1009), image(1010)],
      tags: ['日常'],
      createdAt: isoAt(4, 16, 30),
      rating: { average: 4.8, count: 5 },
    },
    {
      id: 107,
      author: { id: 52, nickname: '小雨' },
      text: '第一次用这个系统发动态，测试一下长文本的折行效果。'.repeat(4),
      media: [],
      tags: ['测试'],
      createdAt: isoAt(5, 9, 0),
      rating: { average: null, count: 0 },
    },
    {
      id: 108,
      author: { id: 61, nickname: '大鹏' },
      text: '校运会接力最后一棒，慢动作回放。',
      media: [video(2002)],
      tags: ['校运会', '视频'],
      createdAt: isoAt(6, 17, 45),
      rating: { average: 4.2, count: 3 },
    },
    {
      id: 109,
      author: { id: 70, nickname: '一一' },
      text: '图书馆四楼靠窗的位置，下午的光很好。',
      media: [image(1011), image(1012)],
      tags: ['图书馆', '学习'],
      createdAt: isoAt(8, 14, 10),
      rating: { average: 4.6, count: 2 },
    },
    {
      id: 110,
      author: { id: 81, nickname: '老王' },
      text: '社团招新的海报做好了，需要再改颜色。',
      media: [image(1013)],
      tags: ['社团'],
      createdAt: isoAt(9, 11, 25),
      rating: { average: null, count: 0 },
    },
    {
      id: 111,
      author: { id: 92, nickname: 'Nana' },
      text: '雨后的操场，跑道上全是积水。',
      media: [image(1014), image(1015)],
      tags: ['校园', '雨天'],
      createdAt: isoAt(11, 18, 5),
      rating: { average: 3.9, count: 7 },
    },
    {
      id: 112,
      author: { id: 99, nickname: '阿岚' },
      text: '周报模板整理好了，需要的同学自取。',
      media: [],
      tags: ['资料'],
      createdAt: isoAt(13, 20, 0),
      rating: { average: 5, count: 2 },
    },
  ]
  return posts
}

const POSTS = buildPosts()

function sendJson(res, statusCode, payload, extraHeaders = {}) {
  const body = JSON.stringify(payload)
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Allow-Methods': 'GET,OPTIONS',
    'Cache-Control': 'no-store',
    ...extraHeaders,
  })
  res.end(body)
}

const ok = (data) => ({ code: 'OK', data, message: '' })

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function log(method, path, status, note) {
  const now = new Date()
  const time = pad(now.getHours()) + ':' + pad(now.getMinutes()) + ':' + pad(now.getSeconds())
  console.log('[' + time + '] ' + method + ' ' + path + ' -> ' + status + (note ? '  (' + note + ')' : ''))
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://' + (req.headers.host || 'localhost'))
  const path = url.pathname
  const scenario = url.searchParams.get('scenario') || DEFAULT_SCENARIO

  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': '*',
      'Access-Control-Allow-Methods': 'GET,OPTIONS',
    })
    res.end()
    return
  }

  if (path === '/health') {
    log(req.method, path, 200, 'scenario=' + scenario)
    sendJson(res, 200, ok({ status: 'up', scenario, posts: POSTS.length }))
    return
  }

  if (path === '/api/v1/posts' && req.method === 'GET') {
    if (scenario === 'error') {
      log(req.method, req.url, 500, '模拟接口失败')
      sendJson(res, 500, { code: 'INTERNAL_ERROR', data: null, message: '服务器内部错误（模拟）' })
      return
    }
    if (scenario === 'slow') {
      await sleep(1500)
    }
    if (scenario === 'empty') {
      log(req.method, req.url, 200, '空结果')
      sendJson(res, 200, ok({ items: [], page: 1, pageSize: 10, total: 0 }))
      return
    }

    const page = Math.max(1, Number(url.searchParams.get('page') || 1))
    const rawPageSize = Number(url.searchParams.get('pageSize') || 10)
    const pageSize = Math.min(50, Math.max(1, Number.isFinite(rawPageSize) ? rawPageSize : 10))
    const start = (page - 1) * pageSize
    const items = POSTS.slice(start, start + pageSize)
    log(req.method, req.url, 200, '返回 ' + items.length + '/' + POSTS.length + ' 条')
    sendJson(res, 200, ok({ items, page, pageSize, total: POSTS.length }))
    return
  }

  const detailMatch = path.match(/^\/api\/v1\/posts\/(\d+)$/)
  if (detailMatch && req.method === 'GET') {
    if (scenario === 'error') {
      log(req.method, req.url, 500, '模拟接口失败')
      sendJson(res, 500, { code: 'INTERNAL_ERROR', data: null, message: '服务器内部错误（模拟）' })
      return
    }
    const id = Number(detailMatch[1])
    const found = POSTS.find((post) => post.id === id)
    if (!found) {
      // id 999 等不存在的动态：模拟逻辑删除后返回 404
      log(req.method, req.url, 404, '已删除或不存在的动态')
      sendJson(res, 404, { code: 'NOT_FOUND', data: null, message: '动态不存在或已删除' })
      return
    }
    log(req.method, req.url, 200, 'detail #' + id)
    sendJson(res, 200, ok(found))
    return
  }

  log(req.method, req.url, 404, '未实现的接口')
  sendJson(res, 404, { code: 'NOT_FOUND', data: null, message: '接口不存在（Mock 仅实现第一周的两个接口）' })
})

server.listen(PORT, () => {
  console.log('M07 Mock 服务已启动: http://localhost:' + PORT + '/api/v1/posts')
  console.log('当前场景: ' + DEFAULT_SCENARIO + '（可用 MOCK_SCENARIO=empty|error|slow 切换）')
  console.log('演示数据: ' + POSTS.length + ' 条；详情 /api/v1/posts/101，已删除示例 /api/v1/posts/999')
})
