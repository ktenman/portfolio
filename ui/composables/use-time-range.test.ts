import { describe, it, expect, beforeEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { TIME_RANGES, useChartRange, usePriceChangePeriod } from './use-time-range'
import { TimeRange } from '../models/generated/domain-models'

describe('useTimeRange', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('should expose every generated range in wire order', () => {
    expect(TIME_RANGES).toEqual([
      '1D',
      '2D',
      '3D',
      '1W',
      '1M',
      '3M',
      '6M',
      'YTD',
      '1Y',
      '2Y',
      '3Y',
      '4Y',
      '5Y',
      'MAX',
    ])
  })

  it('should initialize the chart range with one month when localStorage is empty', () => {
    const selectedRange = useChartRange()

    expect(selectedRange.value).toBe(TimeRange.ONE_MONTH)
  })

  it('should initialize the chart range from localStorage when the stored range is valid', () => {
    localStorage.setItem('portfolio_summary_chart_range', TimeRange.MAX)

    const selectedRange = useChartRange()

    expect(selectedRange.value).toBe(TimeRange.MAX)
  })

  it('should fall back to one month when the stored chart range is unknown', () => {
    localStorage.setItem('portfolio_summary_chart_range', 'ülipikk')

    const selectedRange = useChartRange()

    expect(selectedRange.value).toBe(TimeRange.ONE_MONTH)
  })

  it('should persist the chart range to localStorage when changed', async () => {
    const selectedRange = useChartRange()

    selectedRange.value = TimeRange.YTD

    await flushPromises()

    expect(localStorage.getItem('portfolio_summary_chart_range')).toBe('YTD')
  })

  it('should heal the chart range when a storage event dispatches an unknown range', async () => {
    const selectedRange = useChartRange()

    window.dispatchEvent(
      new StorageEvent('storage', {
        key: 'portfolio_summary_chart_range',
        newValue: 'そんな範囲はない',
        storageArea: localStorage,
      })
    )

    await flushPromises()

    expect(selectedRange.value).toBe(TimeRange.ONE_MONTH)
  })

  it('should initialize the price change period with one day when localStorage is empty', () => {
    const selectedPeriod = usePriceChangePeriod()

    expect(selectedPeriod.value).toBe(TimeRange.ONE_DAY)
  })

  it('should initialize the price change period from localStorage when the stored period is valid', () => {
    localStorage.setItem('portfolio_price_change_period', TimeRange.ONE_WEEK)

    const selectedPeriod = usePriceChangePeriod()

    expect(selectedPeriod.value).toBe(TimeRange.ONE_WEEK)
  })

  it('should fall back to one day when the stored price change period is unknown', () => {
    localStorage.setItem('portfolio_price_change_period', 'invalid')

    const selectedPeriod = usePriceChangePeriod()

    expect(selectedPeriod.value).toBe(TimeRange.ONE_DAY)
  })

  it('should persist the price change period to localStorage when changed', async () => {
    const selectedPeriod = usePriceChangePeriod()

    selectedPeriod.value = TimeRange.FIVE_YEARS

    await flushPromises()

    expect(localStorage.getItem('portfolio_price_change_period')).toBe('5Y')
  })

  it('should keep the chart range and the price change period independent', () => {
    const selectedRange = useChartRange()
    const selectedPeriod = usePriceChangePeriod()

    selectedRange.value = TimeRange.THREE_YEARS

    expect(selectedPeriod.value).toBe(TimeRange.ONE_DAY)
  })
})
