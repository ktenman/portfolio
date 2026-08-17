export function calculatePortfolioWeight(instrumentValue: number, totalValue: number): string {
  if (totalValue === 0) return '0.00%'
  const weight = (instrumentValue / totalValue) * 100
  return `${weight.toFixed(2)}%`
}
