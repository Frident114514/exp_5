import { createRequire } from 'node:module'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

// @dcloudio/vite-plugin-uni 是 CJS 包，在 "type": "module" 下 default 会多包一层，
// 直接用 createRequire 取到函数本体，避免 "uni is not a function"。
const require = createRequire(import.meta.url)
const uni = require('@dcloudio/vite-plugin-uni').default

export default defineConfig({
  plugins: [uni()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
