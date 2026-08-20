import { describe, it, expect, vi } from 'vitest'
import { createWrapper, createWrapperWithInstrument } from './instrument-table-fixture'

vi.mock('../shared/data-table.vue', async () => ({
  default: (await import('../../tests/fixtures')).instrumentDataTableStub,
}))

describe('InstrumentTable', () => {
  describe('platform display', () => {
    it('should display platform badges for instruments with platforms', () => {
      const wrapper = createWrapper()
      const firstRow = wrapper.find('tbody tr')

      const badges = firstRow.findAll('.badge')

      expect(badges.map(badge => badge.text())).toEqual(['Trading 212'])
    })

    it('should display multiple platform badges', () => {
      const wrapper = createWrapper()
      const secondRow = wrapper.findAll('tbody tr')[1]

      const badges = secondRow.findAll('.badge')

      expect(badges.map(badge => badge.text())).toEqual(['Binance', 'Coinbase'])
    })

    it('should format platform names correctly', () => {
      const wrapper = createWrapperWithInstrument({
        platforms: ['TRADING212', 'LIGHTYEAR', 'SWEDBANK', 'LHV', 'AVIVA'],
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
      const mobileCards = wrapper.findAll('.mobile-instrument-card')

      const badges = mobileCards[1].findAll('.platform-tags .badge')
      expect(badges.map(badge => badge.text())).toEqual(['Binance', 'Coinbase'])
    })

    it.each([
      { label: 'undefined', platforms: undefined },
      { label: 'an empty array', platforms: [] },
    ])('should render no badges when platforms is $label', ({ platforms }) => {
      const wrapper = createWrapperWithInstrument({ platforms })

      expect(wrapper.find('tbody tr').findAll('.badge')).toHaveLength(0)
    })

    it('should handle unknown platform names', () => {
      const wrapper = createWrapperWithInstrument({
        platforms: ['NEW_PLATFORM', 'ANOTHER_PLATFORM'],
      })

      const row = wrapper.find('tbody tr')
      expect(row.text()).toContain('NEW_PLATFORM')
      expect(row.text()).toContain('ANOTHER_PLATFORM')
    })
  })
})
