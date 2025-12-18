import { formatCurrencyWithSign } from './formatters'

export function formatProfit(amount: number, currency: string | undefined): string {
  const sign = amount >= 0 ? '' : '-'
  return sign + formatCurrencyWithSign(Math.abs(amount), currency || 'EUR')
}

export function calculatePortfolioWeight(instrumentValue: number, totalValue: number): string {
  if (totalValue === 0) return '0.00%'
  const weight = (instrumentValue / totalValue) * 100
  return `${weight.toFixed(2)}%`
}
