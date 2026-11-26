export const POST_TEXT_MAX_LENGTH = 10_000

export interface TextValidationResult {
  valid: boolean
  normalized: string
  length: number
  message: string
}

export function countCodePoints(value: string): number {
  return Array.from(value).length
}

export function normalizePostText(value: string): string {
  return value.trim()
}

export function validateTextOnlyPost(value: string): TextValidationResult {
  const normalized = normalizePostText(value)
  const length = countCodePoints(normalized)

  if (length === 0) {
    return {
      valid: false,
      normalized,
      length,
      message: '请输入动态正文后再发布',
    }
  }

  if (length > POST_TEXT_MAX_LENGTH) {
    return {
      valid: false,
      normalized,
      length,
      message: `正文最多 ${POST_TEXT_MAX_LENGTH} 个字符，当前 ${length} 个`,
    }
  }

  return { valid: true, normalized, length, message: '' }
}

export function validateEditedText(value: string, mediaCount: number): TextValidationResult {
  const normalized = normalizePostText(value)
  const length = countCodePoints(normalized)

  if (length > POST_TEXT_MAX_LENGTH) {
    return {
      valid: false,
      normalized,
      length,
      message: `正文最多 ${POST_TEXT_MAX_LENGTH} 个字符，当前 ${length} 个`,
    }
  }

  if (length === 0 && mediaCount === 0) {
    return {
      valid: false,
      normalized,
      length,
      message: '文字和媒体至少保留一项',
    }
  }

  return { valid: true, normalized, length, message: '' }
}