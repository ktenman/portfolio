import { describe, it, expect, vi } from 'vitest'
import { createWrapper, createWrapperWithInstrument } from './instrument-table-fixture'

vi.mock('../shared/data-table.vue', async () => ({
  default: (await import('../../tests/fixtures')).instrumentDataTableStub,
}))

const dataTable = (props = {}) => createWrapper(props).findComponent({ name: 'DataTable' })

describe('InstrumentTable', () => {
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
      const wrapper = createWrapperWithInstrument({ category: undefined as unknown as string })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('-')
    })
  })

  describe('price formatting', () => {
    it.each([
      { field: 'current price', usd: '$150.50', eur: '€45,000' },
      { field: 'total investment', usd: '$10,000.00', eur: '€20,000.00' },
      { field: 'current value', usd: '$15,050.00', eur: '€18,000.00' },
    ])('should format the $field with currency', ({ usd, eur }) => {
      const wrapper = createWrapper()
      const rows = wrapper.findAll('tbody tr')

      expect(rows[0].text()).toContain(usd)
      expect(rows[1].text()).toContain(eur)
    })
  })

  describe('profit display', () => {
    it.each([
      { row: 0, color: 'text-gain', text: '$5,050.00' },
      { row: 1, color: 'text-loss', text: '−€2,000.00' },
      { row: 2, color: 'text-gain', text: '$0.00' },
    ])(
      'should display the profit of row $row as a $color reading $text',
      ({ row, color, text }) => {
        const wrapper = createWrapper()
        const profitCell = wrapper.findAll('tbody tr')[row].find(`.${color}`)

        expect(profitCell.text()).toBe(text)
      }
    )

    it('should display $0.00 when profit is undefined', () => {
      const wrapper = createWrapperWithInstrument({ profit: undefined, baseCurrency: 'USD' })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('$0.00')
    })
  })

  describe('props handling', () => {
    it('should pass loading state to data table', () => {
      expect(dataTable({ isLoading: true }).props('isLoading')).toBe(true)
    })

    it('should pass error state to data table', () => {
      const table = dataTable({ isError: true, errorMessage: 'Failed to load instruments' })

      expect(table.props('isError')).toBe(true)
      expect(table.props('errorMessage')).toBe('Failed to load instruments')
    })

    it('should use default props when not provided', () => {
      const table = dataTable({ instruments: [] })

      expect(table.props('isLoading')).toBe(false)
      expect(table.props('isError')).toBe(false)
    })

    it('should pass empty message to data table', () => {
      expect(dataTable().props('emptyMessage')).toBe(
        'No instruments found. Add a new instrument to get started.'
      )
    })
  })

  describe('edge cases', () => {
    it('should handle instruments without financial data', () => {
      const wrapper = createWrapperWithInstrument({ symbol: 'MINIMAL', name: 'Minimal Instrument' })

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
