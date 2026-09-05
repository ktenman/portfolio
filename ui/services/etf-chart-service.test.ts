import { describe, it, expect } from 'vitest'
import {
  buildSectorChartData,
  buildCompanyChartData,
  buildCountryChartData,
  buildIndustryChartData,
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
  holdingIndustry: 'Software',
  holdingGicsSector: 'Information Technology',
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

  describe('buildIndustryChartData', () => {
    it('should aggregate holdings by industry', () => {
      const result = buildIndustryChartData([
        createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 8 }),
        createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 4 }),
        createHolding({ holdingIndustry: 'Software', percentageOfTotal: 5 }),
      ])
      expect(result.map(item => [item.label, item.value])).toEqual([
        ['Banks', 12],
        ['Software', 5],
      ])
    })

    it('should label holdings without an industry as Unclassified', () => {
      const result = buildIndustryChartData([
        createHolding({ holdingIndustry: null, percentageOfTotal: 3 }),
      ])
      expect(result[0].label).toBe('Unclassified')
    })

    it('should fold everything beyond the top 15 into Other so the chart sums to the holdings total', () => {
      const holdings = Array.from({ length: 20 }, (_, i) =>
        createHolding({ holdingIndustry: `Industry ${i}`, percentageOfTotal: 5 })
      )
      const result = buildIndustryChartData(holdings)
      expect([
        result.length,
        result[result.length - 1].label,
        result.reduce((sum, item) => sum + item.value, 0),
      ]).toEqual([16, 'Other', 100])
    })

    it('should fold industries below the 0.5% floor into Other instead of dropping them', () => {
      const result = buildIndustryChartData([
        createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 99.7 }),
        createHolding({ holdingIndustry: 'Tobacco', percentageOfTotal: 0.3 }),
      ])
      expect(result.map(item => item.label)).toEqual(['Banks', 'Other'])
    })

    it('should attach the benchmark share and the ratio per industry', () => {
      const result = buildIndustryChartData(
        [createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 10 })],
        [createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 4 })]
      )
      expect([result[0].benchmark, result[0].ratio]).toEqual([4, 2.5])
    })

    it('should leave the ratio undefined when the benchmark has no weight in the industry', () => {
      const result = buildIndustryChartData(
        [createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 10 })],
        [createHolding({ holdingIndustry: 'Software', percentageOfTotal: 100 })]
      )
      expect(result.map(item => [item.label, item.value, item.benchmark, item.ratio])).toEqual([
        ['Banks', 10, 0, undefined],
        ['Other', 0, 100, undefined],
      ])
    })

    it('should leave the ratio undefined when the benchmark share would display as zero', () => {
      const result = buildIndustryChartData(
        [createHolding({ holdingIndustry: 'Cryptocurrency', percentageOfTotal: 2.67 })],
        [
          createHolding({ holdingIndustry: 'Cryptocurrency', percentageOfTotal: 0.004 }),
          createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 99.996 }),
        ]
      )
      expect([result[0].benchmark, result[0].ratio]).toEqual([0.004, undefined])
    })

    it('should keep benchmark-only weight visible under Other when the portfolio has no residual', () => {
      const result = buildIndustryChartData(
        [createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 100 })],
        [
          createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 98 }),
          createHolding({ holdingIndustry: 'Real Estate', percentageOfTotal: 2 }),
        ]
      )
      const last = result[result.length - 1]
      expect([last.label, last.value, last.benchmark]).toEqual(['Other', 0, 2])
    })

    it('should not emit Other for a benchmark-free portfolio with no residual', () => {
      const result = buildIndustryChartData([
        createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 100 }),
      ])
      expect(result.map(item => item.label)).toEqual(['Banks'])
    })

    it('should put the benchmark weight of unshown industries under Other without a ratio', () => {
      const holdings = Array.from({ length: 16 }, (_, i) =>
        createHolding({ holdingIndustry: `Industry ${i}`, percentageOfTotal: 5 })
      )
      const result = buildIndustryChartData(holdings, [
        createHolding({ holdingIndustry: 'Industry 15', percentageOfTotal: 30 }),
      ])
      const other = result[result.length - 1]
      expect([other.label, other.benchmark, other.ratio]).toEqual(['Other', 30, undefined])
    })

    it('should not attach benchmark fields when no benchmark holdings are given', () => {
      const result = buildIndustryChartData([
        createHolding({ holdingIndustry: 'Banks', percentageOfTotal: 10 }),
      ])
      expect('benchmark' in result[0]).toBe(false)
    })
  })
})
