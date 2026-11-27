import { createRequire } from 'node:module'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'

// @dcloudio/vite-plugin-uni 是 CJS 包，在 "type": "module" 下 default 会多包一层，
// 直接用 createRequire 取到函数本体，避免 "uni is not a function"。
// （该修复来自 M07 的 09c5d9d，合并时保留，不能退回 default import。）
const require = createRequire(import.meta.url)
const uni = require('@dcloudio/vite-plugin-uni').default

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const useH5Proxy = process.env.UNI_PLATFORM === 'h5' &&
    !env.VITE_API_BASE_URL && !env.VITE_MINIAPP_API_BASE_URL
  return {
    define: useH5Proxy ? { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('/api/v1') } : {},
    server: {
      proxy: {
        '/api': {
          target: env.VITE_DEV_API_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
    plugins: [uni()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  }
})

