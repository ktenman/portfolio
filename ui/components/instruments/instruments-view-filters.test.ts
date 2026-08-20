import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { flushPromises, VueWrapper } from '@vue/test-utils'
import type { Platform } from '../../models/generated/domain-models'
import { ProviderName } from '../../models/generated/domain-models'
import { createInstrumentDto } from '../../tests/fixtures'
import {
  mockInstruments,
  mountAndSettle,
  setupInstrumentsViewMocks,
  stubInstruments,
} from './instruments-view-fixture'

vi.mock('../../services/api')
vi.mock('../../composables/use-auth-state', () => ({
  useAuthState: () => ({
    isAuthenticated: ref(true),
    isAuthChecking: ref(false),
    checkAuth: vi.fn().mockResolvedValue(true),
  }),
}))
vi.mock('../../composables/use-toast', () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
  }),
}))
vi.mock('@vueuse/core', () => ({
  useLocalStorage: vi.fn((_key: string, defaultValue: any) => ref(defaultValue)),
  onClickOutside: vi.fn(),
}))

const platformLabels = (wrapper: VueWrapper) =>
  wrapper.findAll('.platform-btn:not(.platform-btn-ghost)').map(btn => btn.text())

describe('InstrumentsView', () => {
  setupInstrumentsViewMocks()

  describe('platform filtering', () => {
    it('should display available platforms from instruments', async () => {
      const { wrapper } = await mountAndSettle()

      const platformTexts = platformLabels(wrapper)

      expect(platformTexts).toContain('Trading 212')
      expect(platformTexts).toContain('Binance')
      expect(platformTexts).toContain('Coinbase')
    })

    it('should show the filters toggle with the platform selection count', async () => {
      const { wrapper } = await mountAndSettle()

      expect(wrapper.find('.dropdown-toggle').text()).toBe('Filters (3/3)')
    })

    it('should flip the toggle button label after clearing every platform', async () => {
      const { wrapper } = await mountAndSettle()
      const toggleButton = wrapper.find('.platform-btn-ghost')
      expect(toggleButton.text()).toBe('Clear All')

      await toggleButton.trigger('click')
      await flushPromises()

      expect(toggleButton.text()).toBe('Select All')
    })

    it('should filter out platforms without valid instruments', async () => {
      stubInstruments([
        createInstrumentDto({
          id: 10,
          symbol: 'INVALID',
          name: 'Invalid InstrumentDto',
          providerName: ProviderName.FT,
          platforms: ['UNKNOWN' as Platform],
        }),
      ])

      const { wrapper } = await mountAndSettle()

      expect(platformLabels(wrapper)).not.toContain('UNKNOWN_PLATFORM')
    })

    it('should deselect each platform toggled off', async () => {
      const { wrapper } = await mountAndSettle()
      const platformButton = (name: string) =>
        wrapper.findAll('.platform-btn').find(btn => btn.text() === name)!

      await platformButton('Binance').trigger('click')
      await flushPromises()
      await platformButton('Coinbase').trigger('click')
      await flushPromises()

      const stillSelected = wrapper
        .findAll('.platform-buttons .platform-btn.active')
        .map(btn => btn.text())
      expect(stillSelected).toEqual(['Trading 212'])
    })

    it('should render platform separator element', async () => {
      const { wrapper } = await mountAndSettle()

      const separator = wrapper.find('.platform-separator')
      expect(separator.exists()).toBe(true)
    })

    it('should only show platforms with instruments that have investments or quantity', async () => {
      stubInstruments([
        ...mockInstruments,
        createInstrumentDto({
          id: 4,
          symbol: 'EMPTY',
          name: 'Empty InstrumentDto',
          providerName: ProviderName.FT,
          category: 'STOCK',
          platforms: ['LHV'],
          totalInvestment: 0,
          quantity: 0,
        }),
      ])

      const { wrapper } = await mountAndSettle()

      expect(platformLabels(wrapper)).not.toContain('LHV')
    })
  })

  describe('active only toggle', () => {
    it('should render active only toggle', async () => {
      const { wrapper } = await mountAndSettle()

      expect(wrapper.find('.toggle-container .toggle-label').text()).toBe('Active only')
    })

    it('should default to showing active only when toggle is checked', async () => {
      const { wrapper } = await mountAndSettle()

      const toggleInput = wrapper.find('.toggle-switch input')
      expect((toggleInput.element as HTMLInputElement).checked).toBe(true)
    })

    it('should filter instruments based on toggle state', async () => {
      stubInstruments([
        createInstrumentDto({
          id: 1,
          symbol: 'ACTIVE',
          name: 'Active Stock',
          providerName: ProviderName.FT,
          currentValue: 1000,
          profit: 100,
          platforms: ['TRADING212'],
        }),
        createInstrumentDto({
          id: 2,
          symbol: 'INACTIVE_WITH_PROFIT',
          name: 'Inactive With Profit',
          providerName: ProviderName.FT,
          currentValue: 0,
          profit: -50,
          platforms: ['TRADING212'],
        }),
        createInstrumentDto({
          id: 3,
          symbol: 'INACTIVE_NO_PROFIT',
          name: 'Inactive No Profit',
          providerName: ProviderName.FT,
          currentValue: 0,
          profit: 0,
          platforms: ['TRADING212'],
        }),
      ])

      const { wrapper } = await mountAndSettle()

      const tableSymbols = (): string[] => {
        const rows = JSON.parse(wrapper.find('#stub-table').attributes('data-instruments') || '[]')
        return rows.map((row: { symbol: string }) => row.symbol)
      }

      expect(tableSymbols()).toEqual(['ACTIVE'])

      await wrapper.find('.toggle-switch input').setValue(false)
      await flushPromises()

      expect(tableSymbols()).toEqual(['ACTIVE', 'INACTIVE_WITH_PROFIT'])
    })
  })
})
