/**
 * 小程序运行时配置。
 * 本地联调时，服务端默认为本机 Spring Boot（http://127.0.0.1:8080/api/v1），路径已含 /api/v1，不要再重复拼接。
 * 真机或改为局域网地址时，复制 .env.local.example 为 .env.local 并填写 VITE_API_BASE_URL。
 */

const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env

export const API_BASE_URL: string = env?.VITE_API_BASE_URL || 'http://127.0.0.1:8080/api/v1'

/** 单次请求超时时间（毫秒）。联调时如服务端较慢可调大。 */
export const REQUEST_TIMEOUT = 10000

/** 列表每页条数，契约上限为 50，默认 10。 */
export const DEFAULT_PAGE_SIZE = 10