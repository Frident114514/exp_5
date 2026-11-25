import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// uni-app 官方约定：插件统一由 @dcloudio/vite-plugin-uni 提供，
// 平台差异（H5 / 微信小程序）由 package.json 中的 `uni -p <platform>` 参数决定。
export default defineConfig({
  plugins: [uni()],
})
