import { describe, it, expect } from 'vitest'
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

const createHolding = (
  overrides: Partial<EtfHoldingBreakdownDto> = {}
): EtfHoldingBreakdownDto => ({
  holdingUuid: 'uuid-1',
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

const BUILDERS = [
  ['buildSectorChartData', buildSectorChartData, (holdingSector: string) => ({ holdingSector })],
  ['buildCompanyChartData', buildCompanyChartData, (holdingName: string) => ({ holdingName })],
  [
    'buildCountryChartData',
    buildCountryChartData,
    (holdingCountryName: string) => ({ holdingCountryName }),
  ],
] as const

describe('etf-chart-service', () => {
  describe.each(BUILDERS)('%s', (_name, build, labelled) => {
    it('should sort entries by percentage descending', () => {
      const result = build([
        createHolding({ ...labelled('B'), percentageOfTotal: 10 }),
        createHolding({ ...labelled('A'), percentageOfTotal: 50 }),
        createHolding({ ...labelled('C'), percentageOfTotal: 30 }),
      ])
      expect(result.map(item => item.label)).toEqual(['A', 'C', 'B'])
    })

    it('should limit to the top 15 entries', () => {
      const result = build(
        Array.from({ length: 20 }, (_, i) =>
          createHolding({ ...labelled(`Entry ${i}`), percentageOfTotal: 5 })
        )
      )
      expect(result).toHaveLength(15)
    })

    it('should drop entries beyond the top count instead of grouping them', () => {
      const result = build(
        Array.from({ length: 22 }, (_, i) =>
          createHolding({ ...labelled(`Entry ${i}`), percentageOfTotal: i + 1 })
        )
      )
      expect(result.some(item => item.label === 'Others')).toBe(false)
    })

    it('should assign colors from the palette in order', () => {
      const result = build([
        createHolding({ ...labelled('A'), percentageOfTotal: 50 }),
        createHolding({ ...labelled('B'), percentageOfTotal: 30 }),
      ])
      expect([result[0].color, result[1].color]).toEqual([DONUT_COLORS[0], DONUT_COLORS[1]])
    })

    it('should handle empty holdings array', () => {
      expect(build([])).toHaveLength(0)
    })
  })

  describe('buildSectorChartData', () => {
    it('should aggregate holdings by sector', () => {
      const result = buildSectorChartData([
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 30 }),
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 20 }),
        createHolding({ holdingSector: 'Finance', percentageOfTotal: 25 }),
      ])
      expect(result).toEqual([
        expect.objectContaining({ label: 'Technology', value: 50 }),
        expect.objectContaining({ label: 'Finance', value: 25 }),
      ])
    })

    it('should handle unknown sector as Unknown', () => {
      const result = buildSectorChartData([
        createHolding({ holdingSector: undefined, percentageOfTotal: 10 }),
      ])
      expect(result[0].label).toBe('Unknown')
    })

    it('should drop sectors below the 0.5% threshold', () => {
      const result = buildSectorChartData([
        createHolding({ holdingSector: 'Technology', percentageOfTotal: 70 }),
        createHolding({ holdingSector: 'Finance', percentageOfTotal: 20 }),
        createHolding({ holdingSector: 'TinySector1', percentageOfTotal: 0.3 }),
        createHolding({ holdingSector: 'TinySector2', percentageOfTotal: 0.2 }),
      ])
      expect(result.map(item => item.label)).toEqual(['Technology', 'Finance'])
    })
  })

  describe('buildCountryChartData', () => {
    it('should aggregate holdings by country', () => {
      const result = buildCountryChartData([
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 30 }),
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 20 }),
        createHolding({ holdingCountryName: 'Germany', percentageOfTotal: 25 }),
      ])
      expect(result).toEqual([
        expect.objectContaining({ label: 'United States', value: 50 }),
        expect.objectContaining({ label: 'Germany', value: 25 }),
      ])
    })

    it('should handle unknown country as Unknown', () => {
      const result = buildCountryChartData([
        createHolding({ holdingCountryName: null, percentageOfTotal: 10 }),
      ])
      expect(result[0].label).toBe('Unknown')
    })

    it('should include country code for flag display', () => {
      const result = buildCountryChartData([
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
      ])
      expect(result.map(item => item.code)).toEqual(['US', 'DE'])
    })

    it('should drop countries below the 0.2% threshold', () => {
      const result = buildCountryChartData([
        createHolding({ holdingCountryName: 'United States', percentageOfTotal: 70 }),
        createHolding({ holdingCountryName: 'Germany', percentageOfTotal: 20 }),
        createHolding({ holdingCountryName: 'TinyCountry1', percentageOfTotal: 0.15 }),
        createHolding({ holdingCountryName: 'TinyCountry2', percentageOfTotal: 0.1 }),
      ])
      expect(result.map(item => item.label)).toEqual(['United States', 'Germany'])
    })
  })

  describe('getFilterParam', () => {
    it.each([
      [[], ['A', 'B', 'C'], undefined],
      [['A', 'B', 'C'], ['A', 'B', 'C'], undefined],
      [
        ['A', 'B'],
        ['A', 'B', 'C'],
        ['A', 'B'],
      ],
      [['B'], ['A', 'B', 'C'], ['B']],
      [
        [1, 2],
        [1, 2, 3, 4],
        [1, 2],
      ],
    ] as [(string | number)[], (string | number)[], (string | number)[] | undefined][])(
      'passes %j of %j to the api as %j',
      (selected, all, expected) => {
        expect(getFilterParam(selected, all)).toEqual(expected)
      }
    )
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
