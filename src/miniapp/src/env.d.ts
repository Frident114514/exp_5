/// <reference types="vite/client" />

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

interface ImportMetaEnv {
  /** 服务端 API 基础地址，含 `/api/v1`，由 src/.env.example 统一登记。 */
  readonly VITE_MINIAPP_API_BASE_URL?: string
  /** 媒体文件源地址（用于拼接 `/api/v1/media/{id}/content`）。 */
  readonly VITE_MINIAPP_MEDIA_ORIGIN?: string
  /** 是否启用本地模拟接口，'true' / '1' 开启；只能在开发期使用。 */
  readonly VITE_MINIAPP_USE_MOCK?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
