import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EtfBreakdownTable from './etf-breakdown-table.vue'
import type { EtfHoldingBreakdownDto } from '../../models/generated/domain-models'

describe('EtfBreakdownTable', () => {
  const mockHoldings: EtfHoldingBreakdownDto[] = [
    {
      holdingTicker: 'AAPL',
      holdingName: 'Apple Inc.',
      percentageOfTotal: 25.5432,
      totalValueEur: 10000,
      holdingSector: 'Technology',
      holdingCountryCode: 'US',
      holdingCountryName: 'United States',
      inEtfs: 'IITU:50%, VUSA:30%',
      numEtfs: 2,
      platforms: 'LIGHTYEAR, TRADING212',
    },
    {
      holdingTicker: 'MSFT',
      holdingName: 'Microsoft Corp.',
      percentageOfTotal: 20.1234,
      totalValueEur: 8000,
      holdingSector: 'Technology',
      holdingCountryCode: 'US',
      holdingCountryName: 'United States',
      inEtfs: 'IITU:40%',
      numEtfs: 1,
      platforms: 'LIGHTYEAR',
    },
  ]

  describe('loading state', () => {
    it('should show loading spinner when isLoading is true', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: [],
          isLoading: true,
          isError: false,
          errorMessage: '',
        },
      })

      expect(wrapper.findComponent({ name: 'LoadingSpinner' }).exists()).toBe(true)
      expect(wrapper.findComponent({ name: 'DataTable' }).exists()).toBe(false)
    })

    it('should not show loading spinner when isLoading is false', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      expect(wrapper.findComponent({ name: 'LoadingSpinner' }).exists()).toBe(false)
    })
  })

  describe('error state', () => {
    it('should show error message when isError is true', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: [],
          isLoading: false,
          isError: true,
          errorMessage: 'Failed to load data',
        },
      })

      const alert = wrapper.find('.alert-danger')
      expect(alert.exists()).toBe(true)
      expect(alert.text()).toContain('Failed to load data')
    })

    it('should not show data table when isError is true', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: true,
          errorMessage: 'Error occurred',
        },
      })

      expect(wrapper.findComponent({ name: 'DataTable' }).exists()).toBe(false)
    })
  })

  describe('empty state', () => {
    it('should show info message when holdings array is empty', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: [],
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const emptyState = wrapper.find('.empty-state')
      expect(emptyState.exists()).toBe(true)
      expect(wrapper.find('.empty-state-title').text()).toContain('No data found')
    })
  })

  describe('data display', () => {
    it('should render DataTable when holdings are available', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      expect(wrapper.findComponent({ name: 'DataTable' }).exists()).toBe(true)
    })

    it('should pass correct props to DataTable', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      expect(dataTable.props('items')).toEqual(mockHoldings)
      expect(dataTable.props('isLoading')).toBe(false)
      expect(dataTable.props('isError')).toBe(false)
    })

    it('should render footer with total value', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const footer = wrapper.find('.table-footer-totals')
      expect(footer.exists()).toBe(true)
      expect(footer.text()).toContain('Total')
      expect(footer.text()).toContain('100.0000%')
      expect(footer.text()).toContain('€18,000.00')
    })
  })

  describe('currency formatting', () => {
    it('should format large numbers with thousands separator', () => {
      const largeHoldings: EtfHoldingBreakdownDto[] = [
        {
          holdingTicker: 'AAPL',
          holdingName: 'Apple Inc.',
          percentageOfTotal: 100,
          totalValueEur: 1234567.89,
          holdingSector: 'Technology',
          holdingCountryCode: 'US',
          holdingCountryName: 'United States',
          inEtfs: 'IITU:100%',
          numEtfs: 1,
          platforms: 'LIGHTYEAR',
        },
      ]

      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: largeHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const footer = wrapper.find('.table-footer-totals')
      expect(footer.text()).toContain('€1,234,567.89')
    })

    it('should format null values as dash', () => {
      const nullHoldings: EtfHoldingBreakdownDto[] = [
        {
          holdingTicker: 'AAPL',
          holdingName: 'Apple Inc.',
          percentageOfTotal: 100,
          totalValueEur: 0,
          holdingSector: null,
          holdingCountryCode: null,
          holdingCountryName: null,
          inEtfs: '',
          numEtfs: 0,
          platforms: '',
        },
      ]

      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: nullHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      expect(wrapper.findComponent({ name: 'DataTable' }).exists()).toBe(true)
    })
  })

  describe('percentage formatting', () => {
    it('should format percentage with 4 decimal places', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')
      const percentColumn = columns.find((col: any) => col.key === 'percentageOfTotal')

      expect(percentColumn.formatter(25.5432)).toBe('25.5432%')
      expect(percentColumn.formatter(10.1)).toBe('10.1000%')
    })

    it('should format null percentage as dash', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')
      const percentColumn = columns.find((col: any) => col.key === 'percentageOfTotal')

      expect(percentColumn.formatter(null)).toBe('-')
    })
  })

  describe('ETF list formatting', () => {
    it('should format ETF list to show only symbols', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')
      const etfColumn = columns.find((col: any) => col.key === 'inEtfs')

      expect(etfColumn.formatter('IITU:50%, VUSA:30%')).toBe('IITU, VUSA')
    })

    it('should handle null ETF list', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')
      const etfColumn = columns.find((col: any) => col.key === 'inEtfs')

      expect(etfColumn.formatter(null)).toBe('-')
    })

    it('should trim whitespace from ETF symbols', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')
      const etfColumn = columns.find((col: any) => col.key === 'inEtfs')

      expect(etfColumn.formatter('  IITU:50%  ,  VUSA:30%  ')).toBe('IITU, VUSA')
    })
  })

  describe('column configuration', () => {
    it('should have all required columns', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')

      expect(columns).toHaveLength(6)
      expect(columns[0].key).toBe('holdingTicker')
      expect(columns[1].key).toBe('holdingName')
      expect(columns[2].key).toBe('percentageOfTotal')
      expect(columns[3].key).toBe('totalValueEur')
      expect(columns[4].key).toBe('holdingSector')
      expect(columns[5].key).toBe('inEtfs')
    })

    it('should have correct sortable configuration', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const dataTable = wrapper.findComponent({ name: 'DataTable' })
      const columns = dataTable.props('columns')

      expect(columns[0].sortable).toBe(true)
      expect(columns[1].sortable).toBe(true)
      expect(columns[2].sortable).toBe(true)
      expect(columns[3].sortable).toBe(true)
      expect(columns[4].sortable).toBe(true)
      expect(columns[5].sortable).toBe(false)
    })
  })

  describe('total calculation', () => {
    it('should calculate correct total from holdings', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: mockHoldings,
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      const footer = wrapper.find('.table-footer-totals')
      expect(footer.text()).toContain('€18,000.00')
    })

    it('should show zero total when no holdings', () => {
      const wrapper = mount(EtfBreakdownTable, {
        props: {
          holdings: [],
          isLoading: false,
          isError: false,
          errorMessage: '',
        },
      })

      expect(wrapper.find('.table-footer-totals').exists()).toBe(false)
    })
  })
})
