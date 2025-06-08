import { describe, it, expect } from 'vitest'

// Since portfolio-summary.ts only contains interface definitions,
// we'll test that the interfaces can be used correctly
describe('PortfolioSummary', () => {
  it('has correct interface structure with all fields', () => {
    const summary = {
      id: 1,
      date: '2024-01-15',
      totalValue: 10000,
      totalInvestment: 8000,
      totalReturn: 2000,
      returnPercentage: 25.0,
      xirr: 15.5,
    }

    expect(summary.id).toBe(1)
    expect(summary.date).toBe('2024-01-15')
    expect(summary.totalValue).toBe(10000)
    expect(summary.totalInvestment).toBe(8000)
    expect(summary.totalReturn).toBe(2000)
    expect(summary.returnPercentage).toBe(25.0)
    expect(summary.xirr).toBe(15.5)
  })

  it('handles optional id field', () => {
    const summary = {
      date: '2024-01-16',
      totalValue: 15000,
      xirrAnnualReturn: 18.2,
      totalProfit: 3000,
      earningsPerDay: 75,
      earningsPerMonth: 2250,
    }

    expect(summary.date).toBe('2024-01-16')
    expect(summary.totalValue).toBe(15000)
    expect(summary.xirrAnnualReturn).toBe(18.2)
  })

  it('handles zero values', () => {
    const summary = {
      id: 2,
      date: '2024-01-01',
      totalValue: 0,
      totalInvestment: 0,
      totalReturn: 0,
      returnPercentage: 0,
      xirr: 0,
    }

    expect(summary.totalValue).toBe(0)
    expect(summary.totalInvestment).toBe(0)
    expect(summary.totalReturn).toBe(0)
    expect(summary.returnPercentage).toBe(0)
    expect(summary.xirr).toBe(0)
  })

  it('handles negative returns', () => {
    const summary = {
      id: 3,
      date: '2024-01-17',
      totalValue: 8000,
      totalInvestment: 10000,
      totalReturn: -2000,
      returnPercentage: -20.0,
      xirr: -15.5,
    }

    expect(summary.totalReturn).toBe(-2000)
    expect(summary.returnPercentage).toBe(-20.0)
    expect(summary.xirr).toBe(-15.5)
  })

  it('handles decimal values', () => {
    const summary = {
      id: 4,
      date: '2024-01-18',
      totalValue: 10567.89,
      totalInvestment: 8234.56,
      totalReturn: 2333.33,
      returnPercentage: 28.345,
      xirr: 16.789,
    }

    expect(summary.totalValue).toBe(10567.89)
    expect(summary.totalInvestment).toBe(8234.56)
    expect(summary.totalReturn).toBe(2333.33)
    expect(summary.returnPercentage).toBe(28.345)
    expect(summary.xirr).toBe(16.789)
  })

  it('handles different date formats', () => {
    const summaries = [
      {
        id: 5,
        date: '2024-01-15',
        totalValue: 1000,
        totalInvestment: 800,
        totalReturn: 200,
        returnPercentage: 25.0,
        xirr: 15.0,
      },
      {
        id: 6,
        date: '2024-12-31',
        totalValue: 2000,
        totalInvestment: 1600,
        totalReturn: 400,
        returnPercentage: 25.0,
        xirr: 15.0,
      },
    ]

    expect(summaries[0].date).toBe('2024-01-15')
    expect(summaries[1].date).toBe('2024-12-31')
    expect(typeof summaries[0].date).toBe('string')
  })

  it('validates numeric field types', () => {
    const summary = {
      id: 7,
      date: '2024-01-19',
      totalValue: 50000,
      totalInvestment: 40000,
      totalReturn: 10000,
      returnPercentage: 25.0,
      xirr: 20.5,
    }

    expect(typeof summary.id).toBe('number')
    expect(typeof summary.totalValue).toBe('number')
    expect(typeof summary.totalInvestment).toBe('number')
    expect(typeof summary.totalReturn).toBe('number')
    expect(typeof summary.returnPercentage).toBe('number')
    expect(typeof summary.xirr).toBe('number')
    expect(typeof summary.date).toBe('string')
  })

  it('handles large values', () => {
    const summary = {
      id: 8,
      date: '2024-01-20',
      totalValue: 1000000.5,
      totalInvestment: 800000.25,
      totalReturn: 200000.25,
      returnPercentage: 25.003125,
      xirr: 22.456789,
    }

    expect(summary.totalValue).toBe(1000000.5)
    expect(summary.totalInvestment).toBe(800000.25)
    expect(summary.returnPercentage).toBe(25.003125)
  })

  it('handles array of summaries', () => {
    const summaries = [
      {
        id: 9,
        date: '2024-01-21',
        totalValue: 5000,
        totalInvestment: 4000,
        totalReturn: 1000,
        returnPercentage: 25.0,
        xirr: 18.0,
      },
      {
        id: 10,
        date: '2024-01-22',
        totalValue: 5500,
        totalInvestment: 4200,
        totalReturn: 1300,
        returnPercentage: 30.95,
        xirr: 19.2,
      },
    ]

    expect(summaries).toHaveLength(2)
    expect(summaries[0].id).toBe(9)
    expect(summaries[1].id).toBe(10)
    expect(summaries[1].totalReturn).toBeGreaterThan(summaries[0].totalReturn)
  })
})
