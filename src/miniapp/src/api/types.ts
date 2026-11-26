export interface ApiEnvelope<T> {
  code: string
  data: T
  message: string
}

export interface PostAuthor {
  id: number
  nickname: string
}

export type MediaType = 'IMAGE' | 'VIDEO'

export interface PostMedia {
  id: number
  type: MediaType
  url: string
}

export interface RatingSummary {
  average: number | null
  count: number
}

export interface PostView {
  id: number
  author: PostAuthor
  text: string
  media: PostMedia[]
  tags: string[]
  createdAt: string
  updatedAt: string
  rating: RatingSummary
}

export interface PageView<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export interface CreatedPost {
  id: number
}

export interface CreatePostRequest {
  text: string
  mediaIds: number[]
  tags: string[]
}

export interface EditPostRequest {
  text?: string
  mediaIds?: number[]
  tags?: string[]
}

export interface PostApi {
  create(input: CreatePostRequest): Promise<CreatedPost>
  edit(postId: number, input: EditPostRequest): Promise<PostView>
  delete(postId: number): Promise<void>
  detail(postId: number): Promise<PostView>
  mine(page: number, pageSize: number): Promise<PageView<PostView>>
}

export type ApiErrorCode =
  | 'VALIDATION_ERROR'
  | 'UNAUTHENTICATED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'NETWORK_ERROR'
  | 'DEPENDENCY_UNAVAILABLE'

export class PostApiError extends Error {
  readonly code: ApiErrorCode

  constructor(code: ApiErrorCode, message: string) {
    super(message)
    this.name = 'PostApiError'
    this.code = code
  }
}