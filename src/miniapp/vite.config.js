import { createRequire } from 'node:module'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

// @dcloudio/vite-plugin-uni 是 CJS 包，在 "type": "module" 下 default 会多包一层，
// 直接用 createRequire 取到函数本体，避免 "uni is not a function"。
const require = createRequire(import.meta.url)
const uni = require('@dcloudio/vite-plugin-uni').default

export default defineConfig({
  plugins: [uni()],
  // H5 开发时如遇跨域，把 VITE_API_BASE_URL 设为 /api/v1，请求会经这里转发到本机后端
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})