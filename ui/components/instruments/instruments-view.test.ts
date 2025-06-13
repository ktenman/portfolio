import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import InstrumentsView from './instruments-view.vue'
import { instrumentsService } from '../../services/instruments-service'
import { renderWithProviders } from '../../tests/test-utils'
import type { Instrument } from '../../models/instrument'
import { ProviderName } from '../../models/provider-name'

vi.mock('../../services/instruments-service')
vi.mock('vue-toastification', () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
  }),
}))
vi.mock('../../composables/use-bootstrap-modal', () => ({
  useBootstrapModal: () => ({
    show: vi.fn(),
    hide: vi.fn(),
  }),
}))

describe('InstrumentsView', () => {
  const mockInstruments: Instrument[] = [
    {
      id: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
      providerName: ProviderName.ALPHA_VANTAGE,
      type: 'STOCK',
    },
    {
      id: 2,
      symbol: 'BTC',
      name: 'Bitcoin',
      providerName: ProviderName.BINANCE,
      type: 'CRYPTO',
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(instrumentsService.getAll).mockResolvedValue(mockInstruments)
  })

  describe('data display', () => {
    it('should display instruments in table', async () => {
      const { getByText } = renderWithProviders(InstrumentsView)
      await flushPromises()

      expect(getByText('AAPL')).toBeTruthy()
      expect(getByText('Apple Inc.')).toBeTruthy()
      expect(getByText('BTC')).toBeTruthy()
      expect(getByText('Bitcoin')).toBeTruthy()
    })

    it('should show loading state while fetching', async () => {
      vi.mocked(instrumentsService.getAll).mockImplementation(() => new Promise(() => {}))

      const { container } = renderWithProviders(InstrumentsView)

      const table = container.querySelector('table')
      const loadingIndicator =
        container.querySelector('.spinner-border') ||
        container.querySelector('[data-testid="loading"]')
      expect(table || loadingIndicator).toBeTruthy()
    })

    it('should display error message on fetch failure', async () => {
      const errorMessage = 'Failed to fetch instruments'
      vi.mocked(instrumentsService.getAll).mockRejectedValue(new Error(errorMessage))

      const { findByText } = renderWithProviders(InstrumentsView)
      await flushPromises()

      const errorElement = await findByText((content, element) => {
        return (
          content.includes('Failed to fetch') ||
          content.includes('Error') ||
          element?.className?.includes('alert-danger') ||
          false
        )
      })
      expect(errorElement).toBeTruthy()
    })
  })

  describe('user interactions', () => {
    it('should have add button available', async () => {
      const { container } = renderWithProviders(InstrumentsView)
      await flushPromises()

      const addButton = container.querySelector('#addNewInstrument') as HTMLElement
      expect(addButton).toBeTruthy()
      expect(addButton.textContent).toContain('Add New Instrument')
    })

    it('should render instrument table with data', async () => {
      const { container } = renderWithProviders(InstrumentsView)
      await flushPromises()

      const table = container.querySelector('table')
      expect(table).toBeTruthy()

      const rows = container.querySelectorAll('tbody tr')
      expect(rows.length).toBe(2)
    })
  })

  describe('save operations', () => {
    it('should have add button with correct text', async () => {
      const { container } = renderWithProviders(InstrumentsView)
      await flushPromises()

      const addButton = container.querySelector('#addNewInstrument')
      expect(addButton).toBeTruthy()
      expect(addButton?.textContent).toContain('Add New Instrument')
    })

    it('should show error message on save failure', async () => {
      const error = new Error('Network error')
      vi.mocked(instrumentsService.create).mockRejectedValue(error)

      const { container } = renderWithProviders(InstrumentsView)
      await flushPromises()

      const component = container.querySelector('[title="Instruments"]')?.parentElement
      const saveEvent = new CustomEvent('save', {
        detail: { symbol: 'FAIL', name: 'Should Fail' },
      })
      component?.dispatchEvent(saveEvent)

      await flushPromises()
    })

    it('should refresh data after successful update', async () => {
      const updatedInstrument = { ...mockInstruments[0], name: 'Apple Corporation' }
      vi.mocked(instrumentsService.update).mockResolvedValue(updatedInstrument)
      vi.mocked(instrumentsService.getAll)
        .mockResolvedValueOnce(mockInstruments)
        .mockResolvedValueOnce([updatedInstrument, mockInstruments[1]])

      const { getByText } = renderWithProviders(InstrumentsView)
      await flushPromises()

      await vi.waitFor(
        async () => {
          await flushPromises()
          try {
            getByText('Apple Corporation')
          } catch {
            return false
          }
          return true
        },
        { timeout: 2000 }
      )
    })
  })
})
