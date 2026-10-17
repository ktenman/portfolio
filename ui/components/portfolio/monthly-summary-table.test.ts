import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import type { MonthlyProfit } from '../../services/monthly-profit'
import { createCalendarRow as row, createMonthlyProfit } from '../../tests/fixtures'
import SkeletonLoader from '../shared/skeleton-loader.vue'
import MonthlySummaryTable from './monthly-summary-table.vue'

enableAutoUnmount(afterEach)

const february = (rows: PortfolioSummaryDto[]) => createMonthlyProfit(rows, '2030-01-01')

const mountTable = (model: MonthlyProfit | undefined) =>
  mount(MonthlySummaryTable, { props: { model } })

type Wrapper = ReturnType<typeof mountTable>

const cellsOf = (wrapper: Wrapper, label: string) =>
  wrapper.findAll(`tbody td[data-label="${label}"]`)

const firstChange = (wrapper: Wrapper) =>
  cellsOf(wrapper, 'Month Change')[0]
    .findAll('span')
    .map(span => [span.text(), span.classes().filter(name => /^text-(gain|loss)$/.test(name))])

const monthOrder = (wrapper: Wrapper) =>
  cellsOf(wrapper, 'Month').map(cell => cell.find('.month-label').text())

const header = (wrapper: Wrapper, label: string) =>
  wrapper.findAll('thead th').find(th => th.text().startsWith(label))!

describe('MonthlySummaryTable', () => {
  it('lists one row per month, newest first', () => {
    const wrapper = mountTable(createMonthlyProfit())

    expect([
      monthOrder(wrapper),
      cellsOf(wrapper, 'Month')[6].find('.month-coverage').text(),
    ]).toEqual([
      ['Dec 2025', 'Nov 2025', 'Oct 2025', 'Sep 2025', 'Aug 2025', 'Jul 2025', 'Jun 2025'],
      'from 27 Jun',
    ])
  })

  it('renames the date and change columns', () =>
    expect(
      mountTable(createMonthlyProfit())
        .findAll('thead th')
        .map(th => th.text().replace(/[▲▼]/g, '').trim())
    ).toEqual([
      'Month',
      'XIRR Annual Return',
      'Earnings Per Day',
      'Earnings Per Month',
      'Unrealized Profit',
      'Total Profit',
      'Month Change',
      'Total Value',
    ]))

  it('shows the closing snapshot unchanged', () => {
    const wrapper = mountTable(createMonthlyProfit())

    expect([
      cellsOf(wrapper, 'Total Value')[0].text(),
      cellsOf(wrapper, 'XIRR Annual Return')[0].text(),
    ]).toEqual(['€178,204.06', '21.18%'])
  })

  it('colours the month change and percentage separately', () => {
    const opposite = february([
      row('2025-01-31', 0, 1000),
      row('2025-02-01', -40, 960),
      row('2025-02-02', 60, 10100),
    ])

    expect([
      firstChange(mountTable(createMonthlyProfit())),
      firstChange(mountTable(opposite)),
    ]).toEqual([
      [
        ['€9,110.23', ['text-gain']],
        ['(5.4%)', ['text-gain']],
      ],
      [
        ['€60.00', ['text-gain']],
        ['(−3.0%)', ['text-loss']],
      ],
    ])
  })

  it('keeps a zero euro change beside a non-zero percentage', () => {
    const netZero = february([
      row('2025-01-31', 0, 1000),
      row('2025-02-01', 100, 1100),
      row('2025-02-02', 0, 100),
    ])

    expect(firstChange(mountTable(netZero))).toEqual([
      ['€0.00', []],
      ['(−45.0%)', ['text-loss']],
    ])
  })

  it('prints a tiny loss as an uncoloured 0.0%', () => {
    const tiny = february([row('2025-01-31', 0), row('2025-02-28', -40, 99960)])

    expect(firstChange(mountTable(tiny))[1]).toEqual(['(0.0%)', []])
  })

  it('shows a dash for an unavailable percentage', () => {
    const unavailable = february([row('2025-01-31', 0), row('2025-02-01', 100, 100)])

    expect(firstChange(mountTable(unavailable))).toEqual([
      ['€100.00', ['text-gain']],
      ['(—)', []],
      ['percentage unavailable', []],
    ])
  })

  it('sorts months by key and month change by number', async () => {
    const wrapper = mountTable(createMonthlyProfit())

    await header(wrapper, 'Month').trigger('click')
    const byMonth = monthOrder(wrapper)[0]
    await header(wrapper, 'Month Change').trigger('click')

    expect([byMonth, monthOrder(wrapper)]).toEqual([
      'Jun 2025',
      ['Jun 2025', 'Jul 2025', 'Oct 2025', 'Sep 2025', 'Dec 2025', 'Aug 2025', 'Nov 2025'],
    ])
  })

  it('shows a loading table until the months arrive', () => {
    const wrapper = mountTable(undefined)

    expect([
      wrapper.findComponent(SkeletonLoader).exists(),
      wrapper.find('tbody').exists(),
    ]).toEqual([true, false])
  })

  it('keeps the six mobile card fields', () =>
    expect(
      mountTable(createMonthlyProfit())
        .find('.mobile-card')
        .findAll('.label')
        .map(label => label.text())
    ).toEqual([
      'Month',
      'XIRR Annual Return',
      'Earnings Per Month',
      'Total Profit',
      'Month Change',
      'Total Value',
    ]))
})
