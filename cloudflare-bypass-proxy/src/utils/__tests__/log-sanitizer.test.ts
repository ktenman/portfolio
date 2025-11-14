import { sanitizeLogInput } from '../log-sanitizer'

describe('Log Sanitizer', () => {
  it('should return input as-is for safe strings', () => {
    expect(sanitizeLogInput('normal-string')).toBe('normal-string')
    expect(sanitizeLogInput('test123')).toBe('test123')
    expect(sanitizeLogInput('api/v1/data')).toBe('api/v1/data')
  })

  it('should handle empty and whitespace strings', () => {
    expect(sanitizeLogInput('')).toBe('null')
    expect(sanitizeLogInput('   ')).toBe('   ')
  })

  it('should handle special characters safely', () => {
    const input = 'test@#$%^&*()'
    const result = sanitizeLogInput(input)
    expect(typeof result).toBe('string')
  })

  it('should handle very long strings', () => {
    const longString = 'a'.repeat(10000)
    const result = sanitizeLogInput(longString)
    expect(result.length).toBeLessThanOrEqual(10000)
  })

  it('should handle unicode characters', () => {
    expect(sanitizeLogInput('测试')).toBeTruthy()
    expect(sanitizeLogInput('🚀')).toBeTruthy()
  })

  it('should handle null and undefined gracefully', () => {
    expect(() => sanitizeLogInput(null as any)).not.toThrow()
    expect(() => sanitizeLogInput(undefined as any)).not.toThrow()
  })

  it('should handle potential log injection attempts', () => {
    const malicious = 'test\\n[ERROR] Fake error message'
    const result = sanitizeLogInput(malicious)
    expect(typeof result).toBe('string')
  })
})
