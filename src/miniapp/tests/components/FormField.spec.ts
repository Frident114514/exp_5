// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FormField from '../../src/components/FormField.vue'

/**
 * FormField 是账号页所有输入项的载体：
 * 错误提示的优先级、密码显隐、清空按钮的行为一旦错了，四个页面都会错。
 */
describe('FormField', () => {
  it('渲染标签与必填星号', () => {
    const wrapper = mount(FormField, {
      props: { modelValue: '', label: '账号', required: true },
    })

    expect(wrapper.text()).toContain('账号')
    expect(wrapper.find('.form-field__required').text()).toBe('*')
  })

  it('必填为 false 时不显示星号', () => {
    const wrapper = mount(FormField, { props: { modelValue: '', label: '昵称' } })
    expect(wrapper.find('.form-field__required').exists()).toBe(false)
  })

  it('输入时派发 update:modelValue', async () => {
    const wrapper = mount(FormField, { props: { modelValue: '' } })
    const input = wrapper.find('input')

    await input.setValue('demo')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['demo'])
  })

  it('失焦时派发 blur 并带上当前值', async () => {
    const wrapper = mount(FormField, { props: { modelValue: 'demo' } })
    await wrapper.find('input').trigger('blur')

    expect(wrapper.emitted('blur')?.[0]).toEqual(['demo'])
  })

  it('有 error 时显示错误文案，并让位掉 hint', () => {
    const wrapper = mount(FormField, {
      props: { modelValue: '', error: '账号长度为 4–20 位', hint: '注册后会用于登录' },
    })

    expect(wrapper.find('.form-field__error').text()).toBe('账号长度为 4–20 位')
    expect(wrapper.find('.form-field__hint').exists()).toBe(false)
    expect(wrapper.classes()).toContain('form-field--error')
  })

  it('没有 error 时显示 hint', () => {
    const wrapper = mount(FormField, {
      props: { modelValue: '', hint: '注册后会用于登录' },
    })

    expect(wrapper.find('.form-field__hint').text()).toBe('注册后会用于登录')
    expect(wrapper.find('.form-field__error').exists()).toBe(false)
  })

  it('type=password 默认密文，点"显示"后变明文，按钮文案随之切换', async () => {
    const wrapper = mount(FormField, {
      props: { modelValue: 'abcd1234', type: 'password' },
    })
    const input = wrapper.find('input')

    expect(input.attributes('password')).toBe('true')
    expect(wrapper.text()).toContain('显示')

    await wrapper.find('.form-field__action').trigger('tap')

    expect(wrapper.find('input').attributes('password')).toBeUndefined()
    expect(wrapper.text()).toContain('隐藏')
  })

  it('有值时显示"清空"，点击后派发空串', async () => {
    const wrapper = mount(FormField, { props: { modelValue: 'demo' } })
    const actions = wrapper.findAll('.form-field__action')
    const clearAction = actions.find((item) => item.text() === '清空')

    expect(clearAction).toBeTruthy()
    await clearAction?.trigger('tap')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([''])
  })

  it('值为空时不显示"清空"', () => {
    const wrapper = mount(FormField, { props: { modelValue: '' } })
    const hasClear = wrapper.findAll('.form-field__action').some((item) => item.text() === '清空')
    expect(hasClear).toBe(false)
  })

  it('禁用时透传 disabled 且不显示清空', () => {
    const wrapper = mount(FormField, { props: { modelValue: 'demo', disabled: true } })

    expect(wrapper.find('input').attributes('disabled')).toBeDefined()
    expect(wrapper.classes()).toContain('form-field--disabled')
  })

  it('placeholder 使用全局类 form-placeholder（小程序端 scoped 样式命中不了）', () => {
    const wrapper = mount(FormField, { props: { modelValue: '', placeholder: '请输入账号' } })

    expect(wrapper.find('input').attributes('placeholder')).toBe('请输入账号')
    expect(wrapper.find('input').attributes('placeholder-class')).toBe('form-placeholder')
  })
})
