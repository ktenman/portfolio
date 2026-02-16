export const calculateTargetValue = (
  currentHoldingsTotal: number,
  totalInvestment: number,
  allocationPercent: number
): number => ((currentHoldingsTotal + totalInvestment) * allocationPercent) / 100

export const calculateInvestmentAmount = (totalInvestment: number, percentage: number): number =>
  (totalInvestment * percentage) / 100

export const calculateUnitsFromAmount = (amount: number, price: number): number =>
  price > 0 ? Math.floor(amount / price) : 0

export const formatEuroAmount = (amount: number): string =>
  amount === 0 ? '-' : `€${amount.toFixed(2)}`

export interface RebalanceAllocationEntry {
  id: number
  units: number
  isBuy: boolean
  difference: number
  unused: number
  price: number | null
}

export const optimizeRebalanceUnits = (
  entries: RebalanceAllocationEntry[]
): { allocations: Map<number, { units: number; isBuy: boolean }>; totalRemaining: number } => {
  const buyEntries = entries.filter(d => d.isBuy && d.difference > 0)
  if (buyEntries.length === 0) {
    return {
      allocations: new Map(entries.map(e => [e.id, { units: e.units, isBuy: e.isBuy }])),
      totalRemaining: 0,
    }
  }
  let totalUnusedAmount = buyEntries.reduce((sum, d) => sum + d.unused, 0)
  const result = new Map(entries.map(e => [e.id, { units: e.units, isBuy: e.isBuy }]))
  const sortedByRemainder = [...buyEntries]
    .filter(d => d.price && d.price > 0)
    .sort((a, b) => b.unused - a.unused)
  for (const fund of sortedByRemainder) {
    if (fund.price && fund.price <= totalUnusedAmount) {
      const current = result.get(fund.id)!
      result.set(fund.id, { ...current, units: current.units + 1 })
      totalUnusedAmount -= fund.price
    }
  }
  return { allocations: result, totalRemaining: Math.max(0, totalUnusedAmount) }
}

export interface InvestmentAllocationEntry {
  id: number
  price: number
  percentage: number
}

export const optimizeInvestmentAllocation = (
  entries: InvestmentAllocationEntry[],
  totalInvestment: number
): Map<number, number> => {
  const fundData = entries.map(entry => {
    const allocated = calculateInvestmentAmount(totalInvestment, entry.percentage)
    const exactUnits = entry.price > 0 ? allocated / entry.price : 0
    const baseUnits = Math.floor(exactUnits)
    return {
      id: entry.id,
      price: entry.price,
      baseUnits,
      remainder: exactUnits - baseUnits,
      currentUnits: baseUnits,
    }
  })
  let totalSpent = fundData.reduce((sum, f) => sum + f.currentUnits * f.price, 0)
  let remaining = totalInvestment - totalSpent
  const sortedByRemainder = [...fundData]
    .filter(f => f.price > 0)
    .sort((a, b) => b.remainder - a.remainder)
  for (const fund of sortedByRemainder) {
    if (fund.price <= remaining) {
      fund.currentUnits++
      remaining -= fund.price
      totalSpent += fund.price
    }
  }
  const totalPercent = entries.reduce((sum, e) => sum + e.percentage, 0)
  let improved = true
  while (improved && remaining > 0) {
    improved = false
    let bestFund: (typeof fundData)[0] | null = null
    let bestDeficit = -Infinity
    for (const fund of fundData) {
      if (fund.price <= 0 || fund.price > remaining) continue
      const currentPercent =
        totalSpent > 0 ? ((fund.currentUnits * fund.price) / totalSpent) * 100 : 0
      const targetPercent =
        ((entries.find(e => e.id === fund.id)?.percentage ?? 0) / totalPercent) * 100
      const deficit = targetPercent - currentPercent
      if (deficit > bestDeficit) {
        bestDeficit = deficit
        bestFund = fund
      }
    }
    if (bestFund) {
      bestFund.currentUnits++
      remaining -= bestFund.price
      totalSpent += bestFund.price
      improved = true
    }
  }
  const result = new Map<number, number>()
  fundData.forEach(f => result.set(f.id, f.currentUnits))
  return result
}

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
