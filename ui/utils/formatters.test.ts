import { describe, it, expect } from 'vitest'
import {
  formatCurrencyWithSymbol,
  formatCurrency,
  formatCurrencyWithSign,
  formatSignedCurrency,
  formatSignedPercent,
  formatPrice,
  getCurrencySymbol,
  formatPercentageFromDecimal,
  formatProfitLoss,
  formatTransactionAmount,
  getProfitClass,
  getGainLossClass,
  getAmountClass,
  formatDate,
  formatQuantity,
  formatScientific,
  formatPriceChange,
} from './formatters'
import type { InstrumentDto } from '../models/generated/domain-models'

describe('formatCurrencyWithSymbol', () => {
  it.each([
    [1234.56, '€1,234.56'],
    [0.99, '€0.99'],
    [1000000, '€1,000,000.00'],
    [-1234.56, '-€1,234.56'],
    [-0.99, '-€0.99'],
    [0, '€0.00'],
    [100, '€100.00'],
    [100.1, '€100.10'],
    [100.999, '€101.00'],
    [null, '€0.00'],
    [undefined, '€0.00'],
  ] as const)('formats %s as %s', (value, expected) => {
    expect(formatCurrencyWithSymbol(value)).toBe(expected)
  })
})

describe('formatCurrency', () => {
  it.each([
    [1234.56, '1,234.56'],
    [0.99, '0.99'],
    [1000000, '1,000,000.00'],
    [-1234.56, '1,234.56'],
    [-0.99, '0.99'],
    [0, '0.00'],
    [100, '100.00'],
    [100.1, '100.10'],
    [100.999, '101.00'],
    [null, '0.00'],
    [undefined, '0.00'],
  ] as const)('formats %s as %s', (value, expected) => {
    expect(formatCurrency(value)).toBe(expected)
  })
})

describe('formatCurrencyWithSign', () => {
  it.each([
    [1234.56, undefined, '€1,234.56'],
    [1234.56, '', '€1,234.56'],
    [1234.56, 'EUR', '€1,234.56'],
    [1234.56, 'eur', '€1,234.56'],
    [1234.56, 'USD', '$1,234.56'],
    [1234.56, 'usd', '$1,234.56'],
    [1234.56, 'GBP', '£1,234.56'],
    [1234.56, 'gbp', '£1,234.56'],
    [-1234.56, 'EUR', '€1,234.56'],
    [-1234.56, 'USD', '$1,234.56'],
    [-1234.56, 'GBP', '£1,234.56'],
    [0, 'EUR', '€0.00'],
    [0, 'USD', '$0.00'],
    [100, 'EUR', '€100.00'],
    [100.1, 'USD', '$100.10'],
    [100.999, 'GBP', '£101.00'],
    [100, 'JPY', '€100.00'],
    [100, 'UNKNOWN', '€100.00'],
    [null, undefined, '0.00'],
    [undefined, undefined, '0.00'],
    [null, 'USD', '0.00'],
  ] as const)('formats %s in %s as %s', (value, currency, expected) => {
    expect(formatCurrencyWithSign(value, currency)).toBe(expected)
  })
})

describe('formatPrice', () => {
  it.each([
    [12345.67, 'EUR', '€12,346'],
    [99999.99, 'USD', '$100,000'],
    [10000, 'GBP', '£10,000'],
    [1234.56, 'EUR', '€1,234.6'],
    [9999.99, 'USD', '$10,000.0'],
    [1000, 'GBP', '£1,000.0'],
    [123.45, 'EUR', '€123.45'],
    [12.34, 'USD', '$12.34'],
    [1.23, 'GBP', '£1.23'],
    [0.12, 'EUR', '€0.12'],
    [0, 'EUR', '€0.00'],
    [0, 'USD', '$0.00'],
    [-12345.67, 'EUR', '€12,346'],
    [-1234.56, 'USD', '$1,234.6'],
    [-123.45, 'GBP', '£123.45'],
    [100, undefined, '€100.00'],
    [1234.56, undefined, '€1,234.6'],
    [12345.67, undefined, '€12,346'],
    [999.99, 'EUR', '€999.99'],
    [1000, 'EUR', '€1,000.0'],
    [9999.9, 'EUR', '€9,999.9'],
    [10000, 'EUR', '€10,000'],
    [null, undefined, '0.00'],
    [undefined, undefined, '0.00'],
    [null, 'USD', '0.00'],
  ] as const)('formats %s in %s as %s', (value, currency, expected) => {
    expect(formatPrice(value, currency)).toBe(expected)
  })
})

