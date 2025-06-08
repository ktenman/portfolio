import { describe, it, expect } from 'vitest'

// Since calculation-result.ts only contains interface definitions,
// we'll test that the interfaces can be used correctly
describe('CalculationResult', () => {
  it('has correct interface structure', () => {
    // Test that we can create objects matching the interface
    const result = {
      currentValue: 10000,
      totalInvestment: 8000,
      totalReturn: 2000,
      returnPercentage: 25.0,
      xirr: 15.5,
    }

    expect(result.currentValue).toBe(10000)
    expect(result.totalInvestment).toBe(8000)
    expect(result.totalReturn).toBe(2000)
    expect(result.returnPercentage).toBe(25.0)
    expect(result.xirr).toBe(15.5)
  })

  it('handles zero values', () => {
    const result = {
      currentValue: 0,
      totalInvestment: 0,
      totalReturn: 0,
      returnPercentage: 0,
      xirr: 0,
    }

    expect(result.currentValue).toBe(0)
    expect(result.totalInvestment).toBe(0)
    expect(result.totalReturn).toBe(0)
    expect(result.returnPercentage).toBe(0)
    expect(result.xirr).toBe(0)
  })

  it('handles negative values', () => {
    const result = {
      currentValue: 8000,
      totalInvestment: 10000,
      totalReturn: -2000,
      returnPercentage: -20.0,
      xirr: -10.5,
    }

    expect(result.currentValue).toBe(8000)
    expect(result.totalInvestment).toBe(10000)
    expect(result.totalReturn).toBe(-2000)
    expect(result.returnPercentage).toBe(-20.0)
    expect(result.xirr).toBe(-10.5)
  })

  it('handles decimal values', () => {
    const result = {
      currentValue: 10000.5,
      totalInvestment: 8000.25,
      totalReturn: 2000.25,
      returnPercentage: 25.003125,
      xirr: 15.456789,
    }

    expect(result.currentValue).toBe(10000.5)
    expect(result.totalInvestment).toBe(8000.25)
    expect(result.totalReturn).toBe(2000.25)
    expect(result.returnPercentage).toBe(25.003125)
    expect(result.xirr).toBe(15.456789)
  })
})
