import { describe, it, expect } from 'vitest'
import {
  formatCurrencyWithSymbol,
  formatCurrency,
  formatNumber,
  formatPercentageFromDecimal,
  formatProfitLoss,
  formatTransactionAmount,
  getProfitClass,
  getAmountClass,
  formatDate,
} from './formatters'

describe('formatCurrencyWithSymbol', () => {
  it('should format positive numbers with EUR symbol', () => {
    expect(formatCurrencyWithSymbol(1234.56)).toBe('€1,234.56')
    expect(formatCurrencyWithSymbol(0.99)).toBe('€0.99')
    expect(formatCurrencyWithSymbol(1000000)).toBe('€1,000,000.00')
  })

  it('should format negative numbers with EUR symbol', () => {
    expect(formatCurrencyWithSymbol(-1234.56)).toBe('-€1,234.56')
    expect(formatCurrencyWithSymbol(-0.99)).toBe('-€0.99')
  })

  it('should handle null and undefined values', () => {
    expect(formatCurrencyWithSymbol(null)).toBe('€0.00')
    expect(formatCurrencyWithSymbol(undefined)).toBe('€0.00')
  })

  it('should handle zero value', () => {
    expect(formatCurrencyWithSymbol(0)).toBe('€0.00')
  })

  it('should always show 2 decimal places', () => {
    expect(formatCurrencyWithSymbol(100)).toBe('€100.00')
    expect(formatCurrencyWithSymbol(100.1)).toBe('€100.10')
    expect(formatCurrencyWithSymbol(100.999)).toBe('€101.00')
  })
})

describe('formatCurrency', () => {
  it('should format positive numbers without symbol', () => {
    expect(formatCurrency(1234.56)).toBe('1234.56')
    expect(formatCurrency(0.99)).toBe('0.99')
    expect(formatCurrency(1000000)).toBe('1000000.00')
  })

  it('should format negative numbers as absolute values', () => {
    expect(formatCurrency(-1234.56)).toBe('1234.56')
    expect(formatCurrency(-0.99)).toBe('0.99')
  })

  it('should handle null and undefined values', () => {
    expect(formatCurrency(null)).toBe('0.00')
    expect(formatCurrency(undefined)).toBe('0.00')
  })

  it('should handle zero value', () => {
    expect(formatCurrency(0)).toBe('0.00')
  })

  it('should always show 2 decimal places', () => {
    expect(formatCurrency(100)).toBe('100.00')
    expect(formatCurrency(100.1)).toBe('100.10')
    expect(formatCurrency(100.999)).toBe('101.00')
  })
})

describe('formatNumber', () => {
  it('should format numbers >= 1 with 2 decimal places', () => {
    expect(formatNumber(1234.56789)).toBe('1,234.57')
    expect(formatNumber(1)).toBe('1.00')
    expect(formatNumber(999999.999)).toBe('1,000,000.00')
  })

  it('should format numbers < 1 with up to 8 decimal places', () => {
    expect(formatNumber(0.12345678)).toBe('0.12345678')
    expect(formatNumber(0.1)).toBe('0.10')
    expect(formatNumber(0.00000001)).toBe('0.00000001')
    expect(formatNumber(0.123456789)).toBe('0.12345679')
  })

  it('should format negative numbers', () => {
    expect(formatNumber(-1234.56)).toBe('-1,234.56')
    expect(formatNumber(-0.12345678)).toBe('-0.12345678')
  })

  it('should handle special cases', () => {
    expect(formatNumber(null)).toBe('')
    expect(formatNumber(undefined)).toBe('')
    expect(formatNumber(0)).toBe('0')
    expect(formatNumber(Infinity)).toBe('0')
    expect(formatNumber(-Infinity)).toBe('0')
    expect(formatNumber(NaN)).toBe('0')
  })
})

describe('formatPercentageFromDecimal', () => {
  it('should convert decimal to percentage', () => {
    expect(formatPercentageFromDecimal(0.1)).toBe('10.00%')
    expect(formatPercentageFromDecimal(0.1234)).toBe('12.34%')
    expect(formatPercentageFromDecimal(1)).toBe('100.00%')
    expect(formatPercentageFromDecimal(1.5)).toBe('150.00%')
  })

  it('should handle negative percentages', () => {
    expect(formatPercentageFromDecimal(-0.1)).toBe('-10.00%')
    expect(formatPercentageFromDecimal(-0.5)).toBe('-50.00%')
  })

  it('should handle null and undefined values', () => {
    expect(formatPercentageFromDecimal(null)).toBe('0.00%')
    expect(formatPercentageFromDecimal(undefined)).toBe('0.00%')
  })

  it('should handle zero value', () => {
    expect(formatPercentageFromDecimal(0)).toBe('0.00%')
  })

  it('should round to 2 decimal places', () => {
    expect(formatPercentageFromDecimal(0.12345)).toBe('12.35%')
    expect(formatPercentageFromDecimal(0.12344)).toBe('12.34%')
  })
})

