import { describe, it, expect } from 'vitest'
import { allocationPair, createWrapper, fullyInvested } from './allocation-table-fixture'

const rebalanceMode = {
  selectedPlatforms: ['LHV'],
  availablePlatforms: ['LHV'],
  currentHoldingsTotal: 5000,
}

const heldAllocations = [{ instrumentId: 1, value: 100, currentValue: 5000 }]

const rebalanceWithInvestment = {
  ...rebalanceMode,
  totalInvestment: 1000,
  allocations: heldAllocations,
}

const partiallyInvested = {
  totalInvestment: 10000,
  allocations: [{ instrumentId: 1, value: 30 }],
}

const twoPlatforms = { availablePlatforms: ['LHV', 'LIGHTYEAR'] }

const platformPills = (wrapper: ReturnType<typeof createWrapper>) =>
  wrapper.findAll('.platform-btn:not(.platform-btn-ghost)')

const hasHeader = (wrapper: ReturnType<typeof createWrapper>, label: string) =>
  wrapper.findAll('th').some(h => h.text().includes(label))

describe('AllocationTable', () => {
  describe('investment calculations', () => {
    it.each([
      { label: 'set', totalInvestment: 10000, visible: true },
      { label: '0', totalInvestment: 0, visible: false },
    ])(
      'should show Units and Unused columns only when totalInvestment is $label',
      ({ totalInvestment, visible }) => {
        const wrapper = createWrapper({ ...fullyInvested, totalInvestment })
        expect(hasHeader(wrapper, 'Units')).toBe(visible)
        expect(hasHeader(wrapper, 'Unused')).toBe(visible)
      }
    )

    it('should calculate units correctly', () => {
      const wrapper = createWrapper(partiallyInvested)
      expect(wrapper.text()).toContain('24')
    })

    it('should calculate unused amount correctly', () => {
      const wrapper = createWrapper(partiallyInvested)
      expect(wrapper.text()).toContain('€108.00')
    })

    it('should show total unused in footer', () => {
      const wrapper = createWrapper({ totalInvestment: 10000, allocations: allocationPair })
      expect(wrapper.text()).toContain('Total Unused')
    })

    it('should show optimize toggle when totalInvestment is set', () => {
      const wrapper = createWrapper(fullyInvested)
      expect(wrapper.find('.optimize-toggle').exists()).toBe(true)
      expect(wrapper.text()).toContain('Optimize')
    })

    it('should not show optimize toggle when totalInvestment is 0', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.optimize-toggle').exists()).toBe(false)
    })

    it('should show total to invest input in percentage mode', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.total-investment-input').exists()).toBe(true)
      expect(wrapper.text()).toContain('Total to invest')
    })

    it('should emit update:totalInvestment when investment amount changes', async () => {
      const wrapper = createWrapper()
      const input = wrapper.find('.total-investment-input input')
      await input.setValue('5000')
      expect(wrapper.emitted('update:totalInvestment')).toEqual([[5000]])
    })

    it('should show dash for units when no ETF selected', () => {
      const wrapper = createWrapper({
        ...fullyInvested,
        allocations: [{ instrumentId: 0, value: 100 }],
      })
      const rows = wrapper.findAll('tbody tr')
      expect(rows[0].text()).toContain('-')
    })
  })

  describe('platform rebalancing', () => {
    it('should show platform buttons when platforms are available', () => {
      const wrapper = createWrapper(twoPlatforms)
      expect(wrapper.find('.platform-buttons').exists()).toBe(true)
    })

    it('should not show platform buttons when no platforms available', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.platform-buttons').exists()).toBe(false)
    })

    it('should format platform names correctly', () => {
      const wrapper = createWrapper(twoPlatforms)
      const pills = platformPills(wrapper)
      expect(pills).toHaveLength(2)
      expect(pills[0].text()).toBe('LHV')
      expect(pills[1].text()).toBe('Lightyear')
    })

    it('should emit togglePlatform when a platform pill is clicked', async () => {
      const wrapper = createWrapper(twoPlatforms)
      const pills = platformPills(wrapper)
      await pills[0].trigger('click')
      expect(wrapper.emitted('togglePlatform')).toEqual([['LHV']])
    })

    it('should emit toggleAllPlatforms when the Select All / Clear All button is clicked', async () => {
      const wrapper = createWrapper(twoPlatforms)
      const toggleAllBtn = wrapper.find('.platform-btn-ghost')
      await toggleAllBtn.trigger('click')
      expect(wrapper.emitted('toggleAllPlatforms')).toHaveLength(1)
    })

    it('marks selected platforms as active and unselected as inactive', () => {
      const wrapper = createWrapper({
        availablePlatforms: ['LHV', 'SWEDBANK'],
        selectedPlatforms: ['LHV'],
      })
      const pills = platformPills(wrapper)
      const lhv = pills.find(p => p.text() === 'LHV')
      const swedbank = pills.find(p => p.text() === 'Swedbank')
      expect(lhv?.classes()).toContain('active')
      expect(swedbank?.classes()).not.toContain('active')
    })

    it('should show current holdings when platform is selected and has holdings', () => {
      const wrapper = createWrapper(rebalanceMode)
      expect(wrapper.find('.current-holdings').exists()).toBe(true)
      expect(wrapper.text()).toContain('€5,000.00')
    })

    it('should not show current holdings when no platform selected', () => {
      const wrapper = createWrapper({ availablePlatforms: ['LHV'], currentHoldingsTotal: 5000 })
      expect(wrapper.find('.current-holdings').exists()).toBe(false)
    })

    it('should show rebalance columns when platform selected with holdings', () => {
      const wrapper = createWrapper({ ...rebalanceMode, allocations: heldAllocations })
      expect(hasHeader(wrapper, 'Current')).toBe(true)
      expect(hasHeader(wrapper, 'Target %')).toBe(true)
    })

    it('should show Action header instead of Units in rebalance mode', () => {
      const wrapper = createWrapper(rebalanceWithInvestment)
      expect(hasHeader(wrapper, 'Action')).toBe(true)
      expect(hasHeader(wrapper, 'Units')).toBe(false)
    })

    it('should show New investment label instead of Total to invest in rebalance mode', () => {
      const wrapper = createWrapper(rebalanceMode)
      expect(wrapper.text()).toContain('New investment')
    })

    it('should show optimize toggle in rebalance mode when investment is set', () => {
      const wrapper = createWrapper(rebalanceWithInvestment)
      expect(wrapper.find('.optimize-toggle').exists()).toBe(true)
    })

    it('should calculate units to buy correctly in rebalance mode', () => {
      const wrapper = createWrapper({
        ...rebalanceMode,
        currentHoldingsTotal: 3000,
        totalInvestment: 1000,
        allocations: [
          { instrumentId: 1, value: 50, currentValue: 3000 },
          { instrumentId: 2, value: 50, currentValue: 0 },
        ],
      })
      const rows = wrapper.findAll('tbody tr')
      expect(rows[0].text()).toContain('Sell 8')
      expect(rows[1].text()).toContain('Buy 20')
    })
  })
})
