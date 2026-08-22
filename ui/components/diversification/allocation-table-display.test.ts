import { describe, it, expect } from 'vitest'
import { Currency } from '../../models/generated/domain-models'
import { allocationPair, createEtf, createWrapper, fullyInvested } from './allocation-table-fixture'

const rebalanceWithHoldings = {
  selectedPlatforms: ['LHV'],
  currentHoldingsTotal: 1000,
  allocations: [{ instrumentId: 1, value: 100, currentValue: 1000 }],
}

const reversedAllocationPair = [
  { instrumentId: 2, value: 40 },
  { instrumentId: 1, value: 60 },
]

describe('AllocationTable', () => {
  describe('column sorting', () => {
    it('should render sortable column headers', () => {
      const wrapper = createWrapper()
      const sortableHeaders = wrapper.findAll('th.sortable')
      expect(sortableHeaders.length).toBeGreaterThan(0)
    })

    it('should show sort indicators on column headers', () => {
      const wrapper = createWrapper()
      const sortIndicators = wrapper.findAll('.sort-indicator')
      expect(sortIndicators.length).toBeGreaterThan(0)
    })

    it('should toggle sort direction when clicking a column header', async () => {
      const wrapper = createWrapper({ allocations: allocationPair })
      const etfHeader = wrapper.find('th.sortable')
      await etfHeader.trigger('click')
      expect(wrapper.find('.sort-indicator.active').exists()).toBe(true)
    })

    it('should sort allocations by symbol when ETF header is clicked', async () => {
      const wrapper = createWrapper({ allocations: reversedAllocationPair })
      const etfHeader = wrapper.findAll('th.sortable')[0]
      await etfHeader.trigger('click')
      const rows = wrapper.findAll('tbody tr')
      expect(rows[0].text()).toContain('VUAA')
      expect(rows[1].text()).toContain('VWCE')
    })

    it('should reverse sort order on second click', async () => {
      const wrapper = createWrapper({ allocations: reversedAllocationPair })
      const etfHeader = wrapper.findAll('th.sortable')[0]
      await etfHeader.trigger('click')
      await etfHeader.trigger('click')
      const indicator = wrapper.find('.sort-indicator.active')
      expect(indicator.classes()).toContain('desc')
    })
  })

  describe('action display mode', () => {
    it('should show display mode toggle when investment columns are visible', () => {
      const wrapper = createWrapper(fullyInvested)
      expect(wrapper.find('.display-mode-toggle').exists()).toBe(true)
    })

    it.each([
      { mode: 'units', activeButtons: [true, false] },
      { mode: 'amount', activeButtons: [false, true] },
    ])(
      'should highlight only the $mode button when actionDisplayMode is $mode',
      ({ mode, activeButtons }) => {
        const wrapper = createWrapper({ ...fullyInvested, actionDisplayMode: mode })
        const buttons = wrapper.findAll('.display-mode-btn')
        expect(buttons.map(button => button.classes().includes('active'))).toEqual(activeButtons)
      }
    )

    it.each([
      { index: 0, currentMode: 'amount', clickedMode: 'units' },
      { index: 1, currentMode: 'units', clickedMode: 'amount' },
    ])(
      'should emit update:actionDisplayMode when clicking the $clickedMode button',
      async ({ index, currentMode, clickedMode }) => {
        const wrapper = createWrapper({ ...fullyInvested, actionDisplayMode: currentMode })
        await wrapper.findAll('.display-mode-btn')[index].trigger('click')
        expect(wrapper.emitted('update:actionDisplayMode')).toEqual([[clickedMode]])
      }
    )

    it('should display exact amount with euro symbol when actionDisplayMode is amount', () => {
      const wrapper = createWrapper({ ...fullyInvested, actionDisplayMode: 'amount' })
      const actionCell = wrapper.findAll('td').find(td => td.text() === '€10000.00')
      expect(actionCell).toBeDefined()
    })

    it('should show zero total unused when actionDisplayMode is amount', () => {
      const wrapper = createWrapper({ ...fullyInvested, actionDisplayMode: 'amount' })
      expect(wrapper.text()).toContain('€0.00')
    })
  })

  describe('buy only toggle', () => {
    it('renders Buy only toggle when rebalance action column is visible', () => {
      const wrapper = createWrapper(rebalanceWithHoldings)
      expect(wrapper.find('#buyOnlyAllocation').exists()).toBe(true)
    })

    it('does not render Buy only toggle in new-investment mode', () => {
      const wrapper = createWrapper({ selectedPlatforms: [], totalInvestment: 1000 })
      expect(wrapper.find('#buyOnlyAllocation').exists()).toBe(false)
    })

    it('emits update:buyOnlyEnabled when toggle is clicked', async () => {
      const wrapper = createWrapper(rebalanceWithHoldings)
      await wrapper.find('#buyOnlyAllocation').setValue(true)
      expect(wrapper.emitted('update:buyOnlyEnabled')).toEqual([[true]])
    })
  })

  describe('fund currency flag', () => {
    it('shows currency flag in Name column when ETF has fundCurrency', () => {
      const wrapper = createWrapper({
        allocations: [{ instrumentId: 1, value: 100 }],
        availableEtfs: [createEtf({ fundCurrency: Currency.USD })],
      })

      const flag = wrapper.find('tbody tr td:nth-child(2) img')

      expect(flag.attributes('src')).toContain('/us.svg')
    })
  })

  describe('target percentage input', () => {
    it('blurs on wheel so trackpad scrolling cannot change the target', async () => {
      const wrapper = createWrapper(
        { allocations: [{ instrumentId: 1, value: 57.3 }] },
        document.body
      )
      const target = wrapper.find('tbody input[type="number"]')
      ;(target.element as HTMLInputElement).focus()

      await target.trigger('wheel')

      expect(document.activeElement).not.toBe(target.element)
      wrapper.unmount()
    })
  })
})
