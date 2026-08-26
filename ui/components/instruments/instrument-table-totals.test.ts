import { describe, it, expect, vi } from 'vitest'
import { TimeRange } from '../../models/generated/domain-models'
import { createWrapper, createWrapperWithInstrument } from './instrument-table-fixture'

vi.mock('../shared/data-table.vue', async () => ({
  default: (await import('../../tests/fixtures')).instrumentDataTableStub,
}))

describe('InstrumentTable', () => {
  describe('computed values', () => {
    it('should calculate totals correctly', () => {
      const wrapper = createWrapper()
      const totals = wrapper.find('.mobile-totals-card')

      expect(totals.text()).toContain('€38,950.00')
      expect(totals.text()).toContain('€36,000.00')
      expect(totals.text()).toContain('€3,050.00')
    })

    it('should handle zero profit correctly', () => {
      const wrapper = createWrapperWithInstrument({
        currentValue: 1000,
        totalInvestment: 1000,
        baseCurrency: 'USD',
      })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('$0.00')
    })

    it('should format different currencies correctly', () => {
      const wrapper = createWrapperWithInstrument({ baseCurrency: 'GBP' })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('£')
    })
  })

  describe('mobile view', () => {
    it('should render mobile card structure', () => {
      const wrapper = createWrapper()
      const mobileCard = wrapper.find('.mobile-instrument-card')

      expect(mobileCard.find('.instrument-header').exists()).toBe(true)
      expect(mobileCard.find('.instrument-metrics').exists()).toBe(true)
      expect(mobileCard.find('.instrument-footer').exists()).toBe(true)
    })

    it('should display mobile totals card', () => {
      const wrapper = createWrapper()
      const mobileTotals = wrapper.find('.mobile-totals-card')

      expect(mobileTotals.find('.totals-header').exists()).toBe(true)
      expect(mobileTotals.find('.totals-content').exists()).toBe(true)
      expect(mobileTotals.text()).toContain('TOTAL')
      expect(mobileTotals.text()).toContain('VALUE')
      expect(mobileTotals.text()).toContain('INVESTED')
      expect(mobileTotals.text()).toContain('PROFIT')
    })

    it('should sign a falling period change so it cannot read as a gain', () => {
      const wrapper = createWrapperWithInstrument({ symbol: 'FÄLL', priceChangeAmount: -84.19 })
      const changeMetric = wrapper.find('.mobile-instrument-card .text-loss')

      expect(changeMetric.text()).toBe('−€84.19')
    })
  })

  describe('period totals', () => {
    const switchToLongerPeriod = async () => {
      const wrapper = createWrapper({ rangeChange: { changeAmount: 100, changePercent: 1 } })
      await wrapper.setProps({
        selectedPeriod: TimeRange.ONE_YEAR,
        rangeChange: { changeAmount: 5000, changePercent: 40 },
      })
      return wrapper.find('.total-price-change-item .total-value')
    }

    it('should report the portfolio range change instead of the sum of the rows', () => {
      const wrapper = createWrapperWithInstrument(
        { priceChangeAmount: 689.62 },
        { rangeChange: { changeAmount: 11862.06, changePercent: 20.29 } }
      )
      const change = wrapper.find('.total-price-change-item .total-value')

      expect(change.text()).toBe('€11,862.06 / 20.29%')
    })

    it('should not show a change until the range change has arrived', () => {
      const wrapper = createWrapper()
      const change = wrapper.find('.total-price-change-item .total-value')

      expect(change.text()).toBe('-')
    })

    it('should jump to the new period change instead of animating from the old one', async () => {
      expect((await switchToLongerPeriod()).text()).toBe('€5,000.00 / 40.00%')
    })

    it('should dont flash the totals when only the period changed', async () => {
      expect((await switchToLongerPeriod()).classes()).not.toContain('value-increase')
    })
  })

  describe('period label', () => {
    it.each([
      [TimeRange.ONE_DAY, '1D'],
      [TimeRange.ONE_WEEK, '1W'],
    ])('should label the price change column with %s as %s', (selectedPeriod, label) => {
      const wrapper = createWrapper({ selectedPeriod })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')
      const priceChangeColumn = columns.find((col: { key: string }) => col.key === 'priceChange')

      expect(priceChangeColumn.label).toBe(label)
    })

    it('should display selected period in mobile card metric label', () => {
      const wrapper = createWrapperWithInstrument(
        { priceChangeAmount: 100 },
        { selectedPeriod: TimeRange.ONE_MONTH }
      )
      const mobileCard = wrapper.find('.mobile-instrument-card')

      const metricLabels = mobileCard.findAll('.metric-label')
      expect(metricLabels.map(label => label.text())).toContain('1M')
    })

    it('should display selected period in mobile totals card', () => {
      const wrapper = createWrapper({ selectedPeriod: TimeRange.ONE_YEAR })
      const mobileTotals = wrapper.find('.mobile-totals-card')

      const totalLabels = mobileTotals.findAll('.total-label')
      expect(totalLabels.map(label => label.text())).toContain('1Y')
    })
  })
})
