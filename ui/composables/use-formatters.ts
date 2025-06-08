/**
 * Shared formatting utilities used across components
 */

export const useFormatters = () => {
  const formatCurrency = (value: number | undefined | null): string => {
    if (value === null || value === undefined) return '€0.00'
    return `€${Math.abs(value).toFixed(2)}`
  }

  const formatNumber = (value: number | undefined | null): string => {
    if (value === undefined || value === null) return ''
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
    return `${(value * 100).toFixed(2)}%`
  }

  const formatDate = (dateString: string): string => {
    if (!dateString) return ''
    const dateObj = new Date(dateString)
    const day = String(dateObj.getDate()).padStart(2, '0')
    const month = String(dateObj.getMonth() + 1).padStart(2, '0')
    const year = String(dateObj.getFullYear()).slice(-2)
    return `${day}.${month}.${year}`
  }

  const formatProfitLoss = (value: number | null | undefined): string => {
    if (value === null || value === undefined) return '0.00'
    const formattedValue = Math.abs(value).toFixed(2)
    return value >= 0 ? `+${formattedValue}` : `-${formattedValue}`
  }

  return {
    formatCurrency,
    formatNumber,
    formatPercentage,
    formatDate,
    formatProfitLoss,
  }
}
