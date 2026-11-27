import { describe, expect, it } from 'vitest'
import {
  DEFAULT_API_BASE_URL,
  DEFAULT_MEDIA_ORIGIN,
  absoluteMediaUrl,
  buildApiUrl,
  buildMediaUrl,
  normalizeBaseUrl,
  resolveApiBaseUrl,
  resolveMediaOrigin,
} from '../src/config/env'

describe('normalizeBaseUrl', () => {
  it('去掉末尾斜杠，避免拼出双斜杠路径', () => {
    expect(normalizeBaseUrl('http://127.0.0.1:8080/api/v1/', DEFAULT_API_BASE_URL)).toBe(
      'http://127.0.0.1:8080/api/v1'
    )
    expect(normalizeBaseUrl('http://127.0.0.1:8080/api/v1///', DEFAULT_API_BASE_URL)).toBe(
      'http://127.0.0.1:8080/api/v1'
    )
  })

  it('去掉首尾空白', () => {
    expect(normalizeBaseUrl('  http://host/api/v1  ', DEFAULT_API_BASE_URL)).toBe('http://host/api/v1')
  })

  it('空值、纯空白与 undefined 回退到默认地址', () => {
    expect(normalizeBaseUrl('', DEFAULT_API_BASE_URL)).toBe(DEFAULT_API_BASE_URL)
    expect(normalizeBaseUrl('   ', DEFAULT_API_BASE_URL)).toBe(DEFAULT_API_BASE_URL)
    expect(normalizeBaseUrl(undefined, DEFAULT_API_BASE_URL)).toBe(DEFAULT_API_BASE_URL)
    expect(normalizeBaseUrl(null, DEFAULT_API_BASE_URL)).toBe(DEFAULT_API_BASE_URL)
  })
})

describe('resolveApiBaseUrl / resolveMediaOrigin', () => {
  it('未配置 .env 时返回本机演示地址', () => {
    expect(resolveApiBaseUrl()).toBe(DEFAULT_API_BASE_URL)
    expect(resolveMediaOrigin()).toBe(DEFAULT_MEDIA_ORIGIN)
  })
})

describe('buildApiUrl', () => {
  it('拼接相对与绝对形式的接口路径', () => {
    expect(buildApiUrl('auth/login', 'http://host:8080/api/v1')).toBe(
      'http://host:8080/api/v1/auth/login'
    )
    expect(buildApiUrl('/users/me', 'http://host:8080/api/v1/')).toBe(
      'http://host:8080/api/v1/users/me'
    )
  })
})

describe('buildMediaUrl', () => {
  it('按契约生成 /media/{id}/content', () => {
    expect(buildMediaUrl(33, 'http://host:8080/api/v1')).toBe(
      'http://host:8080/api/v1/media/33/content'
    )
  })

  it('媒体 ID 非正整数时直接报错', () => {
    expect(() => buildMediaUrl(0)).toThrow('mediaId 必须是正整数')
    expect(() => buildMediaUrl(-1)).toThrow('mediaId 必须是正整数')
    expect(() => buildMediaUrl(1.5)).toThrow('mediaId 必须是正整数')
  })
})

describe('absoluteMediaUrl', () => {
  it('服务端 url 已含 /api/v1，只补协议与主机，不重复拼接', () => {
    expect(absoluteMediaUrl('/api/v1/media/33/content', 'http://host:8080')).toBe(
      'http://host:8080/api/v1/media/33/content'
    )
    expect(absoluteMediaUrl('api/v1/media/33/content', 'http://host:8080/')).toBe(
      'http://host:8080/api/v1/media/33/content'
    )
  })

  it('已是绝对地址时原样返回', () => {
    expect(absoluteMediaUrl('https://cdn.example.com/a.png', 'http://host:8080')).toBe(
      'https://cdn.example.com/a.png'
    )
  })
})
