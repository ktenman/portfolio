export const useDiversificationFormatters = () => {
  const formatPrice = (value: number | null): string => {
    if (value === null) return '-'
    return `€${value.toFixed(2)}`
  }

  const formatTer = (value: number | null, decimals: number = 2): string => {
    if (value === null) return '-'
    return `${value.toFixed(decimals)}%`
  }

  const formatReturn = (value: number | null): string => {
    if (value === null) return '-'
    return `${(value * 100).toFixed(2)}%`
  }

  const formatPercentage = (value: number): string => `${value.toFixed(2)}%`

  const formatCurrency = (value: number): string =>
    new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value)

  const formatRelativeTime = (timestampMs: number, nowMs: number): string => {
    const diffMs = nowMs - timestampMs
    const diffMinutes = Math.floor(diffMs / 60000)
    if (diffMinutes < 1) return 'just now'
    if (diffMinutes === 1) return '1 min ago'
    if (diffMinutes < 60) return `${diffMinutes} min ago`
    const diffHours = Math.floor(diffMinutes / 60)
    if (diffHours === 1) return '1 hour ago'
    return `${diffHours} hours ago`
  }

  return {
    formatPrice,
    formatTer,
    formatReturn,
    formatPercentage,
    formatCurrency,
    formatRelativeTime,
  }
}
