import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const port = Number(env.VITE_ADMIN_DEV_PORT || 5173)
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('VITE_ADMIN_DEV_PORT 必须为 1–65535 的整数')
  }

  return {
    plugins: [vue()],
    server: {
      host: '127.0.0.1',
      port,
      strictPort: true,
      proxy: {
        '/api': { target: env.ADMIN_API_PROXY_TARGET || 'http://127.0.0.1:8080' },
      },
    },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true },
  }
})
