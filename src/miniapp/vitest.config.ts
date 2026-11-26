import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

// 单元测试覆盖两层：
//   1. 纯逻辑：校验规则、路由表、配置、请求层、登录态、守卫、模拟接口；
//   2. 组件交互：AppButton 防重复提交、FormField 输入与错误态、StateView 四态（jsdom 环境）。
// 需要真机/模拟器才能验证的页面行为放在 tests/evidence/M05/ 的手工记录里。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['tests/**/*.spec.ts'],
    reporters: 'default',
    // 组件测试用 jsdom（文件头以 `// @vitest-environment jsdom` 声明），其余跑在 node 下。
    // 单元测试永远走真实请求分支：断言请求细节；本地 .env.local 打开模拟接口也不影响测试。
    env: {
      VITE_MINIAPP_USE_MOCK: 'false',
    },
  },
})
