import { describe, expect, it } from 'vitest'
import {
  ACCOUNT_MAX_LENGTH,
  ACCOUNT_MIN_LENGTH,
  NICKNAME_MAX_LENGTH,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  countChars,
  firstError,
  validateAccount,
  validateLoginPassword,
  validateNewPassword,
  validateNickname,
  validatePasswordConfirm,
  validatePasswordStrength,
  validateRequired,
} from '../src/utils/validate'

describe('countChars', () => {
  it('按 Unicode 码点计数，emoji 只算 1 个字符', () => {
    expect(countChars('abc')).toBe(3)
    expect(countChars('😀')).toBe(1)
    expect(countChars('a😀')).toBe(2)
  })
})

describe('validateRequired', () => {
  it('空白字符串视为未填写', () => {
    expect(validateRequired('', '账号')).toBe('请输入账号')
    expect(validateRequired('   ', '账号')).toBe('请输入账号')
    expect(validateRequired('x', '账号')).toBeNull()
  })
})

describe('validateAccount', () => {
  it('接受 4–20 位字母、数字和下划线', () => {
    expect(validateAccount('user_01')).toBeNull()
    expect(validateAccount('a'.repeat(ACCOUNT_MIN_LENGTH))).toBeNull()
    expect(validateAccount('a'.repeat(ACCOUNT_MAX_LENGTH))).toBeNull()
  })

  it('拒绝空值、过短、过长和非法字符', () => {
    expect(validateAccount('')).toBe('请输入账号')
    expect(validateAccount('a'.repeat(ACCOUNT_MIN_LENGTH - 1))).toContain('账号长度')
    expect(validateAccount('a'.repeat(ACCOUNT_MAX_LENGTH + 1))).toContain('账号长度')
    expect(validateAccount('中文账号')).toBe('账号只能包含字母、数字和下划线')
    expect(validateAccount('user name')).toBe('账号只能包含字母、数字和下划线')
    expect(validateAccount('user-01')).toBe('账号只能包含字母、数字和下划线')
  })
})

describe('validateLoginPassword', () => {
  it('登录只校验必填与长度上限，不拦截历史弱密码', () => {
    expect(validateLoginPassword('')).toBe('请输入密码')
    expect(validateLoginPassword('x')).toBeNull()
    expect(validateLoginPassword('a'.repeat(PASSWORD_MAX_LENGTH + 1))).toContain('不能超过')
  })
})

describe('validatePasswordStrength', () => {
  it('接受 8–32 位且含字母和数字的密码', () => {
    expect(validatePasswordStrength('abcd1234')).toBeNull()
    expect(validatePasswordStrength('A1'.repeat(PASSWORD_MIN_LENGTH / 2))).toBeNull()
  })

  it('拒绝过短、过长、纯字母、纯数字和含空格的密码', () => {
    expect(validatePasswordStrength('')).toBe('请输入密码')
    expect(validatePasswordStrength('abc123')).toContain('密码长度')
    expect(validatePasswordStrength('a1'.repeat(PASSWORD_MAX_LENGTH))).toContain('密码长度')
    expect(validatePasswordStrength('abcdefgh')).toBe('密码需同时包含字母和数字')
    expect(validatePasswordStrength('12345678')).toBe('密码需同时包含字母和数字')
    expect(validatePasswordStrength('abcd 1234')).toBe('密码不能包含空格')
  })
})

describe('validatePasswordConfirm', () => {
  it('两次输入必须一致', () => {
    expect(validatePasswordConfirm('', 'abcd1234')).toBe('请输入确认密码')
    expect(validatePasswordConfirm('abcd1235', 'abcd1234')).toBe('两次输入的密码不一致')
    expect(validatePasswordConfirm('abcd1234', 'abcd1234')).toBeNull()
  })
})

describe('validateNickname', () => {
  it('长度按码点计算，允许 emoji', () => {
    expect(validateNickname('小明')).toBeNull()
    expect(validateNickname('😀'.repeat(NICKNAME_MAX_LENGTH))).toBeNull()
  })

  it('拒绝空值、纯空白和超长昵称', () => {
    expect(validateNickname('')).toBe('请输入昵称')
    expect(validateNickname('   ')).toBe('请输入昵称')
    expect(validateNickname('名'.repeat(NICKNAME_MAX_LENGTH + 1))).toContain('昵称长度')
  })
})

describe('validateNewPassword', () => {
  it('仍要满足强度要求，且不能与原密码相同', () => {
    expect(validateNewPassword('abcd1234', 'abcd1234')).toBe('新密码不能与原密码相同')
    expect(validateNewPassword('abc', 'abcd1234')).toContain('密码长度')
    expect(validateNewPassword('abcd5678', 'abcd1234')).toBeNull()
  })
})

describe('firstError', () => {
  it('返回第一条错误，全部通过返回 null', () => {
    expect(firstError(null, '第二个错', '第三个错')).toBe('第二个错')
    expect(firstError(null, null)).toBeNull()
  })
})
