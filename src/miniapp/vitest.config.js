import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * 合并后的测试配置：
 *   - tests/*.test.ts   M06（作者流程）与 M07（广场/详情）的纯逻辑测试
 *   - tests/*.spec.ts   M05（请求层/登录态/守卫/表单）与公共组件测试
 * 组件用例在文件头用 `// @vitest-environment jsdom` 声明，其余跑在 node 下，不依赖小程序运行时。
 * 这里固定服务端地址，避免本机 .env.local 影响断言。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    // M05 用 *.spec.ts；M06 的两个 * 用 vitest；M07 的 client/feed/format/post.test.ts 是
    // node:test 写法，由 `pnpm test:m07`（node tests/index.ts）运行，这里不收。
    include: ['tests/**/*.spec.ts', 'tests/mockPostApi.test.ts', 'tests/postRules.test.ts'],
    env: {
      VITE_API_BASE_URL: 'http://127.0.0.1:8080/api/v1',
    },
  },
})
