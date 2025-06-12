export const useFormatters = () => {
  const formatCurrencyWithSymbol = (value: number | undefined | null): string => {
    if (value === null || value === undefined) return '€0.00'
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 2,
    }).format(value)
  }

  const formatCurrency = (value: number | undefined | null): string => {
    if (value === null || value === undefined) return '0.00'
    return Math.abs(value).toFixed(2)
  }

  const formatNumber = (value: number | undefined | null): string => {
    if (value === null || value === undefined) return ''

    if (value < 1 && value > 0) {
      return value.toExponential(3).replace('e-', ' * 10^-')
    }

    const [integerPart] = value.toString().split('.')
    const integerDigits = integerPart.length

    if (integerDigits === 1) {
      return value.toFixed(3)
    } else {
      return value.toFixed(2)
    }
  }

  const formatPercentage = (value: number | undefined | null): string => {
    if (value === null || value === undefined) return '0.00%'
    return `${value.toFixed(2)}%`
  }

  const formatPercentageFromDecimal = (value: number | undefined | null): string => {
    if (value === null || value === undefined) return '0.00%'
    return `${(value * 100).toFixed(2)}%`
  }

  const formatProfitLoss = (value: number | null | undefined): string => {
    if (value === null || value === undefined) return '0.00'
    const formattedValue = Math.abs(value).toFixed(2)
    return value >= 0 ? `+${formattedValue}` : `-${formattedValue}`
  }

  const formatTransactionAmount = (quantity: number, price: number, type: string): string => {
    const amount = quantity * price
    const formattedAmount = amount.toFixed(2)
    return type === 'BUY' ? `+${formattedAmount}` : `-${formattedAmount}`
  }

  const getProfitClass = (value: number | null | undefined): string => {
    if (value === null || value === undefined) return ''
    return value >= 0 ? 'text-success' : 'text-danger'
  }

  const getAmountClass = (type: string): string => {
    return type === 'BUY' ? 'text-success' : 'text-danger'
  }

  const formatDate = (dateString: string): string => {
    if (!dateString) return ''
    const date = new Date(dateString)

    const day = String(date.getDate()).padStart(2, '0')
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const year = String(date.getFullYear()).slice(-2)

    return `${day}.${month}.${year}`
  }

  return {
    formatCurrencyWithSymbol,
    formatCurrency,
    formatNumber,
    formatPercentage,
    formatPercentageFromDecimal,
    formatProfitLoss,
    formatTransactionAmount,
    getProfitClass,
    getAmountClass,
    formatDate,
  }
}
