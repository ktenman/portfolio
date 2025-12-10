import { describe, it, expect } from 'vitest'
import {
  annualToMonthlyReturnRate,
  annualToMonthlyGrowthRate,
  calculateTaxAmount,
  calculateNetWorth,
  calculateMonthlyEarnings,
  simulateYear,
  calculateProjection,
  CalculatorInput,
} from './portfolio-growth-calculator'

describe('portfolio-growth-calculator', () => {
  describe('annualToMonthlyReturnRate', () => {
    it('should convert 12% annual to monthly rate', () => {
      const monthlyRate = annualToMonthlyReturnRate(12)
      const expectedRate = Math.pow(1.12, 1 / 12) - 1
      expect(monthlyRate).toBeCloseTo(expectedRate, 10)
    })

    it('should return 0 for 0% annual rate', () => {
      expect(annualToMonthlyReturnRate(0)).toBe(0)
    })

    it('should handle 100% annual rate', () => {
      const monthlyRate = annualToMonthlyReturnRate(100)
      const expectedRate = Math.pow(2, 1 / 12) - 1
      expect(monthlyRate).toBeCloseTo(expectedRate, 10)
    })
  })

  describe('annualToMonthlyGrowthRate', () => {
    it('should convert 12% yearly to 1% monthly', () => {
      expect(annualToMonthlyGrowthRate(12)).toBeCloseTo(0.01, 10)
    })

    it('should convert 5% yearly to correct monthly rate', () => {
      expect(annualToMonthlyGrowthRate(5)).toBeCloseTo(5 / 100 / 12, 10)
    })

    it('should return 0 for 0% yearly rate', () => {
      expect(annualToMonthlyGrowthRate(0)).toBe(0)
    })
  })

  describe('calculateTaxAmount', () => {
    it('should calculate 22% tax on profit', () => {
      expect(calculateTaxAmount(1000, 22)).toBe(220)
    })

    it('should return 0 when tax rate is 0', () => {
      expect(calculateTaxAmount(1000, 0)).toBe(0)
    })

    it('should handle zero profit', () => {
      expect(calculateTaxAmount(0, 22)).toBe(0)
    })

    it('should handle negative profit', () => {
      expect(calculateTaxAmount(-500, 22)).toBe(-110)
    })
  })

  describe('calculateNetWorth', () => {
    it('should calculate net worth correctly', () => {
      const netWorth = calculateNetWorth(10000, 2000, 440)
      expect(netWorth).toBe(11560)
    })

    it('should handle zero values', () => {
      expect(calculateNetWorth(0, 0, 0)).toBe(0)
    })
  })

  describe('calculateMonthlyEarnings', () => {
    it('should divide net profit by 12', () => {
      expect(calculateMonthlyEarnings(1200)).toBe(100)
    })

    it('should handle zero profit', () => {
      expect(calculateMonthlyEarnings(0)).toBe(0)
    })

    it('should handle decimal results', () => {
      expect(calculateMonthlyEarnings(1000)).toBeCloseTo(83.333, 2)
    })
  })

  describe('simulateYear', () => {
    it('should simulate a year with no growth', () => {
      const result = simulateYear(10000, 10000, 100, 0, 22, 1)

      expect(result.summary.year).toBe(1)
      expect(result.summary.totalInvested).toBe(11200)
      expect(result.summary.totalWorth).toBe(11200)
      expect(result.summary.grossProfit).toBe(0)
      expect(result.summary.taxAmount).toBe(0)
      expect(result.summary.netWorth).toBe(11200)
    })

    it('should simulate a year with positive return', () => {
      const monthlyRate = annualToMonthlyReturnRate(12)
      const result = simulateYear(10000, 10000, 0, monthlyRate, 22, 1)

      expect(result.summary.year).toBe(1)
      expect(result.summary.totalInvested).toBe(10000)
      expect(result.summary.grossProfit).toBeGreaterThan(0)
      expect(result.summary.taxAmount).toBeCloseTo(result.summary.grossProfit * 0.22, 2)
    })

    it('should track ending worth and invested amounts', () => {
      const result = simulateYear(5000, 5000, 200, 0, 0, 1)

      expect(result.endingWorth).toBe(7400)
      expect(result.endingInvested).toBe(7400)
    })
  })

  describe('calculateProjection', () => {
    it('should generate correct number of year summaries', () => {
      const input: CalculatorInput = {
        initialWorth: 10000,
        monthlyInvestment: 100,
        yearlyGrowthRate: 0,
        annualReturnRate: 0,
        years: 5,
        taxRate: 22,
      }

      const result = calculateProjection(input)

      expect(result.yearSummaries).toHaveLength(5)
      expect(result.portfolioData).toHaveLength(6)
    })

    it('should include initial worth in portfolio data', () => {
      const input: CalculatorInput = {
        initialWorth: 50000,
        monthlyInvestment: 0,
        yearlyGrowthRate: 0,
        annualReturnRate: 0,
        years: 3,
        taxRate: 0,
      }

      const result = calculateProjection(input)

      expect(result.portfolioData[0]).toBe(50000)
    })

    it('should calculate compound growth correctly over multiple years', () => {
      const input: CalculatorInput = {
        initialWorth: 10000,
        monthlyInvestment: 500,
        yearlyGrowthRate: 0,
        annualReturnRate: 12,
        years: 1,
        taxRate: 22,
      }

      const result = calculateProjection(input)
      const yearOne = result.yearSummaries[0]

      const monthlyRate = Math.pow(1.12, 1 / 12) - 1
      let expectedTotal = 10000
      for (let i = 0; i < 12; i++) {
        expectedTotal += 500
        expectedTotal *= 1 + monthlyRate
      }

      const totalInvested = 10000 + 500 * 12
      const grossProfit = expectedTotal - totalInvested

      expect(yearOne.totalInvested).toBe(totalInvested)
      expect(yearOne.grossProfit).toBeCloseTo(grossProfit, 0)
    })

    it('should apply yearly growth rate to monthly investments', () => {
      const input: CalculatorInput = {
        initialWorth: 0,
        monthlyInvestment: 100,
        yearlyGrowthRate: 10,
        annualReturnRate: 0,
        years: 2,
        taxRate: 0,
      }

      const result = calculateProjection(input)

      expect(result.yearSummaries[0].totalInvested).toBe(1200)
      expect(result.yearSummaries[1].totalInvested).toBeGreaterThan(2400)
    })

    it('should handle all zero inputs gracefully', () => {
      const input: CalculatorInput = {
        initialWorth: 0,
        monthlyInvestment: 0,
        yearlyGrowthRate: 0,
        annualReturnRate: 0,
        years: 3,
        taxRate: 0,
      }

      const result = calculateProjection(input)

      expect(result.yearSummaries).toHaveLength(3)
      result.yearSummaries.forEach(summary => {
        expect(summary.totalWorth).toBe(0)
        expect(summary.grossProfit).toBe(0)
        expect(summary.netWorth).toBe(0)
      })
    })

    it('should calculate tax correctly for each year', () => {
      const input: CalculatorInput = {
        initialWorth: 10000,
        monthlyInvestment: 0,
        yearlyGrowthRate: 0,
        annualReturnRate: 10,
        years: 3,
        taxRate: 25,
      }

      const result = calculateProjection(input)

      result.yearSummaries.forEach(summary => {
        expect(summary.taxAmount).toBeCloseTo(summary.grossProfit * 0.25, 2)
        expect(summary.netWorth).toBeCloseTo(summary.totalWorth - summary.taxAmount, 2)
      })
    })

    it('should calculate monthly earnings based on net profit', () => {
      const input: CalculatorInput = {
        initialWorth: 100000,
        monthlyInvestment: 0,
        yearlyGrowthRate: 0,
        annualReturnRate: 12,
        years: 1,
        taxRate: 22,
      }

      const result = calculateProjection(input)
      const yearOne = result.yearSummaries[0]

      const netProfit = yearOne.grossProfit - yearOne.taxAmount
      expect(yearOne.monthlyEarnings).toBeCloseTo(netProfit / 12, 2)
    })

    it('should accumulate investments correctly year over year', () => {
      const input: CalculatorInput = {
        initialWorth: 5000,
        monthlyInvestment: 200,
        yearlyGrowthRate: 0,
        annualReturnRate: 0,
        years: 3,
        taxRate: 0,
      }

      const result = calculateProjection(input)

      expect(result.yearSummaries[0].totalInvested).toBe(5000 + 200 * 12)
      expect(result.yearSummaries[1].totalInvested).toBe(5000 + 200 * 24)
      expect(result.yearSummaries[2].totalInvested).toBe(5000 + 200 * 36)
    })

    it('should produce consistent results for same input', () => {
      const input: CalculatorInput = {
        initialWorth: 25000,
        monthlyInvestment: 750,
        yearlyGrowthRate: 5,
        annualReturnRate: 8,
        years: 10,
        taxRate: 20,
      }

      const result1 = calculateProjection(input)
      const result2 = calculateProjection(input)

      expect(result1.yearSummaries).toEqual(result2.yearSummaries)
      expect(result1.portfolioData).toEqual(result2.portfolioData)
    })
  })
})
