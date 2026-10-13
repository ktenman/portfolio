import { afterEach, describe, expect, it } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import type { ProfitMonth } from '../../services/monthly-profit'
import { createPortfolioSummaryDto } from '../../tests/fixtures'
import SummaryHistoryTable from './summary-history-table.vue'

enableAutoUnmount(afterEach)

const closing = createPortfolioSummaryDto({
  date: '2025-07-31',
  totalValue: 178204.06,
  totalProfit: 12345.67,
  totalProfitChange24h: 999.99,
  earningsPerMonth: 321.45,
})

const month = (key: string, change: number, percent: number | null): ProfitMonth => ({
  key,
  opening: createPortfolioSummaryDto(),
  closing,
  rawChange: change,
  change,
  percent,
  factor: percent === null ? null : 1 + percent / 100,
  step: 'loss-3',
  eligible: true,
  coverage: ['from 27 Jun', 'No daily summary for 30 Dec'],
  name: 'July 2025',
  rank: '',
  closingLabel: 'Closed at €178,204.06',
  isCurrent: false,
})

const table = (months: ProfitMonth[]) =>
  mount(SummaryHistoryTable, {
    props: { rows: [closing], months, interval: 'monthly' },
  })

describe('Summary monthly history table', () => {
  it('uses the closing snapshot and keeps monthly change separate from the daily DTO', () => {
    const wrapper = table([month('2025-07', 60, -3)])
    const cells = wrapper.findAll('.desktop-table-wrapper tbody td')
    expect(cells[0].find('span').text()).toBe('Jul 2025')
    expect(cells[0].find('.month-coverage').text()).toBe(
      'from 27 Jun · No daily summary for 30 Dec'
    )
    expect(cells.slice(1).map(cell => cell.text())).toEqual([
      '10.00%',
      '€10.00',
      '€321.45',
      '€1,000.00',
      '€12,345.67',
      '+€60.00 (−3.0%)',
      '€178,204.06',
    ])
    const amount = wrapper.find('.desktop-table-wrapper .month-change-amount')
    const percent = wrapper.find('.desktop-table-wrapper .month-change-percent')
    expect(amount.classes()).toContain('text-gain')
    expect(percent.classes()).toContain('text-loss')
    expect(closing.totalProfitChange24h).toBe(999.99)
    expect(wrapper.findAll('.mobile-card-item')).toHaveLength(6)
    expect(wrapper.find('.mobile-card').text()).toContain('No daily summary for 30 Dec')
  })

  it('shows a valid percentage beside zero euros and unavailable percentages as a dash', () => {
    const wrapper = table([month('2025-07', 0, 1.2), month('2025-06', -5, null)])
    expect(
      wrapper.findAll('.desktop-table-wrapper .month-change-amount').map(cell => cell.text())
    ).toEqual(['€0.00', '−€5.00'])
    expect(
      wrapper.findAll('.desktop-table-wrapper .month-change-percent').map(cell => cell.text())
    ).toEqual(['(+1.2%)', '(—)'])
  })

  it('defaults to newest months and sorts Month Change numerically', async () => {
    const wrapper = table([month('2025-06', 9, 1), month('2025-07', 100, 2)])
    const firstMonth = () => wrapper.find('.desktop-table-wrapper tbody td').text()
    expect(firstMonth()).toContain('Jul 2025')
    await wrapper.findAll('.desktop-table-wrapper th')[6].trigger('click')
    expect(firstMonth()).toContain('Jun 2025')
    await wrapper.findAll('.desktop-table-wrapper th')[0].trigger('click')
    expect(firstMonth()).toContain('Jun 2025')
  })
})
