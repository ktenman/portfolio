import { styleClasses } from './style-classes'

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

export const formatProfitLoss = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return '0.00'
  const formattedValue = Math.abs(value).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  return value >= 0 ? `+${formattedValue}` : `-${formattedValue}`
}

export const formatTransactionAmount = (
  quantity: number,
  price: number,
  type: string,
  commission?: number,
  currency?: string
): string => {
  const baseAmount = quantity * price
  const commissionValue = commission || 0
  const totalAmount = type === 'BUY' ? baseAmount + commissionValue : baseAmount - commissionValue

  const formattedAmount = totalAmount.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })

  const currencySymbol = getCurrencySymbol(currency)
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
    const exponent = Math.floor(Math.log10(Math.abs(numValue)))
    const mantissa = numValue / Math.pow(10, exponent)
    return `${mantissa.toFixed(2)} × 10^${exponent}`
  }

  return numValue.toFixed(2)
}
