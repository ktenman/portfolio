import { describe, it, expect } from 'vitest'
import { currencyFlagUrl } from './currency-flag'

describe('currencyFlagUrl', () => {
  it('returns US flag URL for USD', () => {
    expect(currencyFlagUrl('USD')).toBe('https://hatscripts.github.io/circle-flags/flags/us.svg')
  })

  it('returns EU flag URL for EUR', () => {
    expect(currencyFlagUrl('EUR')).toBe('https://hatscripts.github.io/circle-flags/flags/eu.svg')
  })

  it('is case-insensitive', () => {
    expect(currencyFlagUrl('usd')).toBe('https://hatscripts.github.io/circle-flags/flags/us.svg')
    expect(currencyFlagUrl('eUr')).toBe('https://hatscripts.github.io/circle-flags/flags/eu.svg')
  })

  it('returns null for unknown currency code', () => {
    expect(currencyFlagUrl('XYZ')).toBeNull()
  })

  it('returns null for null input', () => {
    expect(currencyFlagUrl(null)).toBeNull()
  })

  it('returns null for undefined input', () => {
    expect(currencyFlagUrl(undefined)).toBeNull()
  })

  it('returns null for empty string', () => {
    expect(currencyFlagUrl('')).toBeNull()
  })
})
