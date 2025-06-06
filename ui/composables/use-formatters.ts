/**
 * Shared formatting utilities used across components
 */

export const useFormatters = () => {
  const formatCurrency = (value: number | undefined | null): string => {
    if (value === null || value === undefined) return '€0.00'
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 2,
    }).format(value)
  }

  const formatNumber = (value: number | undefined | null): string => {
    if (value === null || value === undefined) return '0'
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 6,
    }).format(value)
  }

  const formatPercentage = (value: number | undefined | null): string => {
    if (value === null || value === undefined) return '0.00%'
    return `${value.toFixed(2)}%`
  }

  const formatDate = (dateString: string): string => {
    if (!dateString) return ''
    const date = new Date(dateString)
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
  }

  return {
    formatCurrency,
    formatNumber,
    formatPercentage,
    formatDate,
  }
}
