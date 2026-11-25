/**
 * 表单校验规则 · 维护人：M05
 *
 * 纯函数，不依赖 uni 运行时，便于 Vitest 覆盖和跨页面复用。
 * 约定：返回 `null` 表示通过，返回字符串表示可直接展示的中文错误提示。
 *
 * 注意：这里是前端预校验，只用于提升体验；服务端仍是权限与数据规则的最终判定方
 * （见 specs/design.md 第 8 节）。账号/密码的取值规则需与 M01 的认证实现保持一致，
 * 当前规则已写入 specs/contracts/miniapp-module.md，改动前先与 M01 对齐。
 */

/** 账号：4–20 位字母、数字或下划线。 */
export const ACCOUNT_PATTERN = /^[A-Za-z0-9_]{4,20}$/
export const ACCOUNT_MIN_LENGTH = 4
export const ACCOUNT_MAX_LENGTH = 20

/** 密码：8–32 位，且同时包含字母和数字。 */
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 32
export const PASSWORD_LETTER_PATTERN = /[A-Za-z]/
export const PASSWORD_DIGIT_PATTERN = /\d/
export const PASSWORD_WHITESPACE_PATTERN = /\s/

/** 昵称：1–20 个 Unicode 码点（服务端同样按码点计数）。 */
export const NICKNAME_MIN_LENGTH = 1
export const NICKNAME_MAX_LENGTH = 20

/** 按 Unicode 码点计数，避免 emoji 被算成 2 个字符。 */
export function countChars(value: string): number {
  return Array.from(value).length
}

export type ValidationResult = string | null

export function validateRequired(value: string, label: string): ValidationResult {
  if (value.trim().length === 0) {
    return `请输入${label}`
  }
  return null
}

export function validateAccount(value: string): ValidationResult {
  const required = validateRequired(value, '账号')
  if (required) {
    return required
  }
  if (value.length < ACCOUNT_MIN_LENGTH || value.length > ACCOUNT_MAX_LENGTH) {
    return `账号长度为 ${ACCOUNT_MIN_LENGTH}–${ACCOUNT_MAX_LENGTH} 位`
  }
  if (!ACCOUNT_PATTERN.test(value)) {
    return '账号只能包含字母、数字和下划线'
  }
  return null
}

/** 登录时只做必填与长度校验，具体强度由服务端判定，避免旧账号被前端拦住。 */
export function validateLoginPassword(value: string): ValidationResult {
  const required = validateRequired(value, '密码')
  if (required) {
    return required
  }
  if (countChars(value) > PASSWORD_MAX_LENGTH) {
    return `密码长度不能超过 ${PASSWORD_MAX_LENGTH} 位`
  }
  return null
}

/** 注册/改密的强度校验：8–32 位，同时包含字母和数字，不含空白字符。 */
export function validatePasswordStrength(value: string): ValidationResult {
  const required = validateRequired(value, '密码')
  if (required) {
    return required
  }
  const length = countChars(value)
  if (length < PASSWORD_MIN_LENGTH || length > PASSWORD_MAX_LENGTH) {
    return `密码长度为 ${PASSWORD_MIN_LENGTH}–${PASSWORD_MAX_LENGTH} 位`
  }
  if (PASSWORD_WHITESPACE_PATTERN.test(value)) {
    return '密码不能包含空格'
  }
  if (!PASSWORD_LETTER_PATTERN.test(value) || !PASSWORD_DIGIT_PATTERN.test(value)) {
    return '密码需同时包含字母和数字'
  }
  return null
}

export function validatePasswordConfirm(value: string, original: string): ValidationResult {
  const required = validateRequired(value, '确认密码')
  if (required) {
    return required
  }
  if (value !== original) {
    return '两次输入的密码不一致'
  }
  return null
}

export function validateNickname(value: string): ValidationResult {
  const required = validateRequired(value, '昵称')
  if (required) {
    return required
  }
  const length = countChars(value.trim())
  if (length < NICKNAME_MIN_LENGTH || length > NICKNAME_MAX_LENGTH) {
    return `昵称长度为 ${NICKNAME_MIN_LENGTH}–${NICKNAME_MAX_LENGTH} 个字符`
  }
  return null
}

/** 改密时的新密码：强度校验之外，不允许与原密码相同。 */
export function validateNewPassword(value: string, oldPassword: string): ValidationResult {
  const strength = validatePasswordStrength(value)
  if (strength) {
    return strength
  }
  if (value === oldPassword) {
    return '新密码不能与原密码相同'
  }
  return null
}

/** 依次执行校验，返回第一条错误；全部通过返回 null。 */
export function firstError(...results: ValidationResult[]): ValidationResult {
  for (const result of results) {
    if (result) {
      return result
    }
  }
  return null
}
