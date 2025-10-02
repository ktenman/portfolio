import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import InstrumentTable from './instrument-table.vue'
import { Instrument } from '../../models/instrument'
import { ProviderName } from '../../models/provider-name'

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

vi.mock('../shared/base-icon.vue', () => ({
  default: {
    name: 'BaseIcon',
    props: ['name', 'size'],
    template: '<i :class="`icon-${name}`" />',
  },
}))

describe('InstrumentTable', () => {
  const mockInstruments: Instrument[] = [
    {
      id: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
      type: 'STOCK',
      providerName: ProviderName.ALPHA_VANTAGE,
      currentPrice: 150.5,
      totalInvestment: 10000,
      currentValue: 15050,
      profit: 5050,
      baseCurrency: 'USD',
    },
    {
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
    },
    {
      id: 3,
      symbol: 'ETH',
      name: 'Ethereum',
      providerName: ProviderName.BINANCE,
      currentPrice: 3000,
      totalInvestment: 5000,
      currentValue: 5000,
      profit: 0,
      baseCurrency: 'USD',
    },
    {
      id: 4,
      symbol: 'UNKNOWN',
      name: 'Unknown Asset',
      providerName: ProviderName.FT,
      currentPrice: 100,
      totalInvestment: 1000,
      currentValue: 900,
      profit: undefined,
      baseCurrency: 'USD',
    },
  ]

  const createWrapper = (props = {}) => {
    return mount(InstrumentTable, {
      props: {
        instruments: mockInstruments,
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

      expect(rows[0].text()).toContain('STOCK')
    })

    it('should display category when type is not available', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')

      expect(rows[1].text()).toContain('CRYPTO')
    })

    it('should display dash when neither type nor category is available', () => {
      const wrapper = createWrapper({
        instruments: [
          {
            id: 5,
            symbol: 'TEST',
            name: 'Test Asset',
            providerName: ProviderName.FT,
          },
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
    it('should display positive profit with plus sign and green color', () => {
      const wrapper = createWrapper()
      const profitCell = wrapper.find('.text-success')

      expect(profitCell.text()).toBe('+$5,050.00')
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

    it('should display zero profit with plus sign and success color', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')
      const thirdRow = rows[2]
      const profitCell = thirdRow.find('.text-success')

      expect(profitCell.text()).toBe('+$0.00')
      expect(profitCell.classes()).toContain('text-success')
    })

    it('should calculate profit as value minus invested when profit is undefined', () => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')
      const lastRow = rows[3]

      expect(lastRow.text()).toContain('-$100.00')
    })

    it('should display $0.00 when profit is explicitly undefined', () => {
      const wrapper = createWrapper({
        instruments: [
          {
            id: 6,
            symbol: 'UNDEF',
            name: 'Undefined Profit',
            providerName: ProviderName.FT,
            profit: undefined,
            baseCurrency: 'USD',
          },
        ],
      })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('$0.00')
    })
  })

  describe('actions', () => {
    it('should render edit button for each instrument', () => {
      const wrapper = createWrapper()
      const editButtons = wrapper.findAll('button')

      expect(editButtons).toHaveLength(4)
      editButtons.forEach(button => {
        expect(button.text()).toContain('Edit')
        expect(button.find('.icon-pencil').exists()).toBe(true)
      })
    })

    it('should emit edit event when edit button is clicked', async () => {
      const wrapper = createWrapper()
      const firstEditButton = wrapper.find('button')

      await firstEditButton.trigger('click')

      expect(wrapper.emitted('edit')).toBeTruthy()
      expect(wrapper.emitted('edit')?.[0]).toEqual([mockInstruments[0]])
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
          {
            id: 7,
            symbol: 'MINIMAL',
            name: 'Minimal Instrument',
            providerName: ProviderName.FT,
          },
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
})
