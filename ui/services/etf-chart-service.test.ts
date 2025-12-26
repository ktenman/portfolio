import { describe, it, expect } from 'vitest'
import {
  buildSectorChartData,
  buildCompanyChartData,
  buildCountryChartData,
  getFilterParam,
} from './etf-chart-service'
import type { EtfHoldingBreakdownDto } from '../models/generated/domain-models'

const createHolding = (
  overrides: Partial<EtfHoldingBreakdownDto> = {}
): EtfHoldingBreakdownDto => ({
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

    it('should limit to top 20 sectors by default', () => {
      const holdings = Array.from({ length: 25 }, (_, i) =>
        createHolding({ holdingSector: `Sector ${i}`, percentageOfTotal: 4 })
      )
      const result = buildSectorChartData(holdings)
      expect(result).toHaveLength(21)
      expect(result[20].label).toBe('Others')
    })

    it('should group remaining sectors as Others', () => {
      const holdings = Array.from({ length: 22 }, (_, i) =>
        createHolding({ holdingSector: `Sector ${i}`, percentageOfTotal: i + 1 })
      )
      const result = buildSectorChartData(holdings)
      const others = result.find(item => item.label === 'Others')
      expect(others).toBeDefined()
    })

    it('should assign colors from palette', () => {
      const holdings = [
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 50 }),
        createHolding({ holdingSector: 'Finance', percentageOfTotal: 30 }),
      ]
      const result = buildSectorChartData(holdings)
      expect(result[0].color).toBe('#0072B2')
      expect(result[1].color).toBe('#E69F00')
    })

    it('should assign gray color to Others', () => {
      const holdings = Array.from({ length: 22 }, (_, i) =>
        createHolding({ holdingSector: `Sector ${i}`, percentageOfTotal: 1 })
      )
      const result = buildSectorChartData(holdings)
      const others = result.find(item => item.label === 'Others')
      expect(others?.color).toBe('#999999')
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

    it('should accept custom top count', () => {
      const holdings = Array.from({ length: 10 }, (_, i) =>
        createHolding({ holdingSector: `Sector ${i}`, percentageOfTotal: 10 })
      )
      const result = buildSectorChartData(holdings, { topCount: 5 })
      expect(result).toHaveLength(6)
      expect(result[5].label).toBe('Others')
    })

    it('should accept custom color palette', () => {
      const holdings = [createHolding({ holdingSector: 'Tech', percentageOfTotal: 100 })]
      const result = buildSectorChartData(holdings, { colors: ['#FF0000'] })
      expect(result[0].color).toBe('#FF0000')
    })

    it('should group sectors below 0.5% threshold into Others by default', () => {
      const holdings = [
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 70 }),
        createHolding({ holdingSector: 'Finance', percentageOfTotal: 20 }),
        createHolding({ holdingSector: 'TinySector1', percentageOfTotal: 0.3 }),
        createHolding({ holdingSector: 'TinySector2', percentageOfTotal: 0.2 }),
      ]
      const result = buildSectorChartData(holdings)
      expect(result).toHaveLength(3)
      expect(result[0].label).toBe('Technology')
      expect(result[1].label).toBe('Finance')
      expect(result[2].label).toBe('Others')
      expect(result[2].value).toBeCloseTo(0.5)
    })

    it('should accept custom minThreshold', () => {
      const holdings = [
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 70 }),
        createHolding({ holdingSector: 'Finance', percentageOfTotal: 20 }),
        createHolding({ holdingSector: 'SmallSector', percentageOfTotal: 1 }),
      ]
      const result = buildSectorChartData(holdings, { minThreshold: 2 })
      expect(result).toHaveLength(3)
      expect(result[2].label).toBe('Others')
      expect(result[2].value).toBe(1)
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

    it('should filter by 1.5% threshold by default', () => {
      const holdings = [
        createHolding({ holdingName: 'Big Company', percentageOfTotal: 10 }),
        createHolding({ holdingName: 'Small Company', percentageOfTotal: 1 }),
      ]
      const result = buildCompanyChartData(holdings)
      expect(result).toHaveLength(2)
      expect(result[0].label).toBe('Big Company')
      expect(result[1].label).toBe('Others')
    })

    it('should group small holdings as Others', () => {
      const holdings = [
        createHolding({ holdingName: 'Big', percentageOfTotal: 50 }),
        createHolding({ holdingName: 'Small 1', percentageOfTotal: 0.5 }),
        createHolding({ holdingName: 'Small 2', percentageOfTotal: 0.3 }),
      ]
      const result = buildCompanyChartData(holdings)
      const others = result.find(item => item.label === 'Others')
      expect(others?.value).toBeCloseTo(0.8)
    })

    it('should assign colors from palette', () => {
      const holdings = [
        createHolding({ holdingName: 'Company A', percentageOfTotal: 20 }),
        createHolding({ holdingName: 'Company B', percentageOfTotal: 15 }),
      ]
      const result = buildCompanyChartData(holdings)
      expect(result[0].color).toBe('#0072B2')
      expect(result[1].color).toBe('#E69F00')
    })

    it('should assign gray color to Others', () => {
      const holdings = [
        createHolding({ holdingName: 'Big', percentageOfTotal: 50 }),
        createHolding({ holdingName: 'Small', percentageOfTotal: 0.5 }),
      ]
      const result = buildCompanyChartData(holdings)
      const others = result.find(item => item.label === 'Others')
      expect(others?.color).toBe('#999999')
    })

    it('should handle empty holdings array', () => {
      const result = buildCompanyChartData([])
      expect(result).toHaveLength(0)
    })

    it('should accept custom threshold', () => {
      const holdings = [
        createHolding({ holdingName: 'Company A', percentageOfTotal: 5 }),
        createHolding({ holdingName: 'Company B', percentageOfTotal: 3 }),
      ]
      const result = buildCompanyChartData(holdings, { threshold: 4 })
      expect(result).toHaveLength(2)
      expect(result[0].label).toBe('Company A')
      expect(result[1].label).toBe('Others')
    })

    it('should accept custom color palette', () => {
      const holdings = [createHolding({ holdingName: 'Company', percentageOfTotal: 100 })]
      const result = buildCompanyChartData(holdings, { colors: ['#00FF00'] })
      expect(result[0].color).toBe('#00FF00')
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

    it('should limit to top 20 countries by default', () => {
      const holdings = Array.from({ length: 25 }, (_, i) =>
        createHolding({ holdingCountryName: `Country ${i}`, percentageOfTotal: 4 })
      )
      const result = buildCountryChartData(holdings)
      expect(result).toHaveLength(21)
      expect(result[20].label).toBe('Others')
    })

    it('should group remaining countries as Others', () => {
      const holdings = Array.from({ length: 22 }, (_, i) =>
        createHolding({ holdingCountryName: `Country ${i}`, percentageOfTotal: i + 1 })
      )
      const result = buildCountryChartData(holdings)
      const others = result.find(item => item.label === 'Others')
      expect(others).toBeDefined()
    })

    it('should assign colors from palette', () => {
      const holdings = [
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 50 }),
        createHolding({ holdingCountryName: 'Germany', percentageOfTotal: 30 }),
      ]
      const result = buildCountryChartData(holdings)
      expect(result[0].color).toBe('#0072B2')
      expect(result[1].color).toBe('#E69F00')
    })

    it('should assign gray color to Others', () => {
      const holdings = Array.from({ length: 22 }, (_, i) =>
        createHolding({ holdingCountryName: `Country ${i}`, percentageOfTotal: 1 })
      )
      const result = buildCountryChartData(holdings)
      const others = result.find(item => item.label === 'Others')
      expect(others?.color).toBe('#999999')
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

    it('should accept custom top count', () => {
      const holdings = Array.from({ length: 10 }, (_, i) =>
        createHolding({ holdingCountryName: `Country ${i}`, percentageOfTotal: 10 })
      )
      const result = buildCountryChartData(holdings, { topCount: 5 })
      expect(result).toHaveLength(6)
      expect(result[5].label).toBe('Others')
    })

    it('should accept custom color palette', () => {
      const holdings = [createHolding({ holdingCountryName: 'USA', percentageOfTotal: 100 })]
      const result = buildCountryChartData(holdings, { colors: ['#FF0000'] })
      expect(result[0].color).toBe('#FF0000')
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

    it('should have undefined code for Others category', () => {
      const holdings = Array.from({ length: 22 }, (_, i) =>
        createHolding({
          holdingCountryName: `Country ${i}`,
          holdingCountryCode: `C${i}`,
          percentageOfTotal: 1,
        })
      )
      const result = buildCountryChartData(holdings)
      const others = result.find(item => item.label === 'Others')
      expect(others?.code).toBeUndefined()
    })

    it('should group countries below 0.5% threshold into Others by default', () => {
      const holdings = [
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 70 }),
        createHolding({ holdingCountryName: 'Germany', percentageOfTotal: 20 }),
        createHolding({ holdingCountryName: 'TinyCountry1', percentageOfTotal: 0.3 }),
        createHolding({ holdingCountryName: 'TinyCountry2', percentageOfTotal: 0.2 }),
      ]
      const result = buildCountryChartData(holdings)
      expect(result).toHaveLength(3)
      expect(result[0].label).toBe('United States')
      expect(result[1].label).toBe('Germany')
      expect(result[2].label).toBe('Others')
      expect(result[2].value).toBeCloseTo(0.5)
    })

    it('should accept custom minThreshold', () => {
      const holdings = [
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 70 }),
        createHolding({ holdingCountryName: 'Germany', percentageOfTotal: 20 }),
        createHolding({ holdingCountryName: 'SmallCountry', percentageOfTotal: 1 }),
      ]
      const result = buildCountryChartData(holdings, { minThreshold: 2 })
      expect(result).toHaveLength(3)
      expect(result[2].label).toBe('Others')
      expect(result[2].value).toBe(1)
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
})