describe('formatProfitLoss', () => {
  it('should format positive values with + sign', () => {
    expect(formatProfitLoss(100)).toBe('+100.00')
    expect(formatProfitLoss(1234.56)).toBe('+1234.56')
    expect(formatProfitLoss(0.01)).toBe('+0.01')
  })

  it('should format negative values with - sign', () => {
    expect(formatProfitLoss(-100)).toBe('-100.00')
    expect(formatProfitLoss(-1234.56)).toBe('-1234.56')
    expect(formatProfitLoss(-0.01)).toBe('-0.01')
  })

  it('should handle zero as positive', () => {
    expect(formatProfitLoss(0)).toBe('+0.00')
  })

  it('should handle null and undefined values', () => {
    expect(formatProfitLoss(null)).toBe('0.00')
    expect(formatProfitLoss(undefined)).toBe('0.00')
  })
})

describe('formatTransactionAmount', () => {
  it('should format BUY transactions with + sign', () => {
    expect(formatTransactionAmount(10, 100, 'BUY')).toBe('+1000.00')
    expect(formatTransactionAmount(5.5, 20.5, 'BUY')).toBe('+112.75')
    expect(formatTransactionAmount(1, 0.01, 'BUY')).toBe('+0.01')
  })

  it('should format SELL transactions with - sign', () => {
    expect(formatTransactionAmount(10, 100, 'SELL')).toBe('-1000.00')
    expect(formatTransactionAmount(5.5, 20.5, 'SELL')).toBe('-112.75')
    expect(formatTransactionAmount(1, 0.01, 'SELL')).toBe('-0.01')
  })

  it('should handle zero values', () => {
    expect(formatTransactionAmount(0, 100, 'BUY')).toBe('+0.00')
    expect(formatTransactionAmount(10, 0, 'SELL')).toBe('-0.00')
  })
})

describe('getProfitClass', () => {
  it('should return success class for positive values', () => {
    expect(getProfitClass(100)).toBe('text-success')
    expect(getProfitClass(0.01)).toBe('text-success')
    expect(getProfitClass(0)).toBe('text-success')
  })

  it('should return danger class for negative values', () => {
    expect(getProfitClass(-100)).toBe('text-danger')
    expect(getProfitClass(-0.01)).toBe('text-danger')
  })

  it('should handle null and undefined values', () => {
    expect(getProfitClass(null)).toBe('')
    expect(getProfitClass(undefined)).toBe('')
  })
})

describe('getAmountClass', () => {
  it('should return success class for BUY type', () => {
    expect(getAmountClass('BUY')).toBe('text-success')
  })

  it('should return danger class for SELL type', () => {
    expect(getAmountClass('SELL')).toBe('text-danger')
  })

  it('should handle other transaction types', () => {
    expect(getAmountClass('OTHER')).toBe('text-danger')
    expect(getAmountClass('')).toBe('text-danger')
  })
})

describe('formatDate', () => {
  it('should format valid date strings to DD.MM.YY', () => {
    expect(formatDate('2023-01-15')).toBe('15.01.23')
    expect(formatDate('2023-12-31')).toBe('31.12.23')
    expect(formatDate('2023-02-05')).toBe('05.02.23')
  })

  it('should format date with time component', () => {
    expect(formatDate('2023-01-15T10:30:00')).toBe('15.01.23')
    expect(formatDate('2023-12-31T23:59:59.999')).toBe('31.12.23')
  })

  it('should handle different date formats', () => {
    expect(formatDate('2023/01/15')).toBe('15.01.23')
    expect(formatDate('01/15/2023')).toBe('15.01.23')
  })

  it('should handle empty or invalid date strings', () => {
    expect(formatDate('')).toBe('')
  })

  it('should handle years with 2 and 4 digits', () => {
    expect(formatDate('2023-01-15')).toBe('15.01.23')
    expect(formatDate('2099-12-31')).toBe('31.12.99')
    expect(formatDate('2100-01-01')).toBe('01.01.00')
  })
})
