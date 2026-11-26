/**
 * 媒体接口 · 维护人：M05（接口层），上传交互由 M06 实现
 *
 * 对应 specs/contracts/api.md 的 U02 行与 media-module.md：
 *   - `POST /media`：multipart/form-data，字段名 `file`；图片 JPEG/PNG/WebP ≤10MB，MP4 ≤50MB；
 *   - `GET /media/{mediaId}/content`：公开读取，视频支持 Range（直接用于 <video> 的 src）。
 *
 * 上传不走 `api/http.ts` 的 request()（multipart 需要 uni.uploadFile），
 * 但错误解析与 401 处理复用同一套逻辑，页面拿到的仍然是 `ApiError`。
 */

import { absoluteMediaUrl, buildMediaUrl, buildApiUrl, resolveUseMock } from '@/config/env'
import { authStore } from '@/stores/auth'
import {
  apiErrorFromResponse,
  createApiError,
  fallbackMessage,
  isUnauthorizedError,
  notifyUnauthorized,
} from './http'
import { findMock } from './mock'
import { SUCCESS_CODE, type ApiEnvelope, type MediaUploadResult } from './types'

/** 上传超时：视频最大 50MB，给足时间。 */
export const UPLOAD_TIMEOUT_MS = 60000

export const MEDIA_ENDPOINTS = {
  upload: '/media',
  content: (mediaId: number) => `/media/${mediaId}/content`,
} as const

/** 媒体地址：相对地址补全为可访问地址，服务端返回的 url 已含 /api/v1。 */
export function mediaDisplayUrl(url: string): string {
  return absoluteMediaUrl(url)
}

/** 按 mediaId 直接拼内容地址（例如只有 ID、没有 url 字段时）。 */
export function mediaContentUrl(mediaId: number): string {
  return buildMediaUrl(mediaId)
}

export interface UploadMediaOptions {
  /** multipart 字段名，契约固定为 `file`。 */
  name?: string
  timeout?: number
}

/**
 * 上传单个媒体文件，返回 `mediaId` 供发布/编辑动态时使用。
 * `filePath` 来自 uni.chooseImage / uni.chooseVideo 的临时路径。
 */
export async function uploadMedia(
  filePath: string,
  options: UploadMediaOptions = {}
): Promise<MediaUploadResult> {
  if (!filePath) {
    throw new Error('上传文件的 filePath 不能为空')
  }

  if (resolveUseMock()) {
    const mock = findMock('POST', MEDIA_ENDPOINTS.upload)
    if (mock) {
      return (await mock({
        method: 'POST',
        path: MEDIA_ENDPOINTS.upload,
        data: { filePath },
        query: {},
        token: authStore.token,
      })) as MediaUploadResult
    }
  }

  const tokenBeforeRequest = authStore.token
  const header: Record<string, string> = {}
  if (tokenBeforeRequest) {
    header.Authorization = `Bearer ${tokenBeforeRequest}`
  }

  return new Promise<MediaUploadResult>((resolve, reject) => {
    uni.uploadFile({
      url: buildApiUrl(MEDIA_ENDPOINTS.upload),
      filePath,
      name: options.name ?? 'file',
      header,
      timeout: options.timeout ?? UPLOAD_TIMEOUT_MS,
      success: (res) => {
        let parsed: unknown = res.data
        if (typeof res.data === 'string') {
          try {
            parsed = JSON.parse(res.data)
          } catch {
            parsed = null
          }
        }
        const envelope = parsed as Partial<ApiEnvelope<MediaUploadResult>> | null
        if (res.statusCode >= 200 && res.statusCode < 300 && envelope?.code === SUCCESS_CODE) {
          resolve(envelope.data as MediaUploadResult)
          return
        }
        const error = apiErrorFromResponse(res.statusCode, parsed)
        if (isUnauthorizedError(error)) {
          notifyUnauthorized(MEDIA_ENDPOINTS.upload, tokenBeforeRequest)
        }
        reject(error)
      },
      fail: () => {
        reject(createApiError(0, 'NETWORK_ERROR', fallbackMessage(0)))
      },
    })
  })
}
