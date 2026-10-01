import { describe, it, expect } from 'vitest'
import { buildFundChartData, cashWeight, compareFunds, formatReportDate } from './fund-allocation'
import type { FundReportDto } from '../models/generated/domain-models'

const report = (asOfDate: string, funds: [string, number][]): FundReportDto => ({
  asOfDate,
  funds: funds.map(([isin, weight]) => ({ isin, name: `Fond ${isin} õ`, weight })),
})

const AUGUST = report('2026-08-31', [
  ['IE000I9HGDZ3', 29.36],
  ['IE000QWCYQT0', 28.9],
  ['IE00BKPTWY98', 12.55],
])

const JULY = report('2026-07-31', [
  ['IE000I9HGDZ3', 29.67],
  ['IE0009FT4LX4', 29.39],
  ['IE00BKPTWY98', 11.61],
])

describe('fund-allocation', () => {
  it('marks a fund missing from the previous report as new', () => {
    expect(compareFunds(AUGUST, JULY).find(row => row.isin === 'IE000QWCYQT0')?.status).toBe('new')
  })

  it('lists a fund the previous report held as dropped without a weight', () => {
    expect(compareFunds(AUGUST, JULY).find(row => row.isin === 'IE0009FT4LX4')).toMatchObject({
      status: 'dropped',
      weight: null,
    })
  })

  it('marks a fund both reports hold as held', () => {
    expect(compareFunds(AUGUST, JULY).find(row => row.isin === 'IE00BKPTWY98')?.status).toBe('held')
  })

  it('treats every fund of the oldest report as held', () => {
    expect(compareFunds(JULY).map(row => row.status)).toEqual(['held', 'held', 'held'])
  })

  it('puts dropped funds after the funds the report holds', () => {
    expect(compareFunds(AUGUST, JULY).map(row => row.isin)).toEqual([
      'IE000I9HGDZ3',
      'IE000QWCYQT0',
      'IE00BKPTWY98',
      'IE0009FT4LX4',
    ])
  })

  it('counts whatever the funds leave of 100 percent as cash', () => {
    expect(cashWeight(AUGUST)).toBeCloseTo(29.19)
  })

  it('charts each fund and the cash remainder', () => {
    expect(buildFundChartData(AUGUST).map(row => row.label)).toEqual([
      'Fond IE000I9HGDZ3 õ',
      'Fond IE000QWCYQT0 õ',
      'Fond IE00BKPTWY98 õ',
      'Cash',
    ])
  })

  it('leaves cash out of the chart when the funds fill the whole report', () => {
    expect(
      buildFundChartData(report('2026-08-31', [['IE000I9HGDZ3', 100]])).map(row => row.label)
    ).toEqual(['Fond IE000I9HGDZ3 õ'])
  })

  it('formats the report date day first', () => {
    expect(formatReportDate('2026-08-31')).toBe('31.08.2026')
  })
})
