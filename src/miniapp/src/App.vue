<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { resolveApiBaseUrl, resolveUseMock } from '@/config/env'
import { authStore } from '@/stores/auth'

onLaunch(() => {
  // 启动即恢复本地登录态；并打印生效的服务端地址，便于联调时确认 .env 是否被正确读取。
  authStore.load()
  console.log('[miniapp] API base url =', resolveApiBaseUrl(), 'mock =', resolveUseMock())
})

onShow(() => {
  console.log('[miniapp] show')
})
</script>

<style lang="scss">
@import './styles/base.scss';
</style>
