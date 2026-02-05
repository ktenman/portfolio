export interface CalculatorInput {
  initialWorth: number
  monthlyInvestment: number
  yearlyGrowthRate: number
  annualReturnRate: number
  years: number
  taxRate: number
}

export interface YearSummary {
  year: number
  totalInvested: number
  totalWorth: number
  grossProfit: number
  taxAmount: number
  netWorth: number
  monthlyEarnings: number
}

interface ProjectionResult {
  yearSummaries: YearSummary[]
  portfolioData: number[]
}

export function annualToMonthlyReturnRate(annualRate: number): number {
  return Math.pow(1 + annualRate / 100, 1 / 12) - 1
}

export function annualToMonthlyGrowthRate(yearlyGrowthRate: number): number {
  return yearlyGrowthRate / 100 / 12
}

export function calculateTaxAmount(grossProfit: number, taxRate: number): number {
  return grossProfit * (taxRate / 100)
}

export function calculateNetWorth(
  totalInvested: number,
  grossProfit: number,
  taxAmount: number
): number {
  return totalInvested + grossProfit - taxAmount
}

export function calculateMonthlyEarnings(totalWorth: number, annualReturnRate: number): number {
  return (totalWorth * annualReturnRate) / 100 / 12
}

export function simulateYear(
  startingWorth: number,
  startingInvested: number,
  monthlyInvestment: number,
  monthlyReturnRate: number,
  taxRate: number,
  yearNumber: number,
  annualReturnRate: number
): { summary: YearSummary; endingWorth: number; endingInvested: number } {
  let totalWorth = startingWorth
  let yearlyInvestmentAmount = 0

  for (let month = 1; month <= 12; month++) {
    yearlyInvestmentAmount += monthlyInvestment
    totalWorth += monthlyInvestment
    totalWorth *= 1 + monthlyReturnRate
  }

  const totalInvested = startingInvested + yearlyInvestmentAmount
  const grossProfit = totalWorth - totalInvested
  const taxAmount = calculateTaxAmount(grossProfit, taxRate)
  const grossTotalWorth = totalInvested + grossProfit
  const netWorth = calculateNetWorth(totalInvested, grossProfit, taxAmount)
  const monthlyEarnings = calculateMonthlyEarnings(grossTotalWorth, annualReturnRate)

  return {
    summary: {
      year: yearNumber,
      totalInvested,
      totalWorth: grossTotalWorth,
      grossProfit,
      taxAmount,
      netWorth,
      monthlyEarnings,
    },
    endingWorth: totalWorth,
    endingInvested: totalInvested,
  }
}

export function calculateProjection(input: CalculatorInput): ProjectionResult {
  const monthlyGrowthRate = annualToMonthlyGrowthRate(input.yearlyGrowthRate)
  const monthlyReturnRate = annualToMonthlyReturnRate(input.annualReturnRate)

  const yearSummaries: YearSummary[] = []
  const portfolioData: number[] = [input.initialWorth]

  let totalWorth = input.initialWorth
  let totalInvested = input.initialWorth
  let currentMonthlyInvestment = input.monthlyInvestment

  for (let year = 1; year <= input.years; year++) {
    const result = simulateYear(
      totalWorth,
      totalInvested,
      currentMonthlyInvestment,
      monthlyReturnRate,
      input.taxRate,
      year,
      input.annualReturnRate
    )

    yearSummaries.push(result.summary)
    portfolioData.push(result.summary.totalWorth)

    totalWorth = result.endingWorth
    totalInvested = result.endingInvested
    currentMonthlyInvestment *= 1 + monthlyGrowthRate
  }

  return { yearSummaries, portfolioData }
}
