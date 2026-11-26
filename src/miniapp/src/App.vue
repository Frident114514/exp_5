<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  // 第一周只打印环境信息，方便在微信开发者工具 Console 里确认联调地址
  console.log('[miniapp] launched')
})
</script>

<style>
page {
  background-color: #f5f6f8;
  color: #23293a;
  font-family: -apple-system, 'PingFang SC', 'Helvetica Neue', Helvetica, sans-serif;
}

.page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 24rpx 24rpx 48rpx;
}
</style>
