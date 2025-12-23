import { styleClasses } from './style-classes'
import type { InstrumentDto } from '../models/generated/domain-models'

const SUPERSCRIPT_DIGITS: Record<string, string> = {
  '-': '⁻',
  '0': '⁰',
  '1': '¹',
  '2': '²',
  '3': '³',
  '4': '⁴',
  '5': '⁵',
  '6': '⁶',
  '7': '⁷',
  '8': '⁸',
  '9': '⁹',
}

export const formatScientific = (value: number, suffix: string = ''): string => {
  const exponent = Math.floor(Math.log10(Math.abs(value)))
  const mantissa = value / Math.pow(10, exponent)
  const superscriptExp = String(exponent)
    .split('')
    .map(c => SUPERSCRIPT_DIGITS[c] || c)
    .join('')
  return `${mantissa.toFixed(2)} × 10${superscriptExp}${suffix}`
}

const ACRONYMS = [
  'ETF',
  'FT',
  'API',
  'USD',
  'EUR',
  'GBP',
  'JPY',
  'CHF',
  'CAD',
  'AUD',
  'CEO',
  'CFO',
  'IT',
  'AI',
]

export const formatAcronym = (value: string | undefined | null): string => {
  if (!value) return '-'

  if (ACRONYMS.includes(value.toUpperCase())) {
    return value.toUpperCase()
  }

  const withSpaces = value.replace(/_/g, ' ')
  return withSpaces
    .split(' ')
    .map(word => {
      if (ACRONYMS.includes(word.toUpperCase())) return word.toUpperCase()
      return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    })
    .join(' ')
}

export const formatCurrencyWithSymbol = (value: number | undefined | null): string => {
  if (value === null || value === undefined) return '€0.00'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
  }).format(value)
}

export const formatCurrency = (value: number | undefined | null): string => {
  if (value === null || value === undefined) return '0.00'
  return Math.abs(value).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

export const formatCurrencyWithSign = (
  value: number | undefined | null,
  currency?: string
): string => {
  if (value === null || value === undefined) return '0.00'

  const absValue = Math.abs(value).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  const currencySymbol = getCurrencySymbol(currency)

  return `${currencySymbol}${absValue}`
}

export const getCurrencySymbol = (currency?: string): string => {
  switch (currency?.toUpperCase()) {
    case 'EUR':
      return '€'
    case 'USD':
      return '$'
    case 'GBP':
      return '£'
    default:
      return '€'
  }
}

export const formatNumber = (value: number | undefined | null): string => {
  if (value === null || value === undefined) return ''
  if (value === 0) return '0'
  if (!isFinite(value)) return '0'

  if (Math.abs(value) >= 1) {
    return value.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
  }

  return value.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 8,
  })
}

export const formatPercentageFromDecimal = (value: number | undefined | null): string => {
  if (value === null || value === undefined) return '0.00%'
  return `${(value * 100).toFixed(2)}%`
}

export const formatProfitLoss = (
  value: number | null | undefined,
  includeSign: boolean = true
): string => {
  if (value === null || value === undefined) return '0.00'
  const formattedValue = Math.abs(value).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  if (!includeSign) return formattedValue
  const sign = value >= 0 ? '+' : '-'
  return `${sign}${formattedValue}`
}

export const formatTransactionAmount = (
  quantity: number,
  price: number,
  type: string,
  currency?: string,
  includeSign: boolean = true
): string => {
  const amount = quantity * price

  const formattedAmount = amount.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })

  const currencySymbol = getCurrencySymbol(currency)

  if (!includeSign) {
    return `${currencySymbol}${formattedAmount}`
  }

  const sign = type === 'BUY' ? '+' : '-'
  return `${sign}${currencySymbol}${formattedAmount}`
}

export const getProfitClass = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return ''
  return value >= 0 ? styleClasses.text.success : styleClasses.text.danger
}

export const getAmountClass = (type: string): string => {
  return type === 'BUY' ? styleClasses.text.success : styleClasses.text.danger
}

export const formatDate = (dateString: string): string => {
  if (!dateString) return ''
  const date = new Date(dateString)

  const day = String(date.getDate()).padStart(2, '0')
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const year = String(date.getFullYear()).slice(-2)

  return `${day}.${month}.${year}`
}

export const formatQuantity = (value: number | string | undefined | null): string => {
  if (value === null || value === undefined) return '0.00'

  const numValue = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(numValue) || !isFinite(numValue)) return '0.00'
  if (numValue === 0) return '0.00'

  if (Math.abs(numValue) < 0.01) {
    return formatScientific(numValue)
  }

  return numValue.toFixed(2)
}

export const formatPriceChange = (item: InstrumentDto): string => {
  const amount = item.priceChangeAmount
  const percent = item.priceChangePercent

  if (amount === null || amount === undefined || percent === null || percent === undefined) {
    return '-'
  }

  const currency = item.baseCurrency || 'EUR'
  const isPositive = amount >= 0
  const colorClass = isPositive ? styleClasses.text.success : styleClasses.text.danger

  const formattedAmount = formatCurrencyWithSign(Math.abs(amount), currency)
  const formattedPercent = Math.abs(percent).toFixed(2)

  return `<span class="${colorClass}">${formattedAmount} / ${formattedPercent}%</span>`
}
