/// <reference types="@dcloudio/types" />
/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

/**
 * 小程序端环境变量（编译期注入，改完需重启 dev/build）。
 * 两个历史名字都支持，读到任意一个即可：M07 用 VITE_API_BASE_URL，M05 用 VITE_MINIAPP_API_BASE_URL。
 */
interface ImportMetaEnv {
  readonly VITE_MINIAPP_USE_MOCK?: string
  readonly VITE_API_BASE_URL?: string
  readonly VITE_MINIAPP_API_BASE_URL?: string
  readonly VITE_MINIAPP_MEDIA_ORIGIN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
