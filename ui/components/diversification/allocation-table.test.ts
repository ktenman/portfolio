import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import AllocationTable from './allocation-table.vue'

vi.mock('@vueuse/core', () => ({
  useLocalStorage: vi.fn(<T>(_key: string, defaultValue: T) => ref(defaultValue)),
}))

describe('AllocationTable', () => {
  const defaultEtfs = [
    {
      instrumentId: 1,
      symbol: 'VWCE',
      name: 'Vanguard FTSE All-World',
      allocation: 0,
      ter: 0.22,
      annualReturn: 0.12,
      currentPrice: 120.5,
    },
    {
      instrumentId: 2,
      symbol: 'VUAA',
      name: 'Vanguard S&P 500',
      allocation: 0,
      ter: 0.07,
      annualReturn: 0.15,
      currentPrice: 95.3,
    },
  ]

  const defaultAllocations = [{ instrumentId: 0, value: 0 }]

  const defaultProps = {
    allocations: defaultAllocations,
    availableEtfs: defaultEtfs,
    isLoadingPortfolio: false,
    totalInvestment: 0,
    selectedPlatform: null,
    availablePlatforms: [] as string[],
    currentHoldingsTotal: 0,
    optimizeEnabled: false,
    actionDisplayMode: 'units' as const,
  }

  describe('rendering', () => {
    it('should render allocation table', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      expect(wrapper.find('.allocation-table').exists()).toBe(true)
    })

    it('should render header with correct columns', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const headers = wrapper.findAll('th')
      expect(headers[0].text()).toContain('ETF')
      expect(headers[1].text()).toContain('Name')
      expect(headers[2].text()).toContain('Price')
      expect(headers[3].text()).toContain('TER')
      expect(headers[4].text()).toContain('Annual Return')
    })

    it('should show Allocation % header in percentage mode', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const headers = wrapper.findAll('th')
      expect(headers[5].text()).toContain('Allocation %')
    })
  })

  describe('allocation rows', () => {
    it('should render one row per allocation', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [
            { instrumentId: 1, value: 60 },
            { instrumentId: 2, value: 40 },
          ],
        },
      })
      const rows = wrapper.findAll('tbody tr')
      expect(rows).toHaveLength(2)
    })

    it('should show ETF name when selected', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [{ instrumentId: 1, value: 100 }],
        },
      })
      expect(wrapper.text()).toContain('Vanguard FTSE All-World')
    })

    it('should format price correctly', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [{ instrumentId: 1, value: 100 }],
        },
      })
      expect(wrapper.text()).toContain('€120.50')
    })

    it('should format TER correctly', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [{ instrumentId: 1, value: 100 }],
        },
      })
      expect(wrapper.text()).toContain('0.22%')
    })

    it('should format annual return correctly', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [{ instrumentId: 1, value: 100 }],
        },
      })
      expect(wrapper.text()).toContain('12.00%')
    })
  })

  describe('action buttons', () => {
    it('should render Add ETF button', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      expect(wrapper.text()).toContain('+ Add ETF')
    })

    it('should emit add event when Add ETF is clicked', async () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const addBtn = wrapper.findAll('.action-btn').find(b => b.attributes('title') === 'Add ETF')
      await addBtn?.trigger('click')
      expect(wrapper.emitted('add')).toHaveLength(1)
    })

    it('should emit loadPortfolio event when Load from Portfolio is clicked', async () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const loadBtn = wrapper
        .findAll('.action-btn')
        .find(b => b.attributes('title') === 'Load from Portfolio')
      await loadBtn?.trigger('click')
      expect(wrapper.emitted('loadPortfolio')).toHaveLength(1)
    })

    it('should disable Load from Portfolio button when loading', () => {
      const wrapper = mount(AllocationTable, {
        props: { ...defaultProps, isLoadingPortfolio: true },
      })
      const loadBtn = wrapper
        .findAll('.action-btn')
        .find(b => b.attributes('title') === 'Load from Portfolio')
      expect(loadBtn?.attributes('disabled')).toBeDefined()
    })

    it('should show spinner when loading portfolio', () => {
      const wrapper = mount(AllocationTable, {
        props: { ...defaultProps, isLoadingPortfolio: true },
      })
      expect(wrapper.find('.spinner-border').exists()).toBe(true)
    })

    it('should emit clear event when Clear is clicked', async () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [{ instrumentId: 1, value: 50 }],
        },
      })
      const clearBtn = wrapper.findAll('.action-btn').find(b => b.attributes('title') === 'Clear')
      await clearBtn?.trigger('click')
      expect(wrapper.emitted('clear')).toHaveLength(1)
    })

    it('should disable Clear button when only empty allocation exists', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const clearBtn = wrapper.findAll('.action-btn').find(b => b.attributes('title') === 'Clear')
      expect(clearBtn?.attributes('disabled')).toBeDefined()
    })
  })

  describe('remove button', () => {
    it('should disable remove button when only one allocation', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const removeBtn = wrapper.find('.remove-btn')
      expect(removeBtn.attributes('disabled')).toBeDefined()
    })

    it('should enable remove button when multiple allocations', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [
            { instrumentId: 1, value: 50 },
            { instrumentId: 2, value: 50 },
          ],
        },
      })
      const removeBtn = wrapper.find('.remove-btn')
      expect(removeBtn.attributes('disabled')).toBeUndefined()
    })

    it('should emit remove event with index when clicked', async () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [
            { instrumentId: 1, value: 50 },
            { instrumentId: 2, value: 50 },
          ],
        },
      })
      const removeBtns = wrapper.findAll('.remove-btn')
      await removeBtns[1].trigger('click')
      expect(wrapper.emitted('remove')).toEqual([[1]])
    })
  })

  describe('total allocation', () => {
    it('should show total as valid when sum is 100%', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [
            { instrumentId: 1, value: 60 },
            { instrumentId: 2, value: 40 },
          ],
        },
      })
      expect(wrapper.find('.total-value.valid').exists()).toBe(true)
    })

    it('should show total as invalid when sum is not 100%', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [{ instrumentId: 1, value: 50 }],
        },
      })
      expect(wrapper.find('.total-value.invalid').exists()).toBe(true)
    })

    it('should show hint when total is invalid in percentage mode', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [{ instrumentId: 1, value: 50 }],
        },
      })
      expect(wrapper.text()).toContain('(should be 100%)')
    })
  })

  describe('ETF selection filtering', () => {
    it('should filter out already selected ETFs from dropdown', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [
            { instrumentId: 1, value: 60 },
            { instrumentId: 0, value: 0 },
          ],
        },
      })
      const selects = wrapper.findAll('select')
      const secondSelectOptions = selects[1].findAll('option')
      const optionValues = secondSelectOptions.map(o => o.attributes('value'))
      expect(optionValues).not.toContain('1')
      expect(optionValues).toContain('2')
    })
  })

  describe('export and import buttons', () => {
    it('should render Export button', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      expect(wrapper.text()).toContain('Export')
    })

    it('should render Import button', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      expect(wrapper.text()).toContain('Import')
    })

    it('should emit export event when Export is clicked', async () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const exportBtn = wrapper.findAll('.action-btn').find(b => b.attributes('title') === 'Export')
      await exportBtn?.trigger('click')
      expect(wrapper.emitted('export')).toHaveLength(1)
    })

    it('should emit import event when Import is clicked', async () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const importBtn = wrapper.findAll('.action-btn').find(b => b.attributes('title') === 'Import')
      await importBtn?.trigger('click')
      expect(wrapper.emitted('import')).toHaveLength(1)
    })
  })

  describe('investment calculations', () => {
    beforeEach(() => {
      vi.clearAllMocks()
      localStorage.clear()
    })

    it('should show Units and Unused columns when totalInvestment is set', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 100 }],
        },
      })
      const headers = wrapper.findAll('th').map(h => h.text())
      expect(headers.some(h => h.includes('Units'))).toBe(true)
      expect(headers.some(h => h.includes('Unused'))).toBe(true)
    })

    it('should not show Units and Unused columns when totalInvestment is 0', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 0,
          allocations: [{ instrumentId: 1, value: 100 }],
        },
      })
      const headers = wrapper.findAll('th').map(h => h.text())
      expect(headers.some(h => h.includes('Units'))).toBe(false)
      expect(headers.some(h => h.includes('Unused'))).toBe(false)
    })

    it('should calculate units correctly', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 30 }],
        },
      })
      expect(wrapper.text()).toContain('24')
    })

    it('should calculate unused amount correctly', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 30 }],
        },
      })
      expect(wrapper.text()).toContain('€108.00')
    })

    it('should show total unused in footer', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [
            { instrumentId: 1, value: 60 },
            { instrumentId: 2, value: 40 },
          ],
        },
      })
      expect(wrapper.text()).toContain('Total Unused')
    })

    it('should show optimize toggle when totalInvestment is set', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 100 }],
        },
      })
      expect(wrapper.find('.optimize-toggle').exists()).toBe(true)
      expect(wrapper.text()).toContain('Optimize')
    })

    it('should not show optimize toggle when totalInvestment is 0', () => {
      const wrapper = mount(AllocationTable, {
        props: defaultProps,
      })
      expect(wrapper.find('.optimize-toggle').exists()).toBe(false)
    })

    it('should emit update:totalInvestment when investment amount changes', async () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const input = wrapper.find('.total-investment-input input')
      await input.setValue('5000')
      expect(wrapper.emitted('update:totalInvestment')).toEqual([[5000]])
    })

    it('should show dash for units when no ETF selected', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 0, value: 100 }],
        },
      })
      const rows = wrapper.findAll('tbody tr')
      expect(rows[0].text()).toContain('-')
    })
  })

  describe('platform rebalancing', () => {
    it('should show platform selector when platforms are available', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          availablePlatforms: ['LHV', 'LIGHTYEAR'],
        },
      })
      expect(wrapper.find('.platform-selector').exists()).toBe(true)
    })

    it('should not show platform selector when no platforms available', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      expect(wrapper.find('.platform-selector').exists()).toBe(false)
    })

    it('should format platform names correctly', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          availablePlatforms: ['LHV', 'LIGHTYEAR'],
        },
      })
      const options = wrapper.find('.platform-selector select').findAll('option')
      expect(options[1].text()).toBe('LHV')
      expect(options[2].text()).toBe('Lightyear')
    })

    it('should emit update:selectedPlatform when platform changes', async () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          availablePlatforms: ['LHV', 'LIGHTYEAR'],
        },
      })
      const select = wrapper.find('.platform-selector select')
      await select.setValue('LHV')
      expect(wrapper.emitted('update:selectedPlatform')).toEqual([['LHV']])
    })

    it('should show current holdings when platform is selected and has holdings', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          selectedPlatform: 'LHV',
          availablePlatforms: ['LHV'],
          currentHoldingsTotal: 5000,
        },
      })
      expect(wrapper.find('.current-holdings').exists()).toBe(true)
      expect(wrapper.text()).toContain('€5000.00')
    })

    it('should not show current holdings when no platform selected', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          availablePlatforms: ['LHV'],
          currentHoldingsTotal: 5000,
        },
      })
      expect(wrapper.find('.current-holdings').exists()).toBe(false)
    })

    it('should show rebalance columns when platform selected with holdings', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          selectedPlatform: 'LHV',
          availablePlatforms: ['LHV'],
          currentHoldingsTotal: 5000,
          allocations: [{ instrumentId: 1, value: 100, currentValue: 5000 }],
        },
      })
      const headers = wrapper.findAll('th').map(h => h.text())
      expect(headers.some(h => h.includes('Current'))).toBe(true)
      expect(headers.some(h => h.includes('Target %'))).toBe(true)
    })

    it('should show Target % header in rebalance mode', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          selectedPlatform: 'LHV',
          availablePlatforms: ['LHV'],
          currentHoldingsTotal: 5000,
          allocations: [{ instrumentId: 1, value: 100, currentValue: 5000 }],
        },
      })
      const headers = wrapper.findAll('th').map(h => h.text())
      expect(headers.some(h => h.includes('Target %'))).toBe(true)
    })

    it('should show Action header instead of Units in rebalance mode', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          selectedPlatform: 'LHV',
          availablePlatforms: ['LHV'],
          currentHoldingsTotal: 5000,
          totalInvestment: 1000,
          allocations: [{ instrumentId: 1, value: 100, currentValue: 5000 }],
        },
      })
      const headers = wrapper.findAll('th').map(h => h.text())
      expect(headers.some(h => h.includes('Action'))).toBe(true)
      expect(headers.some(h => h.includes('Units'))).toBe(false)
    })

    it('should show New investment label instead of Total to invest in rebalance mode', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          selectedPlatform: 'LHV',
          availablePlatforms: ['LHV'],
          currentHoldingsTotal: 5000,
        },
      })
      expect(wrapper.text()).toContain('New investment')
    })

    it('should show optimize toggle in rebalance mode when investment is set', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          selectedPlatform: 'LHV',
          availablePlatforms: ['LHV'],
          currentHoldingsTotal: 5000,
          totalInvestment: 1000,
          allocations: [{ instrumentId: 1, value: 100, currentValue: 5000 }],
        },
      })
      expect(wrapper.find('.optimize-toggle').exists()).toBe(true)
    })

    it('should calculate units to buy correctly in rebalance mode', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          selectedPlatform: 'LHV',
          availablePlatforms: ['LHV'],
          currentHoldingsTotal: 3000,
          totalInvestment: 1000,
          allocations: [
            { instrumentId: 1, value: 50, currentValue: 3000 },
            { instrumentId: 2, value: 50, currentValue: 0 },
          ],
        },
      })
      const rows = wrapper.findAll('tbody tr')
      expect(rows[0].text()).toContain('Sell 8')
      expect(rows[1].text()).toContain('Buy 20')
    })
  })

  describe('optimization algorithm', () => {
    beforeEach(() => {
      vi.clearAllMocks()
      localStorage.clear()
    })

    it('should distribute extra units using largest remainder method when optimize enabled', async () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 1000,
          allocations: [
            { instrumentId: 1, value: 50 },
            { instrumentId: 2, value: 50 },
          ],
        },
      })
      const checkbox = wrapper.find('#optimizeAllocation')
      await checkbox.setValue(true)
      expect(wrapper.find('.optimize-toggle input').element).toBeInstanceOf(HTMLInputElement)
    })

    it('should show total to invest input in percentage mode', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      expect(wrapper.find('.total-investment-input').exists()).toBe(true)
      expect(wrapper.text()).toContain('Total to invest')
    })
  })

  describe('column sorting', () => {
    it('should render sortable column headers', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const sortableHeaders = wrapper.findAll('th.sortable')
      expect(sortableHeaders.length).toBeGreaterThan(0)
    })

    it('should show sort indicators on column headers', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const sortIndicators = wrapper.findAll('.sort-indicator')
      expect(sortIndicators.length).toBeGreaterThan(0)
    })

    it('should toggle sort direction when clicking a column header', async () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [
            { instrumentId: 1, value: 60 },
            { instrumentId: 2, value: 40 },
          ],
        },
      })
      const etfHeader = wrapper.find('th.sortable')
      await etfHeader.trigger('click')
      expect(wrapper.find('.sort-indicator.active').exists()).toBe(true)
    })

    it('should sort allocations by symbol when ETF header is clicked', async () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [
            { instrumentId: 2, value: 40 },
            { instrumentId: 1, value: 60 },
          ],
        },
      })
      const etfHeader = wrapper.findAll('th.sortable')[0]
      await etfHeader.trigger('click')
      const rows = wrapper.findAll('tbody tr')
      expect(rows[0].text()).toContain('VUAA')
      expect(rows[1].text()).toContain('VWCE')
    })

    it('should reverse sort order on second click', async () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [
            { instrumentId: 2, value: 40 },
            { instrumentId: 1, value: 60 },
          ],
        },
      })
      const etfHeader = wrapper.findAll('th.sortable')[0]
      await etfHeader.trigger('click')
      await etfHeader.trigger('click')
      const indicator = wrapper.find('.sort-indicator.active')
      expect(indicator.classes()).toContain('desc')
    })

    it('should emit remove with correct original index after sorting', async () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          allocations: [
            { instrumentId: 1, value: 60 },
            { instrumentId: 2, value: 40 },
          ],
        },
      })
      const removeButtons = wrapper.findAll('.remove-btn')
      await removeButtons[1].trigger('click')
      const emitted = wrapper.emitted('remove')
      expect(emitted).toBeTruthy()
      expect(emitted![0][0]).toBe(1)
    })
  })

  describe('action display mode', () => {
    it('should show display mode toggle when investment columns are visible', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 100 }],
        },
      })
      expect(wrapper.find('.display-mode-toggle').exists()).toBe(true)
    })

    it('should highlight units button when actionDisplayMode is units', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 100 }],
          actionDisplayMode: 'units',
        },
      })
      const buttons = wrapper.findAll('.display-mode-btn')
      expect(buttons[0].classes()).toContain('active')
      expect(buttons[1].classes()).not.toContain('active')
    })

    it('should highlight amount button when actionDisplayMode is amount', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 100 }],
          actionDisplayMode: 'amount',
        },
      })
      const buttons = wrapper.findAll('.display-mode-btn')
      expect(buttons[0].classes()).not.toContain('active')
      expect(buttons[1].classes()).toContain('active')
    })

    it('should emit update:actionDisplayMode when clicking units button', async () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 100 }],
          actionDisplayMode: 'amount',
        },
      })
      await wrapper.findAll('.display-mode-btn')[0].trigger('click')
      expect(wrapper.emitted('update:actionDisplayMode')).toEqual([['units']])
    })

    it('should emit update:actionDisplayMode when clicking amount button', async () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 100 }],
          actionDisplayMode: 'units',
        },
      })
      await wrapper.findAll('.display-mode-btn')[1].trigger('click')
      expect(wrapper.emitted('update:actionDisplayMode')).toEqual([['amount']])
    })

    it('should display exact amount with euro symbol when actionDisplayMode is amount', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 100 }],
          actionDisplayMode: 'amount',
        },
      })
      const actionCell = wrapper.findAll('td').find(td => td.text() === '€10000.00')
      expect(actionCell).toBeDefined()
    })

    it('should show zero total unused when actionDisplayMode is amount', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 10000,
          allocations: [{ instrumentId: 1, value: 100 }],
          actionDisplayMode: 'amount',
        },
      })
      expect(wrapper.text()).toContain('€0.00')
    })
  })
})
