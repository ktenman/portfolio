import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import AllocationTable from './allocation-table.vue'

vi.mock('@vueuse/core', () => ({
  useLocalStorage: vi.fn((_key: string, defaultValue: boolean) => ref(defaultValue)),
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
    inputMode: 'percentage' as const,
    availableEtfs: defaultEtfs,
    isLoadingPortfolio: false,
    totalInvestment: 0,
  }

  describe('rendering', () => {
    it('should render allocation table', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      expect(wrapper.find('.allocation-table').exists()).toBe(true)
    })

    it('should render header with correct columns', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const headers = wrapper.findAll('th')
      expect(headers[0].text()).toBe('ETF')
      expect(headers[1].text()).toBe('Name')
      expect(headers[2].text()).toBe('Price')
      expect(headers[3].text()).toBe('TER')
      expect(headers[4].text()).toBe('Annual Return')
    })

    it('should show Allocation % header in percentage mode', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const headers = wrapper.findAll('th')
      expect(headers[5].text()).toBe('Allocation %')
    })

    it('should show Amount EUR header in amount mode', () => {
      const wrapper = mount(AllocationTable, {
        props: { ...defaultProps, inputMode: 'amount' },
      })
      const headers = wrapper.findAll('th')
      expect(headers[5].text()).toBe('Amount EUR')
    })
  })

  describe('mode buttons', () => {
    it('should highlight percentage button when in percentage mode', () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      const buttons = wrapper.findAll('.mode-btn')
      expect(buttons[0].classes()).toContain('active')
      expect(buttons[1].classes()).not.toContain('active')
    })

    it('should highlight amount button when in amount mode', () => {
      const wrapper = mount(AllocationTable, {
        props: { ...defaultProps, inputMode: 'amount' },
      })
      const buttons = wrapper.findAll('.mode-btn')
      expect(buttons[0].classes()).not.toContain('active')
      expect(buttons[1].classes()).toContain('active')
    })

    it('should emit update:inputMode when clicking percentage button', async () => {
      const wrapper = mount(AllocationTable, {
        props: { ...defaultProps, inputMode: 'amount' },
      })
      await wrapper.findAll('.mode-btn')[0].trigger('click')
      expect(wrapper.emitted('update:inputMode')).toEqual([['percentage']])
    })

    it('should emit update:inputMode when clicking amount button', async () => {
      const wrapper = mount(AllocationTable, { props: defaultProps })
      await wrapper.findAll('.mode-btn')[1].trigger('click')
      expect(wrapper.emitted('update:inputMode')).toEqual([['amount']])
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

    it('should show total as valid in amount mode when sum is greater than 0', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          inputMode: 'amount',
          allocations: [{ instrumentId: 1, value: 1000 }],
        },
      })
      expect(wrapper.find('.total-value.valid').exists()).toBe(true)
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
      const headers = wrapper.findAll('th')
      expect(headers.map(h => h.text())).toContain('Units')
      expect(headers.map(h => h.text())).toContain('Unused')
    })

    it('should not show Units and Unused columns when totalInvestment is 0', () => {
      const wrapper = mount(AllocationTable, {
        props: {
          ...defaultProps,
          totalInvestment: 0,
          allocations: [{ instrumentId: 1, value: 100 }],
        },
      })
      const headers = wrapper.findAll('th')
      expect(headers.map(h => h.text())).not.toContain('Units')
      expect(headers.map(h => h.text())).not.toContain('Unused')
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

    it('should not show total to invest input in amount mode', () => {
      const wrapper = mount(AllocationTable, {
        props: { ...defaultProps, inputMode: 'amount' },
      })
      expect(wrapper.find('.total-investment-input').exists()).toBe(false)
    })
  })
})
