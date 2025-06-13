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
  return Math.abs(value).toFixed(2)
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
  const formattedValue = Math.abs(value).toFixed(2)
  return value >= 0 ? `+${formattedValue}` : `-${formattedValue}`
}

export const formatTransactionAmount = (quantity: number, price: number, type: string): string => {
  const amount = quantity * price
  const formattedAmount = amount.toFixed(2)
  return type === 'BUY' ? `+${formattedAmount}` : `-${formattedAmount}`
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
