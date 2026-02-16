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

export interface BudgetConstrainedEntry {
  id: number
  price: number
  difference: number
  isBuy: boolean
}

export interface BudgetConstrainedResult {
  allocations: Map<number, { units: number; isBuy: boolean }>
  totalRemaining: number
}

export const calculateBudgetConstrainedRebalance = (
  entries: BudgetConstrainedEntry[],
  budget: number,
  optimize: boolean
): BudgetConstrainedResult | null => {
  const buyEntries = entries.filter(e => e.isBuy && e.difference > 0 && e.price > 0)
  const totalBuyNeeded = buyEntries.reduce((sum, e) => sum + e.difference, 0)
  if (totalBuyNeeded <= 0) return null
  const sellProceeds = entries
    .filter(e => !e.isBuy && e.price > 0)
    .reduce((sum, e) => sum + Math.floor(Math.abs(e.difference) / e.price) * e.price, 0)
  const availableBudget = budget + sellProceeds
  if (totalBuyNeeded <= availableBudget) return null
  const allocations = new Map<number, { units: number; isBuy: boolean }>()
  let totalSpent = 0
  for (const entry of buyEntries) {
    const budgetShare = (entry.difference / totalBuyNeeded) * availableBudget
    const units = Math.floor(budgetShare / entry.price)
    totalSpent += units * entry.price
    allocations.set(entry.id, { units, isBuy: true })
  }
  if (!optimize) return { allocations, totalRemaining: availableBudget - totalSpent }
  let remaining = availableBudget - totalSpent
  let improved = true
  while (improved && remaining > 0) {
    improved = false
    let bestEntry: BudgetConstrainedEntry | null = null
    let bestDeficit = -Infinity
    for (const entry of buyEntries) {
      if (entry.price > remaining) continue
      const current = allocations.get(entry.id)!
      const deficit =
        (entry.difference / totalBuyNeeded) * availableBudget - current.units * entry.price
      if (deficit > bestDeficit) {
        bestDeficit = deficit
        bestEntry = entry
      }
    }
    if (bestEntry) {
      const current = allocations.get(bestEntry.id)!
      allocations.set(bestEntry.id, { units: current.units + 1, isBuy: true })
      totalSpent += bestEntry.price
      remaining = availableBudget - totalSpent
      improved = true
    }
  }
  return { allocations, totalRemaining: availableBudget - totalSpent }
}
