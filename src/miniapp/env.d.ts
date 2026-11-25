/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 服务端 API 基础地址，默认 http://localhost:8080/api/v1 */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
