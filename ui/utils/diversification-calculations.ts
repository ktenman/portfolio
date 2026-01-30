export const calculateTargetValue = (
  currentHoldingsTotal: number,
  totalInvestment: number,
  allocationPercent: number
): number => ((currentHoldingsTotal + totalInvestment) * allocationPercent) / 100

export const calculateInvestmentAmount = (totalInvestment: number, percentage: number): number =>
  (totalInvestment * percentage) / 100

export const calculateRebalanceDifference = (currentValue: number, targetValue: number): number =>
  Math.abs(targetValue - currentValue)

export const calculateUnitsFromAmount = (amount: number, price: number): number =>
  price > 0 ? Math.floor(amount / price) : 0

export const formatEuroAmount = (amount: number): string =>
  amount === 0 ? '-' : `€${amount.toFixed(2)}`
