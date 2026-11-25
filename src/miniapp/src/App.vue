<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { resolveApiBaseUrl } from '@/config/env'

onLaunch(() => {
  // 启动即打印生效的服务端地址，便于联调时确认 .env 是否被正确读取。
  console.log('[miniapp] API base url =', resolveApiBaseUrl())
})

onShow(() => {
  console.log('[miniapp] show')
})
</script>

<style lang="scss">
@import './styles/base.scss';
</style>
