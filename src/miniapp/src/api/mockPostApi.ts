import {
  PostApiError,
  type CreatePostRequest,
  type CreatedPost,
  type EditPostRequest,
  type PageView,
  type PostApi,
  type PostAuthor,
  type PostView,
} from './types'
import {
  normalizePostText,
  validateEditedText,
  validateTextOnlyPost,
} from '../utils/postRules'

interface MockPostApiOptions {
  currentUser?: PostAuthor
  initialPosts?: PostView[]
  latencyMs?: number
  now?: () => Date
}

const DEMO_USER: PostAuthor = {
  id: 6006,
  nickname: 'M06 演示用户',
}

const DEMO_TEXTS = [
  '今天完成了第一周的纯文本动态发布。',
  '作者工作台已经可以从发布页返回“我的动态”。',
  '这条样例用于检查动态 ID、正文和作者字段。',
  '分页采用 page 从 1 开始、pageSize 最大 50 的契约。',
  '编辑只提交正文，媒体和标签字段先保持不变。',
  '删除前会弹出确认，成功后重新加载本人列表。',
  '这是用于第二页展示的样例动态。',
]

function createDemoPosts(currentUser: PostAuthor, now: Date): PostView[] {
  return DEMO_TEXTS.map((text, index) => {
    const createdAt = new Date(now.getTime() - index * 3_600_000).toISOString()
    return {
      id: 1001 + index,
      author: { ...currentUser },
      text,
      media: [],
      tags: [],
      createdAt,
      updatedAt: createdAt,
      rating: { average: null, count: 0 },
    }
  })
}

function clonePost(post: PostView): PostView {
  return {
    ...post,
    author: { ...post.author },
    media: post.media.map((item) => ({ ...item })),
    tags: [...post.tags],
    rating: { ...post.rating },
  }
}

function wait(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

export class MockPostApi implements PostApi {
  private readonly currentUser: PostAuthor
  private readonly latencyMs: number
  private readonly now: () => Date
  private posts: PostView[]
  private nextId: number
  private failOnce = false

  constructor(options: MockPostApiOptions = {}) {
    this.currentUser = { ...(options.currentUser ?? DEMO_USER) }
    this.latencyMs = options.latencyMs ?? 320
    this.now = options.now ?? (() => new Date())
    const seed = options.initialPosts ?? createDemoPosts(this.currentUser, this.now())
    this.posts = seed.map(clonePost)
    this.nextId = Math.max(1000, ...this.posts.map((post) => post.id)) + 1
  }

  getCurrentUser(): PostAuthor {
    return { ...this.currentUser }
  }

  failNextRequest(): void {
    this.failOnce = true
  }

  private async beforeRequest(): Promise<void> {
    if (this.latencyMs > 0) {
      await wait(this.latencyMs)
    }

    if (this.failOnce) {
      this.failOnce = false
      throw new PostApiError('NETWORK_ERROR', '模拟网络异常：本次请求未完成，请重试')
    }
  }

  private requireOwnedPost(postId: number): PostView {
    const post = this.posts.find((item) => item.id === postId)
    if (!post) {
      throw new PostApiError('NOT_FOUND', '动态不存在或已删除')
    }
    if (post.author.id !== this.currentUser.id) {
      throw new PostApiError('FORBIDDEN', '只能管理本人动态')
    }
    return post
  }

  async create(input: CreatePostRequest): Promise<CreatedPost> {
    await this.beforeRequest()

    if (input.mediaIds.length > 0 || input.tags.length > 0) {
      throw new PostApiError(
        'VALIDATION_ERROR',
        'M06 第一周 Mock 仅支持纯文本动态，图片、视频和标签将在第二周接入',
      )
    }

    const validation = validateTextOnlyPost(input.text)
    if (!validation.valid) {
      throw new PostApiError('VALIDATION_ERROR', validation.message)
    }

    const timestamp = this.now().toISOString()
    const post: PostView = {
      id: this.nextId++,
      author: { ...this.currentUser },
      text: validation.normalized,
      media: [],
      tags: [],
      createdAt: timestamp,
      updatedAt: timestamp,
      rating: { average: null, count: 0 },
    }

    this.posts.unshift(post)
    return { id: post.id }
  }

  async edit(postId: number, input: EditPostRequest): Promise<PostView> {
    await this.beforeRequest()

    if (input.mediaIds !== undefined || input.tags !== undefined) {
      throw new PostApiError(
        'VALIDATION_ERROR',
        'M06 第一周 Mock 仅支持编辑正文，媒体和标签字段暂不提交',
      )
    }

    const post = this.requireOwnedPost(postId)
    const nextText = input.text ?? post.text
    const validation = validateEditedText(nextText, post.media.length)
    if (!validation.valid) {
      throw new PostApiError('VALIDATION_ERROR', validation.message)
    }

    post.text = validation.normalized
    post.updatedAt = this.now().toISOString()
    return clonePost(post)
  }

  async delete(postId: number): Promise<void> {
    await this.beforeRequest()
    this.requireOwnedPost(postId)
    this.posts = this.posts.filter((post) => post.id !== postId)
  }

  async detail(postId: number): Promise<PostView> {
    await this.beforeRequest()
    const post = this.posts.find((item) => item.id === postId)
    if (!post) {
      throw new PostApiError('NOT_FOUND', '动态不存在或已删除')
    }
    return clonePost(post)
  }

  async mine(page: number, pageSize: number): Promise<PageView<PostView>> {
    await this.beforeRequest()

    if (!Number.isInteger(page) || page < 1) {
      throw new PostApiError('VALIDATION_ERROR', '页码必须从 1 开始')
    }
    if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > 50) {
      throw new PostApiError('VALIDATION_ERROR', '每页数量必须在 1 到 50 之间')
    }

    const mine = this.posts
      .filter((post) => post.author.id === this.currentUser.id)
      .sort((left, right) => {
        const byTime = Date.parse(right.createdAt) - Date.parse(left.createdAt)
        return byTime || right.id - left.id
      })
    const start = (page - 1) * pageSize

    return {
      items: mine.slice(start, start + pageSize).map(clonePost),
      page,
      pageSize,
      total: mine.length,
    }
  }
}

export function createMockPostApi(options: MockPostApiOptions = {}): MockPostApi {
  return new MockPostApi(options)
}

export function normalizeMockText(value: string): string {
  return normalizePostText(value)
}