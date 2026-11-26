import { PostApiError } from '../api/types'

export function getErrorMessage(error: unknown): string {
  if (error instanceof PostApiError) {
    return error.message
  }

  if (error instanceof Error && error.message.trim()) {
    return error.message
  }

  return '请求失败，请稍后重试'
}