describe('getCurrencySymbol', () => {
  it.each([
    ['EUR', '€'],
    ['eur', '€'],
    ['USD', '$'],
    ['usd', '$'],
    ['GBP', '£'],
    ['gbp', '£'],
    ['', '€'],
    ['UNKNOWN', '€'],
    ['JPY', '€'],
    [undefined, '€'],
  ] as const)('symbolises %s as %s', (currency, expected) => {
    expect(getCurrencySymbol(currency)).toBe(expected)
  })
})

describe('formatPercentageFromDecimal', () => {
  it.each([
    [0.1, '10.00%'],
    [0.1234, '12.34%'],
    [1, '100.00%'],
    [1.5, '150.00%'],
    [-0.1, '-10.00%'],
    [-0.5, '-50.00%'],
    [0, '0.00%'],
    [0.12345, '12.35%'],
    [0.12344, '12.34%'],
    [null, 'N/A'],
    [undefined, 'N/A'],
  ] as const)('formats %s as %s', (value, expected) => {
    expect(formatPercentageFromDecimal(value)).toBe(expected)
  })
})

describe('formatProfitLoss', () => {
  it.each([
    [100, '+100.00'],
    [1234.56, '+1,234.56'],
    [0.01, '+0.01'],
    [-100, '-100.00'],
    [-1234.56, '-1,234.56'],
    [-0.01, '-0.01'],
    [0, '+0.00'],
    [null, '0.00'],
    [undefined, '0.00'],
  ] as const)('formats %s as %s', (value, expected) => {
    expect(formatProfitLoss(value)).toBe(expected)
  })
})

describe('formatTransactionAmount', () => {
  it.each([
    [10, 100, 'BUY', undefined, '+€1,000.00'],
    [5.5, 20.5, 'BUY', undefined, '+€112.75'],
    [1, 0.01, 'BUY', undefined, '+€0.01'],
    [10, 100, 'SELL', undefined, '-€1,000.00'],
    [5.5, 20.5, 'SELL', undefined, '-€112.75'],
    [1, 0.01, 'SELL', undefined, '-€0.01'],
    [0, 100, 'BUY', undefined, '+€0.00'],
    [10, 0, 'SELL', undefined, '-€0.00'],
    [10, 100, 'BUY', 'USD', '+$1,000.00'],
    [10, 100, 'SELL', 'GBP', '-£1,000.00'],
  ] as const)('formats %s × %s %s in %s as %s', (quantity, price, type, currency, expected) => {
    expect(formatTransactionAmount(quantity, price, type, currency)).toBe(expected)
  })
})

describe('getProfitClass', () => {
  it.each([
    [100, 'text-gain'],
    [0.01, 'text-gain'],
    [0, 'text-gain'],
    [-100, 'text-loss'],
    [-0.01, 'text-loss'],
    [null, ''],
    [undefined, ''],
  ] as const)('classifies %s as %s', (value, expected) => {
    expect(getProfitClass(value)).toBe(expected)
  })
})

describe('getGainLossClass', () => {
  it.each([
    [100, 'text-gain'],
    [0.01, 'text-gain'],
    [-100, 'text-loss'],
    [-0.01, 'text-loss'],
    [0, ''],
    [null, ''],
    [undefined, ''],
  ] as const)('classifies %s as %s', (value, expected) => {
    expect(getGainLossClass(value)).toBe(expected)
  })
})

describe('getAmountClass', () => {
  it.each([
    ['BUY', 'text-gain'],
    ['SELL', 'text-loss'],
    ['OTHER', 'text-loss'],
    ['', 'text-loss'],
  ] as const)('classifies %s as %s', (type, expected) => {
    expect(getAmountClass(type)).toBe(expected)
  })
})

