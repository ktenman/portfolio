import { describe, it, expect, beforeEach } from 'vitest'
import {
  buildSectorChartData,
  buildCompanyChartData,
  buildCountryChartData,
  calculateWeightedMetrics,
  getFilterParam,
} from './etf-chart-service'
import type { EtfHoldingBreakdownDto } from '../models/generated/domain-models'
import { DONUT_COLORS } from '../constants/chart-colors'
import { createInstrumentDto } from '../tests/fixtures'

let holdingIdCounter = 1

const createHolding = (
  overrides: Partial<EtfHoldingBreakdownDto> = {}
): EtfHoldingBreakdownDto => ({
  holdingUuid: `uuid-${holdingIdCounter++}`,
  holdingName: 'Apple Inc.',
  holdingTicker: 'AAPL',
  holdingSector: 'Technology',
  holdingCountryCode: 'US',
  holdingCountryName: 'United States',
  totalValueEur: 1000,
  percentageOfTotal: 10,
  inEtfs: 'VWCE',
  numEtfs: 1,
  platforms: 'TRADING212',
  ...overrides,
})

describe('etf-chart-service', () => {
  beforeEach(() => {
    holdingIdCounter = 1
  })
  describe('buildSectorChartData', () => {
    it('should aggregate holdings by sector', () => {
      const holdings = [
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 30 }),
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 20 }),
        createHolding({ holdingSector: 'Finance', percentageOfTotal: 25 }),
      ]
      const result = buildSectorChartData(holdings)
      expect(result).toHaveLength(2)
      expect(result[0].label).toBe('Technology')
      expect(result[0].value).toBe(50)
      expect(result[1].label).toBe('Finance')
      expect(result[1].value).toBe(25)
    })

    it('should sort sectors by total percentage descending', () => {
      const holdings = [
        createHolding({ holdingSector: 'Finance', percentageOfTotal: 10 }),
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 50 }),
        createHolding({ holdingSector: 'Healthcare', percentageOfTotal: 30 }),
      ]
      const result = buildSectorChartData(holdings)
      expect(result[0].label).toBe('Technology')
      expect(result[1].label).toBe('Healthcare')
      expect(result[2].label).toBe('Finance')
    })

    it('should limit to top 15 sectors by default', () => {
      const holdings = Array.from({ length: 20 }, (_, i) =>
        createHolding({ holdingSector: `Sector ${i}`, percentageOfTotal: 5 })
      )
      const result = buildSectorChartData(holdings)
      expect(result).toHaveLength(15)
    })

    it('should drop sectors beyond the top count instead of grouping them', () => {
      const holdings = Array.from({ length: 22 }, (_, i) =>
        createHolding({ holdingSector: `Sector ${i}`, percentageOfTotal: i + 1 })
      )
      const result = buildSectorChartData(holdings)
      expect(result.some(item => item.label === 'Others')).toBe(false)
    })

    it('should assign colors from palette', () => {
      const holdings = [
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 50 }),
        createHolding({ holdingSector: 'Finance', percentageOfTotal: 30 }),
      ]
      const result = buildSectorChartData(holdings)
      expect(result[0].color).toBe(DONUT_COLORS[0])
      expect(result[1].color).toBe(DONUT_COLORS[1])
    })

    it('should handle empty holdings array', () => {
      const result = buildSectorChartData([])
      expect(result).toHaveLength(0)
    })

    it('should handle unknown sector as Unknown', () => {
      const holdings = [createHolding({ holdingSector: undefined, percentageOfTotal: 10 })]
      const result = buildSectorChartData(holdings)
      expect(result[0].label).toBe('Unknown')
    })

    it('should drop sectors below the 0.5% threshold', () => {
      const holdings = [
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 70 }),
        createHolding({ holdingSector: 'Finance', percentageOfTotal: 20 }),
        createHolding({ holdingSector: 'TinySector1', percentageOfTotal: 0.3 }),
        createHolding({ holdingSector: 'TinySector2', percentageOfTotal: 0.2 }),
      ]
      const result = buildSectorChartData(holdings)
      expect(result.map(item => item.label)).toEqual(['Technology', 'Finance'])
    })
  })

  describe('buildCompanyChartData', () => {
    it('should sort holdings by percentage descending', () => {
      const holdings = [
        createHolding({ holdingName: 'Company A', percentageOfTotal: 5 }),
        createHolding({ holdingName: 'Company B', percentageOfTotal: 15 }),
        createHolding({ holdingName: 'Company C', percentageOfTotal: 10 }),
      ]
      const result = buildCompanyChartData(holdings)
      expect(result[0].label).toBe('Company B')
      expect(result[1].label).toBe('Company C')
      expect(result[2].label).toBe('Company A')
    })

    it('should show top 15 companies by default', () => {
      const holdings = Array.from({ length: 20 }, (_, i) =>
        createHolding({ holdingName: `Company ${i}`, percentageOfTotal: 5 })
      )
      const result = buildCompanyChartData(holdings)
      expect(result).toHaveLength(15)
    })

    it('should drop holdings beyond the top count instead of grouping them', () => {
      const holdings = Array.from({ length: 22 }, (_, i) =>
        createHolding({ holdingName: `Company ${i}`, percentageOfTotal: i + 1 })
      )
      const result = buildCompanyChartData(holdings)
      expect(result.some(item => item.label === 'Others')).toBe(false)
    })

    it('should assign colors from palette', () => {
      const holdings = [
        createHolding({ holdingName: 'Company A', percentageOfTotal: 20 }),
        createHolding({ holdingName: 'Company B', percentageOfTotal: 15 }),
      ]
      const result = buildCompanyChartData(holdings)
      expect(result[0].color).toBe(DONUT_COLORS[0])
      expect(result[1].color).toBe(DONUT_COLORS[1])
    })

    it('should handle empty holdings array', () => {
      const result = buildCompanyChartData([])
      expect(result).toHaveLength(0)
    })
  })

  describe('buildCountryChartData', () => {
    it('should aggregate holdings by country', () => {
      const holdings = [
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 30 }),
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 20 }),
        createHolding({ holdingCountryName: 'Germany', percentageOfTotal: 25 }),
      ]
      const result = buildCountryChartData(holdings)
      expect(result).toHaveLength(2)
      expect(result[0].label).toBe('United States')
      expect(result[0].value).toBe(50)
      expect(result[1].label).toBe('Germany')
      expect(result[1].value).toBe(25)
    })

    it('should sort countries by total percentage descending', () => {
      const holdings = [
        createHolding({ holdingCountryName: 'Germany', percentageOfTotal: 10 }),
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 50 }),
        createHolding({ holdingCountryName: 'Japan', percentageOfTotal: 30 }),
      ]
      const result = buildCountryChartData(holdings)
      expect(result[0].label).toBe('United States')
      expect(result[1].label).toBe('Japan')
      expect(result[2].label).toBe('Germany')
    })

    it('should limit to top 15 countries by default', () => {
      const holdings = Array.from({ length: 20 }, (_, i) =>
        createHolding({ holdingCountryName: `Country ${i}`, percentageOfTotal: 5 })
      )
      const result = buildCountryChartData(holdings)
      expect(result).toHaveLength(15)
    })

    it('should drop countries beyond the top count instead of grouping them', () => {
      const holdings = Array.from({ length: 22 }, (_, i) =>
        createHolding({ holdingCountryName: `Country ${i}`, percentageOfTotal: i + 1 })
      )
      const result = buildCountryChartData(holdings)
      expect(result.some(item => item.label === 'Others')).toBe(false)
    })

    it('should assign colors from palette', () => {
      const holdings = [
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 50 }),
        createHolding({ holdingCountryName: 'Germany', percentageOfTotal: 30 }),
      ]
      const result = buildCountryChartData(holdings)
      expect(result[0].color).toBe(DONUT_COLORS[0])
      expect(result[1].color).toBe(DONUT_COLORS[1])
    })

    it('should handle empty holdings array', () => {
      const result = buildCountryChartData([])
      expect(result).toHaveLength(0)
    })

    it('should handle unknown country as Unknown', () => {
      const holdings = [createHolding({ holdingCountryName: null, percentageOfTotal: 10 })]
      const result = buildCountryChartData(holdings)
      expect(result[0].label).toBe('Unknown')
    })

    it('should include country code for flag display', () => {
      const holdings = [
        createHolding({
          holdingCountryName: 'United States',
          holdingCountryCode: 'US',
          percentageOfTotal: 60,
        }),
        createHolding({
          holdingCountryName: 'Germany',
          holdingCountryCode: 'DE',
          percentageOfTotal: 40,
        }),
      ]
      const result = buildCountryChartData(holdings)
      expect(result[0].code).toBe('US')
      expect(result[1].code).toBe('DE')
    })

    it('should drop countries below the 0.2% threshold', () => {
      const holdings = [
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 70 }),
        createHolding({ holdingCountryName: 'Germany', percentageOfTotal: 20 }),
        createHolding({ holdingCountryName: 'TinyCountry1', percentageOfTotal: 0.15 }),
        createHolding({ holdingCountryName: 'TinyCountry2', percentageOfTotal: 0.1 }),
      ]
      const result = buildCountryChartData(holdings)
      expect(result.map(item => item.label)).toEqual(['United States', 'Germany'])
    })
  })

  describe('getFilterParam', () => {
    it('should return undefined when nothing selected', () => {
      const result = getFilterParam([], ['A', 'B', 'C'])
      expect(result).toBeUndefined()
    })

    it('should return undefined when all items selected', () => {
      const result = getFilterParam(['A', 'B', 'C'], ['A', 'B', 'C'])
      expect(result).toBeUndefined()
    })

    it('should return selected items when partial selection', () => {
      const result = getFilterParam(['A', 'B'], ['A', 'B', 'C'])
      expect(result).toEqual(['A', 'B'])
    })

    it('should return single selected item', () => {
      const result = getFilterParam(['B'], ['A', 'B', 'C'])
      expect(result).toEqual(['B'])
    })

    it('should handle number arrays', () => {
      const result = getFilterParam([1, 2], [1, 2, 3, 4])
      expect(result).toEqual([1, 2])
    })
  })

  describe('calculateWeightedMetrics', () => {
    const vwce = createInstrumentDto({
      symbol: 'VWCE',
      currentValue: 3000,
      ter: 0.2,
      xirrAnnualReturn: 0.1,
    })
    const qdve = createInstrumentDto({
      symbol: 'QDVE',
      currentValue: 1000,
      ter: 0.6,
      xirrAnnualReturn: 0.5,
    })

    it('should weight both figures by current value', () => {
      const result = calculateWeightedMetrics([vwce, qdve], ['VWCE', 'QDVE'])

      expect(result.ter).toBeCloseTo(0.3, 10)
      expect(result.annualReturn).toBeCloseTo(0.2, 10)
    })

    it('should ignore instruments that are not selected', () => {
      const excluded = createInstrumentDto({ symbol: 'SXR8', currentValue: 9000, ter: 5 })
      const result = calculateWeightedMetrics([vwce, qdve, excluded], ['VWCE', 'QDVE'])

      expect(result.ter).toBeCloseTo(0.3, 10)
    })

    it('should ignore instruments without a current value', () => {
      const sold = createInstrumentDto({ symbol: 'IWDA', currentValue: 0, ter: 5 })
      const result = calculateWeightedMetrics([vwce, qdve, sold], ['VWCE', 'QDVE', 'IWDA'])

      expect(result.ter).toBeCloseTo(0.3, 10)
    })

    it('should redistribute weight when a figure is missing', () => {
      const withoutTer = createInstrumentDto({ symbol: 'VWCE', currentValue: 3000, ter: null })
      const result = calculateWeightedMetrics([withoutTer, qdve], ['VWCE', 'QDVE'])

      expect(result.ter).toBeCloseTo(0.6, 10)
    })

    it('should return nulls when nothing is selected', () => {
      const result = calculateWeightedMetrics([vwce, qdve], [])

      expect(result).toEqual({ ter: null, annualReturn: null })
    })
  })
})
