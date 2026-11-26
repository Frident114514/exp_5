import { createMockPostApi } from './mockPostApi'
import type { PostApi } from './types'

export const CURRENT_USER = {
  id: 6006,
  nickname: 'M06 演示用户',
}

export const isUsingMock = true
export const mockPostApi = createMockPostApi({ currentUser: CURRENT_USER })

export function getPostApi(): PostApi {
  return mockPostApi
}