describe('formatSignedCurrency', () => {
  it.each([
    [100, 'EUR', '€100.00'],
    [1234567.89, 'EUR', '€1,234,567.89'],
    [-100, 'EUR', '−€100.00'],
    [-1234567.89, 'EUR', '−€1,234,567.89'],
    [0, 'EUR', '€0.00'],
    [50, 'USD', '$50.00'],
    [-50, 'GBP', '−£50.00'],
    [123.456, 'EUR', '€123.46'],
    [75, undefined, '€75.00'],
  ] as const)('formats %s in %s as %s', (value, currency, expected) => {
    expect(formatSignedCurrency(value, currency)).toBe(expected)
  })
})

describe('formatSignedPercent', () => {
  it.each([
    [17.16, '17.16%'],
    [-8.46, '−8.46%'],
    [0, '0.00%'],
  ] as const)('formats %s as %s', (value, expected) => {
    expect(formatSignedPercent(value)).toBe(expected)
  })
})

describe('formatDate', () => {
  it.each([
    ['2023-01-15', '15.01.23'],
    ['2023-12-31', '31.12.23'],
    ['2023-02-05', '05.02.23'],
    ['2023-01-15T10:30:00', '15.01.23'],
    ['2023-12-31T23:59:59.999', '31.12.23'],
    ['2023/01/15', '15.01.23'],
    ['01/15/2023', '15.01.23'],
    ['2099-12-31', '31.12.99'],
    ['2100-01-01', '01.01.00'],
    ['', ''],
  ] as const)('formats %s as %s', (dateString, expected) => {
    expect(formatDate(dateString)).toBe(expected)
  })
})

describe('formatQuantity', () => {
  it.each([
    [176.73, '176.73'],
    [100, '100.00'],
    [999.99, '999.99'],
    [1234.5678, '1234.57'],
    [84.446, '84.446'],
    [10, '10.000'],
    [99.999, '99.999'],
    [45.1234, '45.123'],
    [5.25, '5.2500'],
    [0.1331, '0.1331'],
    [0.01332304, '0.0133'],
    [1.2345, '1.2345'],
    [9.9999, '9.9999'],
    [0.0001, '0.0001'],
    [0.00009999, '10.00 × 10⁻⁵'],
    [0.00001, '1.00 × 10⁻⁵'],
    [0.0000123456, '1.23 × 10⁻⁵'],
    [0.000000001, '1.00 × 10⁻⁹'],
    [0.0000000001, '1.00 × 10⁻¹⁰'],
    [-176.73, '-176.73'],
    [-84.446, '-84.446'],
    [-0.1331, '-0.1331'],
    [-0.00001, '-1.00 × 10⁻⁵'],
    [0, '0.0000'],
    [null, '0.0000'],
    [undefined, '0.0000'],
  ] as const)('formats %s as %s', (value, expected) => {
    expect(formatQuantity(value)).toBe(expected)
  })
})

describe('formatScientific', () => {
  it.each([
    [0.001, undefined, '1.00 × 10⁻³'],
    [0.00001, undefined, '1.00 × 10⁻⁵'],
    [0.0000123, undefined, '1.23 × 10⁻⁵'],
    [-0.001, undefined, '-1.00 × 10⁻³'],
    [-0.00005, undefined, '-5.00 × 10⁻⁵'],
    [0.0000000001, undefined, '1.00 × 10⁻¹⁰'],
    [0.00000000001, undefined, '1.00 × 10⁻¹¹'],
    [0.0001, '%', '1.00 × 10⁻⁴%'],
    [0.00005, ' units', '5.00 × 10⁻⁵ units'],
  ] as const)('formats %s with suffix %s as %s', (value, suffix, expected) => {
    expect(formatScientific(value, suffix)).toBe(expected)
  })
})

describe('formatPriceChange', () => {
  const item = {
    priceChangeAmount: 1.23,
    priceChangePercent: 4.56,
    baseCurrency: 'EUR',
  } as InstrumentDto

  it('should return text without markup', () => {
    expect(formatPriceChange(item)).not.toContain('<')
  })

  it('should leave both halves of a gain unsigned', () => {
    expect(formatPriceChange(item)).toBe('€1.23 / 4.56%')
  })

  it('should sign both halves of a loss', () => {
    expect(
      formatPriceChange({ ...item, priceChangeAmount: -1.23, priceChangePercent: -4.56 })
    ).toBe('−€1.23 / −4.56%')
  })

  it('should return a dash when the change is unknown', () => {
    expect(formatPriceChange({ ...item, priceChangeAmount: null })).toBe('-')
  })
})
