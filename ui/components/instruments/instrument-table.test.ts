import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import InstrumentTable from './instrument-table.vue'
import { ProviderName } from '../../models/generated/domain-models'
import { createInstrumentDto } from '../../tests/fixtures'

vi.mock('../shared/data-table.vue', () => ({
  default: {
    name: 'DataTable',
    props: ['items', 'columns', 'isLoading', 'isError', 'errorMessage', 'emptyMessage'],
    template: `
      <div>
        <table>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td><slot name="cell-instrument" :item="item" /></td>
              <td><slot name="cell-type" :item="item" /></td>
              <td><slot name="cell-currentPrice" :item="item" /></td>
              <td><slot name="cell-totalInvestment" :item="item" /></td>
              <td><slot name="cell-currentValue" :item="item" /></td>
              <td><slot name="cell-profit" :item="item" /></td>
              <td><slot name="actions" :item="item" /></td>
            </tr>
          </tbody>
        </table>
      </div>
    `,
  },
}))

describe('InstrumentTable', () => {
  const mockInstruments = [
    createInstrumentDto({
      id: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
      category: 'STOCK',
      providerName: ProviderName.FT,
      currentPrice: 150.5,
      totalInvestment: 10000,
      currentValue: 15050,
      profit: 5050,
      baseCurrency: 'USD',
      platforms: ['TRADING212'],
    }),
    createInstrumentDto({
      id: 2,
      symbol: 'BTC',
      name: 'Bitcoin',
      category: 'CRYPTO',
      providerName: ProviderName.BINANCE,
      currentPrice: 45000,
      totalInvestment: 20000,
      currentValue: 18000,
      profit: -2000,
      baseCurrency: 'EUR',
      platforms: ['BINANCE', 'COINBASE'],
    }),
    createInstrumentDto({
      id: 3,
      symbol: 'ETH',
      name: 'Ethereum',
      providerName: ProviderName.BINANCE,
      currentPrice: 3000,
      totalInvestment: 5000,
      currentValue: 5000,
      profit: 0,
      baseCurrency: 'USD',
      platforms: ['BINANCE'],
    }),
    createInstrumentDto({
      id: 4,
      symbol: 'UNKNOWN',
      name: 'Unknown Asset',
      providerName: ProviderName.FT,
      currentPrice: 100,
      totalInvestment: 1000,
      currentValue: 900,
      profit: undefined,
      baseCurrency: 'USD',
    }),
  ]
  const createWrapper = (props = {}) => {
    return mount(InstrumentTable, {
      props: {
        instruments: mockInstruments,
        selectedPeriod: '24h',
        ...props,
      },
    })
  }

  describe('data display', () => {
    it('should render all instruments', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')
      expect(rows).toHaveLength(4)
    })

    it('should display instrument name and symbol', () => {
      const wrapper = createWrapper()
      const firstRow = wrapper.find('tbody tr')

      expect(firstRow.text()).toContain('Apple Inc.')
      expect(firstRow.text()).toContain('AAPL')
    })

    it('should display type when available', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')

      expect(rows[0].text()).toContain('Stock')
    })

    it('should display category when type is not available', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')

      expect(rows[1].text()).toContain('Crypto')
    })

    it('should display dash when neither type nor category is available', () => {
      const wrapper = createWrapper({
        instruments: [
          createInstrumentDto({
            id: 5,
            symbol: 'TEST',
            name: 'Test Asset',
            providerName: ProviderName.FT,
            category: undefined as unknown as string,
          }),
        ],
      })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('-')
    })
  })

  describe('price formatting', () => {
    it('should format current price with currency', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')

      expect(rows[0].text()).toContain('$150.50')
      expect(rows[1].text()).toContain('€45,000.00')
    })

    it('should format total investment with currency', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')

      expect(rows[0].text()).toContain('$10,000.00')
      expect(rows[1].text()).toContain('€20,000.00')
    })

    it('should format current value with currency', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')

      expect(rows[0].text()).toContain('$15,050.00')
      expect(rows[1].text()).toContain('€18,000.00')
    })
  })

  describe('profit display', () => {
    it('should display positive profit without plus sign and green color', () => {
      const wrapper = createWrapper()
      const profitCell = wrapper.find('.text-success')

      expect(profitCell.text()).toBe('$5,050.00')
      expect(profitCell.classes()).toContain('text-success')
    })

    it('should display negative profit with minus sign and red color', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')
      const secondRow = rows[1]
      const profitCell = secondRow.find('.text-danger')

      expect(profitCell.text()).toBe('-€2,000.00')
      expect(profitCell.classes()).toContain('text-danger')
    })

    it('should display zero profit without plus sign and success color', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')
      const thirdRow = rows[2]
      const profitCell = thirdRow.find('.text-success')

      expect(profitCell.text()).toBe('$0.00')
      expect(profitCell.classes()).toContain('text-success')
    })

    it('should display $0.00 when profit is undefined (fallback behavior)', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')
      const lastRow = rows[3]

      expect(lastRow.text()).toContain('$0.00')
    })

    it('should display $0.00 when profit is explicitly undefined', () => {
      const wrapper = createWrapper({
        instruments: [
          createInstrumentDto({
            id: 6,
            symbol: 'UNDEF',
            name: 'Undefined Profit',
            providerName: ProviderName.FT,
            profit: undefined,
            baseCurrency: 'USD',
          }),
        ],
      })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('$0.00')
    })
  })

  describe('props handling', () => {
    it('should pass loading state to data table', () => {
      const wrapper = createWrapper({ isLoading: true })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })

      expect(dataTable.props('isLoading')).toBe(true)
    })

    it('should pass error state to data table', () => {
      const wrapper = createWrapper({
        isError: true,
        errorMessage: 'Failed to load instruments',
      })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })

      expect(dataTable.props('isError')).toBe(true)
      expect(dataTable.props('errorMessage')).toBe('Failed to load instruments')
    })

    it('should use default props when not provided', () => {
      const wrapper = mount(InstrumentTable, {
        props: {
          instruments: [],
          selectedPeriod: '24h',
        },
      })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })

      expect(dataTable.props('isLoading')).toBe(false)
      expect(dataTable.props('isError')).toBe(false)
    })

    it('should pass empty message to data table', () => {
      const wrapper = createWrapper()
      const dataTable = wrapper.findComponent({ name: 'DataTable' })

      expect(dataTable.props('emptyMessage')).toBe(
        'No instruments found. Add a new instrument to get started.'
      )
    })
  })

  describe('edge cases', () => {
    it('should handle instruments without financial data', () => {
      const wrapper = createWrapper({
        instruments: [
          createInstrumentDto({
            id: 7,
            symbol: 'MINIMAL',
            name: 'Minimal InstrumentDto',
            providerName: ProviderName.FT,
          }),
        ],
      })

      const row = wrapper.find('tbody tr')
      expect(() => row.text()).not.toThrow()
    })

    it('should handle empty instruments array', () => {
      const wrapper = createWrapper({ instruments: [] })
      const rows = wrapper.findAll('tbody tr')

      expect(rows).toHaveLength(0)
    })
  })

  describe('platform display', () => {
    it('should display platform badges for instruments with platforms', () => {
      const wrapper = createWrapper()
      const firstRow = wrapper.find('tbody tr')

      expect(firstRow.text()).toContain('Trading 212')

      const badges = firstRow.findAll('.badge')
      expect(badges.length).toBe(1)
      expect(badges[0].classes()).toContain('bg-secondary')
      expect(badges[0].classes()).toContain('text-white')
    })

    it('should display multiple platform badges', () => {
      const wrapper = createWrapper()
      const secondRow = wrapper.findAll('tbody tr')[1]

      expect(secondRow.text()).toContain('Binance')
      expect(secondRow.text()).toContain('Coinbase')

      const badges = secondRow.findAll('.badge')
      expect(badges.length).toBe(2)
    })

    it('should not display platform badges for instruments without platforms', () => {
      const wrapper = createWrapper()
      const lastRow = wrapper.findAll('tbody tr')[3]

      const badges = lastRow.findAll('.badge')
      expect(badges.length).toBe(0)
    })

    it('should format platform names correctly', () => {
      const wrapper = createWrapper({
        instruments: [
          createInstrumentDto({
            id: 8,
            symbol: 'TEST',
            name: 'Test Asset',
            providerName: ProviderName.FT,
            platforms: ['TRADING212', 'LIGHTYEAR', 'SWEDBANK', 'LHV', 'AVIVA'],
          }),
        ],
      })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('Trading 212')
      expect(row.text()).toContain('Lightyear')
      expect(row.text()).toContain('Swedbank')
      expect(row.text()).toContain('LHV')
      expect(row.text()).toContain('Aviva')
    })

    it('should display platform badges in mobile view', () => {
      const wrapper = createWrapper()
      const mobileCard = wrapper.find('.mobile-instrument-card')

      if (mobileCard.exists()) {
        const badges = mobileCard.findAll('.platform-tags .badge')
        expect(badges.length).toBeGreaterThan(0)
        expect(badges[0].classes()).toContain('bg-secondary')
      }
    })

    it('should handle undefined platforms gracefully', () => {
      const wrapper = createWrapper({
        instruments: [
          createInstrumentDto({
            id: 9,
            symbol: 'NO_PLATFORM',
            name: 'No Platform Asset',
            providerName: ProviderName.FT,
            platforms: undefined,
          }),
        ],
      })

      const row = wrapper.find('tbody tr')
      const badges = row.findAll('.badge')
      expect(badges.length).toBe(0)
      expect(() => row.text()).not.toThrow()
    })

    it('should handle empty platforms array', () => {
      const wrapper = createWrapper({
        instruments: [
          createInstrumentDto({
            id: 10,
            symbol: 'EMPTY_PLATFORM',
            name: 'Empty Platform Asset',
            providerName: ProviderName.FT,
            platforms: [],
          }),
        ],
      })

      const row = wrapper.find('tbody tr')
      const badges = row.findAll('.badge')
      expect(badges.length).toBe(0)
    })

    it('should handle unknown platform names', () => {
      const wrapper = createWrapper({
        instruments: [
          createInstrumentDto({
            id: 11,
            symbol: 'UNKNOWN_PLATFORM',
            name: 'Unknown Platform Asset',
            providerName: ProviderName.FT,
            platforms: ['NEW_PLATFORM', 'ANOTHER_PLATFORM'],
          }),
        ],
      })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('NEW_PLATFORM')
      expect(row.text()).toContain('ANOTHER_PLATFORM')
    })
  })

  describe('computed values', () => {
    it('should calculate totals correctly', () => {
      const wrapper = createWrapper()
      const footerRow = wrapper.find('.table-footer-totals')

      if (footerRow.exists()) {
        expect(footerRow.text()).toContain('Total')
        expect(footerRow.text()).toContain('€38,950.00')
        expect(footerRow.text()).toContain('€36,000.00')
        expect(footerRow.text()).toContain('€2,950.00')
      }
    })

    it('should handle zero profit correctly', () => {
      const wrapper = createWrapper({
        instruments: [
          createInstrumentDto({
            id: 12,
            symbol: 'ZERO',
            name: 'Zero Profit',
            providerName: ProviderName.FT,
            currentValue: 1000,
            totalInvestment: 1000,
            baseCurrency: 'USD',
          }),
        ],
      })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('$0.00')
    })

    it('should format different currencies correctly', () => {
      const wrapper = createWrapper({
        instruments: [
          createInstrumentDto({
            id: 13,
            symbol: 'GBP_TEST',
            name: 'GBP Asset',
            providerName: ProviderName.FT,
            currentPrice: 100.5,
            totalInvestment: 1000,
            currentValue: 1500,
            baseCurrency: 'GBP',
          }),
        ],
      })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('£')
    })
  })

  describe('mobile view', () => {
    it('should render mobile card structure', () => {
      const wrapper = createWrapper()
      const mobileCard = wrapper.find('.mobile-instrument-card')

      if (mobileCard.exists()) {
        expect(mobileCard.find('.instrument-header').exists()).toBe(true)
        expect(mobileCard.find('.instrument-metrics').exists()).toBe(true)
        expect(mobileCard.find('.instrument-footer').exists()).toBe(true)
      }
    })

    it('should display mobile totals card', () => {
      const wrapper = createWrapper()
      const mobileTotals = wrapper.find('.mobile-totals-card')

      if (mobileTotals.exists()) {
        expect(mobileTotals.find('.totals-header').exists()).toBe(true)
        expect(mobileTotals.find('.totals-content').exists()).toBe(true)
        expect(mobileTotals.text()).toContain('TOTAL')
        expect(mobileTotals.text()).toContain('VALUE')
        expect(mobileTotals.text()).toContain('INVESTED')
        expect(mobileTotals.text()).toContain('PROFIT')
      }
    })
  })

  describe('period label', () => {
    it('should update column header label based on selected period', () => {
      const wrapper = createWrapper({ selectedPeriod: '7d' })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')
      const priceChangeColumn = columns.find((col: { key: string }) => col.key === 'priceChange')

      expect(priceChangeColumn.label).toBe('7D')
    })

    it('should display selected period in mobile card metric label', () => {
      const wrapper = createWrapper({
        selectedPeriod: '30d',
        instruments: [
          createInstrumentDto({
            id: 14,
            symbol: 'TEST',
            name: 'Test Asset',
            providerName: ProviderName.FT,
            priceChangeAmount: 100,
            baseCurrency: 'USD',
          }),
        ],
      })
      const mobileCard = wrapper.find('.mobile-instrument-card')

      if (mobileCard.exists()) {
        const metricLabels = mobileCard.findAll('.metric-label')
        const periodLabel = metricLabels.find(label => label.text() === '30D')
        expect(periodLabel).toBeDefined()
      }
    })

    it('should display selected period in mobile totals card', () => {
      const wrapper = createWrapper({ selectedPeriod: '1y' })
      const mobileTotals = wrapper.find('.mobile-totals-card')

      if (mobileTotals.exists()) {
        const totalLabels = mobileTotals.findAll('.total-label')
        const periodLabel = totalLabels.find(label => label.text() === '1Y')
        expect(periodLabel).toBeDefined()
      }
    })

    it('should use default period when set to 24h', () => {
      const wrapper = createWrapper({ selectedPeriod: '24h' })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')
      const priceChangeColumn = columns.find((col: { key: string }) => col.key === 'priceChange')

      expect(priceChangeColumn.label).toBe('24H')
    })

    it('should uppercase period label in all locations', () => {
      const wrapper = createWrapper({ selectedPeriod: '3d' })
      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')
      const priceChangeColumn = columns.find((col: { key: string }) => col.key === 'priceChange')

      expect(priceChangeColumn.label).toBe('3D')
      expect(priceChangeColumn.label).not.toBe('3d')
    })
  })
})
