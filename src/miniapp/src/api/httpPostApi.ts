import {
  PostApiError,
  type ApiEnvelope,
  type ApiErrorCode,
  type CreatePostRequest,
  type CreatedPost,
  type EditPostRequest,
  type PageView,
  type PostApi,
  type PostView,
} from './types'

export interface HttpRequestOptions {
  path: string
  method: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  data?: unknown
}

export interface HttpTransport {
  request<T>(options: HttpRequestOptions): Promise<ApiEnvelope<T>>
}

function errorCode(value: string): ApiErrorCode {
  const known: ApiErrorCode[] = [
    'VALIDATION_ERROR',
    'UNAUTHENTICATED',
    'FORBIDDEN',
    'NOT_FOUND',
    'CONFLICT',
    'NETWORK_ERROR',
    'DEPENDENCY_UNAVAILABLE',
  ]
  return known.includes(value as ApiErrorCode)
    ? (value as ApiErrorCode)
    : 'DEPENDENCY_UNAVAILABLE'
}

function unwrap<T>(response: ApiEnvelope<T>): T {
  if (response.code !== 'OK') {
    throw new PostApiError(errorCode(response.code), response.message || '请求失败')
  }
  return response.data
}

export function createHttpPostApi(transport: HttpTransport): PostApi {
  return {
    async create(input: CreatePostRequest): Promise<CreatedPost> {
      return unwrap(
        await transport.request<CreatedPost>({
          path: '/posts',
          method: 'POST',
          data: input,
        }),
      )
    },

    async edit(postId: number, input: EditPostRequest): Promise<PostView> {
      return unwrap(
        await transport.request<PostView>({
          path: `/posts/${postId}`,
          method: 'PATCH',
          data: input,
        }),
      )
    },

    async delete(postId: number): Promise<void> {
      unwrap(
        await transport.request<null>({
          path: `/posts/${postId}`,
          method: 'DELETE',
        }),
      )
    },

    async detail(postId: number): Promise<PostView> {
      return unwrap(
        await transport.request<PostView>({
          path: `/posts/${postId}`,
          method: 'GET',
        }),
      )
    },

    async mine(page: number, pageSize: number): Promise<PageView<PostView>> {
      return unwrap(
        await transport.request<PageView<PostView>>({
          path: `/users/me/posts?page=${page}&pageSize=${pageSize}`,
          method: 'GET',
        }),
      )
    },
  }
}