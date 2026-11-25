/**
 * 统一反馈（提示、加载、确认框）· 维护人：M05
 *
 * 页面不要直接散写 uni.showToast / uni.showModal，统一走这里：
 * 文案长短、显示时长和图标风格保持一致，后续换样式只需改一处。
 * 约定：
 *   - 成功：绿色对勾，1.5 秒；
 *   - 失败：无图标（可容纳长句），3 秒，便于看清服务端 message；
 *   - 加载：必须成对调用 showLoading / hideLoading，异常分支也要关闭。
 */

const TOAST_DURATION_SUCCESS = 1500
const TOAST_DURATION_ERROR = 3000

export function toastSuccess(title: string): void {
  uni.showToast({ title, icon: 'success', duration: TOAST_DURATION_SUCCESS })
}

export function toastError(title: string): void {
  uni.showToast({ title, icon: 'none', duration: TOAST_DURATION_ERROR })
}

export function toastInfo(title: string): void {
  uni.showToast({ title, icon: 'none', duration: TOAST_DURATION_SUCCESS })
}

export function showLoading(title = '加载中'): void {
  uni.showLoading({ title, mask: true })
}

export function hideLoading(): void {
  uni.hideLoading()
}

/** 确认框，返回用户是否点击了"确定"。 */
export function confirmAction(content: string, title = '提示', confirmText = '确定'): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      cancelText: '取消',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false),
    })
  })
}
