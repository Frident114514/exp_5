import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

// 单元测试只覆盖与 uni 运行时无关的纯逻辑（校验规则、路由表、配置、类型边界）。
// 需要真机/模拟器才能验证的页面行为放在 tests/evidence/M05/ 的手工记录里。
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['tests/**/*.spec.ts'],
    reporters: 'default',
    // 单元测试永远走真实请求分支：断言请求细节；本地 .env.local 打开模拟接口也不影响测试。
    env: {
      VITE_MINIAPP_USE_MOCK: 'false',
    },
  },
})
