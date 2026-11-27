// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AppButton from '../../src/components/AppButton.vue'

/**
 * AppButton 的防重复提交是最容易被绕过的公共能力：
 * 页面只要忘了绑 loading，用户连点就会重复提交。这里锁定行为。
 */
describe('AppButton', () => {
  it('点击时派发 click 事件（小程序里 tap 由 uni 编译，测试中直接派发 tap）', async () => {
    const wrapper = mount(AppButton)
    await wrapper.trigger('tap')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('loading 为真时不派发 click', async () => {
    const wrapper = mount(AppButton, { props: { loading: true } })
    await wrapper.trigger('tap')
    await wrapper.trigger('tap')
    expect(wrapper.emitted('click')).toBeUndefined()
    expect(wrapper.find('.app-button__spinner').exists()).toBe(true)
  })

  it('disabled 为真时不派发 click', async () => {
    const wrapper = mount(AppButton, { props: { disabled: true } })
    await wrapper.trigger('tap')
    expect(wrapper.emitted('click')).toBeUndefined()
  })

  it('默认 400ms 节流：连续快速点击只触发一次', async () => {
    const wrapper = mount(AppButton)
    await wrapper.trigger('tap')
    await wrapper.trigger('tap')
    await wrapper.trigger('tap')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('throttle=0 时不做间隔限制', async () => {
    const wrapper = mount(AppButton, { props: { throttle: 0 } })
    await wrapper.trigger('tap')
    await wrapper.trigger('tap')
    expect(wrapper.emitted('click')).toHaveLength(2)
  })

  it('没有插槽时渲染 text 属性', () => {
    const wrapper = mount(AppButton, { props: { text: '提交' } })
    expect(wrapper.text()).toContain('提交')
  })

  it('loading 时按钮被禁用，避免浏览器重复提交', () => {
    const wrapper = mount(AppButton, { props: { loading: true } })
    expect(wrapper.attributes('disabled')).toBeDefined()
  })
})
