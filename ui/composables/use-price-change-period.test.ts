import { describe, it, expect, beforeEach, vi } from 'vitest'
import { usePriceChangePeriod } from './use-price-change-period'
import { PriceChangePeriod } from '../models/generated/domain-models'

describe('usePriceChangePeriod', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('should initialize with default period when localStorage is empty', () => {
    const { selectedPeriod } = usePriceChangePeriod()

    expect(selectedPeriod.value).toBe(PriceChangePeriod.P24H)
  })

  it('should initialize with stored period when valid period exists in localStorage', () => {
    localStorage.setItem('portfolio_price_change_period', PriceChangePeriod.P7D)

    const { selectedPeriod } = usePriceChangePeriod()

    expect(selectedPeriod.value).toBe(PriceChangePeriod.P7D)
  })

  it('should initialize with default period when invalid period in localStorage', () => {
    localStorage.setItem('portfolio_price_change_period', 'invalid')

    const { selectedPeriod } = usePriceChangePeriod()

    expect(selectedPeriod.value).toBe(PriceChangePeriod.P24H)
  })

  it('should persist period to localStorage when changed', async () => {
    const { selectedPeriod } = usePriceChangePeriod()

    selectedPeriod.value = PriceChangePeriod.P30D

    await new Promise(resolve => setTimeout(resolve, 10))

    expect(localStorage.getItem('portfolio_price_change_period')).toBe(PriceChangePeriod.P30D)
  })

  it('should return all period options', () => {
    const { periods } = usePriceChangePeriod()

    expect(periods).toHaveLength(7)
    expect(periods).toEqual([
      { value: PriceChangePeriod.P24H, label: '24H' },
      { value: PriceChangePeriod.P48H, label: '48H' },
      { value: PriceChangePeriod.P3D, label: '3D' },
      { value: PriceChangePeriod.P7D, label: '7D' },
      { value: PriceChangePeriod.P10D, label: '10D' },
      { value: PriceChangePeriod.P30D, label: '30D' },
      { value: PriceChangePeriod.P1Y, label: '1Y' },
    ])
  })

  it('should allow changing between all valid periods', async () => {
    const { selectedPeriod } = usePriceChangePeriod()
    const validPeriods: PriceChangePeriod[] = [
      PriceChangePeriod.P24H,
      PriceChangePeriod.P48H,
      PriceChangePeriod.P3D,
      PriceChangePeriod.P7D,
      PriceChangePeriod.P30D,
      PriceChangePeriod.P1Y,
    ]

    for (const period of validPeriods) {
      selectedPeriod.value = period
      expect(selectedPeriod.value).toBe(period)
    }
  })

  it('should return read-only period options', () => {
    const { periods } = usePriceChangePeriod()

    expect(periods).toHaveLength(7)
    expect(periods[0].value).toBe(PriceChangePeriod.P24H)
    expect(periods[1].value).toBe(PriceChangePeriod.P48H)
    expect(periods[2].value).toBe(PriceChangePeriod.P3D)
    expect(periods[3].value).toBe(PriceChangePeriod.P7D)
    expect(periods[4].value).toBe(PriceChangePeriod.P10D)
    expect(periods[5].value).toBe(PriceChangePeriod.P30D)
    expect(periods[6].value).toBe(PriceChangePeriod.P1Y)
  })
})
