// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StateView from '../../src/components/StateView.vue'

/**
 * StateView 是"加载/空/错误/正常"的唯一出口：
 * 错误态必须能重试，正常态必须渲染插槽内容，否则页面会出现空白无反馈。
 */
describe('StateView', () => {
  it('loading 显示加载圈与默认标题', () => {
    const wrapper = mount(StateView, { props: { status: 'loading' } })

    expect(wrapper.find('.state-view__spinner').exists()).toBe(true)
    expect(wrapper.text()).toContain('加载中…')
  })

  it('empty 显示空态默认标题与说明', () => {
    const wrapper = mount(StateView, {
      props: { status: 'empty', description: '还没有动态，去发布第一条。' },
    })

    expect(wrapper.text()).toContain('暂无内容')
    expect(wrapper.text()).toContain('还没有动态，去发布第一条。')
    expect(wrapper.find('.state-view__spinner').exists()).toBe(false)
  })

  it('error 显示重试按钮，点击派发 retry', async () => {
    const wrapper = mount(StateView, { props: { status: 'error' } })

    expect(wrapper.text()).toContain('加载失败')
    const button = wrapper.find('button')
    expect(button.exists()).toBe(true)

    await button.trigger('tap')

    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('error 支持自定义重试文案', () => {
    const wrapper = mount(StateView, {
      props: { status: 'error', retryText: '再试一次' },
    })
    expect(wrapper.text()).toContain('再试一次')
  })

  it('ready 只渲染插槽内容，不渲染状态区', () => {
    const wrapper = mount(StateView, {
      props: { status: 'ready' },
      slots: { default: '<view class="real-content">真实内容</view>' },
    })

    expect(wrapper.find('.real-content').exists()).toBe(true)
    expect(wrapper.find('.state-view__body').exists()).toBe(false)
  })

  it('loading / empty 状态不渲染插槽内容', () => {
    const wrapper = mount(StateView, {
      props: { status: 'loading' },
      slots: { default: '<view class="real-content">真实内容</view>' },
    })

    expect(wrapper.find('.real-content').exists()).toBe(false)
  })

  it('支持自定义标题覆盖默认值', () => {
    const wrapper = mount(StateView, {
      props: { status: 'empty', title: '还没有评分' },
    })
    expect(wrapper.text()).toContain('还没有评分')
    expect(wrapper.text()).not.toContain('暂无内容')
  })

  it('compact 使用紧凑样式类', () => {
    const wrapper = mount(StateView, { props: { status: 'empty', compact: true } })
    expect(wrapper.classes()).toContain('state-view--compact')
  })
})
