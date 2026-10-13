import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SummaryHistoryTable from './summary-history-table.vue'
import { buildMonthlyProfit, type MonthResult } from '../../services/monthly-profit'
import { mergeSummariesByDate } from '../../services/summary-aggregator'
import { createPortfolioSummaryDto } from '../../tests/fixtures'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../../tests/visual/summary-fixture'

const row = (date: string, totalProfit = 0, totalValue = 1000) =>
  createPortfolioSummaryDto({ date, totalProfit, totalValue })

const FIXTURE = buildMonthlyProfit(
  mergeSummariesByDate(SERIES_RESPONSE, [TODAY_SUMMARY]),
  '2026-01-15'
)!.months

const createTable = (months: MonthResult[] | null = FIXTURE) =>
  mount(SummaryHistoryTable, {
    props: {
      days: [
        createPortfolioSummaryDto({ date: '2025-12-31', totalProfitChange24h: 120 }),
        createPortfolioSummaryDto({ date: '2025-12-30', totalProfitChange24h: null }),
      ],
      months,
    },
  })

type Table = ReturnType<typeof createTable>

const labelOf = (text: string) => text.replace(/[\s▲▼]+$/, '')
const headers = (wrapper: Table) => wrapper.findAll('thead th').map(th => labelOf(th.text()))
const rows = (wrapper: Table) =>
  wrapper.findAll('tbody tr').map(tr => tr.findAll('td').map(td => td.text().replace(/\s+/g, ' ')))
const firstCells = (wrapper: Table) => rows(wrapper).map(cells => cells[0])
const sortBy = (wrapper: Table, label: string) =>
  wrapper
    .findAll('thead th')
    .find(th => labelOf(th.text()) === label)!
    .trigger('click')

describe('SummaryHistoryTable', () => {
  it('lists the days under the daily headers without months', () => {
    const wrapper = createTable(null)
    expect([
      headers(wrapper)[0],
      headers(wrapper)[6],
      firstCells(wrapper),
      rows(wrapper)[0][6],
    ]).toEqual(['Date', '24h Change', ['31.12.25', '30.12.25'], '€120.00 (1.21%)'])
  })

  it('lists the months newest first under the monthly headers', () => {
    const wrapper = createTable()
    expect([headers(wrapper), firstCells(wrapper).map(cell => cell.slice(0, 8))]).toEqual([
      [
        'Month',
        'XIRR Annual Return',
        'Earnings Per Day',
        'Earnings Per Month',
        'Unrealized Profit',
        'Total Profit',
        'Month Change',
        'Total Value',
      ],
      ['Dec 2025', 'Nov 2025', 'Oct 2025', 'Sep 2025', 'Aug 2025', 'Jul 2025', 'Jun 2025'],
    ])
  })

  it('fills a month row from its closing snapshot and its own change', () => {
    const [december] = rows(createTable())
    expect([december[0], december[1], december[6], december[7]]).toEqual([
      'Dec 2025 No daily summary for 30 Dec',
      '21.18%',
      '€9,110.23 (5.4%)',
      '€178,204.06',
    ])
  })

  it('adds the coverage of a partial month beside its name', () => {
    expect(firstCells(createTable())[6].startsWith('Jun 2025 from 27 Jun')).toBe(true)
  })

  it('keeps six columns on the phone card', () => {
    const labels = createTable().find('.mobile-card-body').findAll('.mobile-card-item')
    expect(labels).toHaveLength(6)
  })

  it('sorts by month and by month change', async () => {
    const wrapper = createTable()
    await sortBy(wrapper, 'Month')
    const oldest = firstCells(wrapper)[0].slice(0, 8)
    await sortBy(wrapper, 'Month Change')
    await sortBy(wrapper, 'Month Change')

    expect([oldest, firstCells(wrapper)[0].slice(0, 8)]).toEqual(['Jun 2025', 'Nov 2025'])
  })

  it('keeps a sort for each interval', async () => {
    const wrapper = createTable()
    await sortBy(wrapper, 'Month')

    await wrapper.setProps({ months: null })
    const days = firstCells(wrapper)[0]
    await wrapper.setProps({ months: FIXTURE })

    expect([days, firstCells(wrapper)[0].slice(0, 8)]).toEqual(['31.12.25', 'Jun 2025'])
  })

  it('colours the euros and the percentage of a month change each by its own sign', () => {
    const months = buildMonthlyProfit(
      [row('2025-01-31'), row('2025-02-10', -100, 900), row('2025-02-28', 60, 2217.14)],
      '2030-01-15'
    )!.months
    const [amount, percent] = createTable(months).findAll('tbody .month-change > span')
    expect([amount.text(), amount.classes(), percent.text(), percent.classes()]).toEqual([
      '€60.00',
      ['text-gain'],
      '(−3.0%)',
      ['change-percentage', 'text-loss'],
    ])
  })

  it('prints a zero change with its percentage and a dash for an unavailable one', () => {
    const flat = buildMonthlyProfit(
      [row('2025-01-31'), row('2025-02-10', -4, 1996), row('2025-02-28', 0, 670.67)],
      '2030-01-15'
    )!.months
    const unknown = buildMonthlyProfit(
      [row('2025-01-31'), row('2025-02-28', 10, 10)],
      '2030-01-15'
    )!.months
    expect([rows(createTable(flat))[0][6], rows(createTable(unknown))[0][6]]).toEqual([
      '€0.00 (0.4%)',
      '€10.00 (—)',
    ])
  })
})
