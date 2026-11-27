<script setup lang="ts">
/**
 * 应用入口（M05/M06/M07 合并）
 *
 * 同时引入两套历史样式：
 *   - styles/app.css     M06 作者页面使用的类（app-header / status-panel 等）
 *   - styles/base.scss   M05 全局基础样式与工具类（配合 uni.scss 令牌）
 * 启动时恢复登录态，供请求层与账号页使用。
 */
import { onLaunch } from '@dcloudio/uni-app'
import { authStore } from '@/stores/auth'
import './styles/app.css'

onLaunch(() => {
  authStore.load()
  console.log('[miniapp] API base =', import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_MINIAPP_API_BASE_URL || '默认 127.0.0.1:8080')
})
</script>

<style lang="scss">
@import './styles/base.scss';

page {
  min-height: 100%;
  background: #f5f7fb;
}
</style>
