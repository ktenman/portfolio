import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { createPortfolioSummaryDto } from '../../tests/fixtures'
import DailySummaryTable from './daily-summary-table.vue'

enableAutoUnmount(afterEach)

describe('DailySummaryTable', () => {
  it('renders the columns and 24h change as the page did', () => {
    const wrapper = mount(DailySummaryTable, {
      props: {
        summaries: [
          createPortfolioSummaryDto({
            date: '2025-12-02',
            totalValue: 100000,
            totalProfitChange24h: 250.5,
          }),
          createPortfolioSummaryDto({
            date: '2025-12-01',
            totalValue: 100000,
            totalProfitChange24h: 0.005,
          }),
        ],
      },
    })
    const changes = wrapper.findAll('tbody td[data-label="24h Change"]')

    expect([
      wrapper.findAll('thead th').map(th => th.text().replace(/[▲▼]/g, '').trim()),
      changes[0].find('.text-gain').text().split(/\s+/),
      changes[1].text(),
    ]).toEqual([
      [
        'Date',
        'XIRR Annual Return',
        'Earnings Per Day',
        'Earnings Per Month',
        'Unrealized Profit',
        'Total Profit',
        '24h Change',
        'Total Value',
      ],
      ['€250.50', '(0.25%)'],
      '',
    ])
  })
})
