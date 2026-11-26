import { describe, expect, it } from 'vitest'
import {
  countCodePoints,
  validateEditedText,
  validateTextOnlyPost,
} from '@/utils/postRules'

describe('纯文本动态规则', () => {
  it('去除首尾空白并拒绝空正文', () => {
    expect(validateTextOnlyPost('   ')).toMatchObject({
      valid: false,
      length: 0,
    })
    expect(validateTextOnlyPost('  第一条动态  ')).toMatchObject({
      valid: true,
      normalized: '第一条动态',
    })
  })

  it('允许 10000 个 Unicode 码点并拒绝第 10001 个', () => {
    expect(validateTextOnlyPost('a'.repeat(10_000)).valid).toBe(true)
    expect(validateTextOnlyPost('a'.repeat(10_001)).valid).toBe(false)
  })

  it('按 Unicode 码点而非 UTF-16 单元计数', () => {
    const emojiText = '😀'.repeat(10_000)
    expect(emojiText.length).toBe(20_000)
    expect(countCodePoints(emojiText)).toBe(10_000)
    expect(validateTextOnlyPost(emojiText).valid).toBe(true)
  })

  it('编辑时可清空文字但必须保留至少一项媒体', () => {
    expect(validateEditedText('   ', 0)).toMatchObject({ valid: false })
    expect(validateEditedText('   ', 1)).toMatchObject({
      valid: true,
      normalized: '',
    })
  })
})