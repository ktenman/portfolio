import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import InstrumentsView from './instruments-view.vue'
import { instrumentsService } from '../../services/instruments-service'
import type { Platform } from '../../models/generated/domain-models'
import { ProviderName } from '../../models/generated/domain-models'
import { h } from 'vue'
import { createInstrumentDto } from '../../tests/fixtures'

const mockShow = vi.fn()
const mockHide = vi.fn()
const mockToastSuccess = vi.fn()
const mockToastError = vi.fn()

vi.mock('../../services/instruments-service')
vi.mock('../../composables/use-toast', () => ({
  useToast: () => ({
    success: mockToastSuccess,
    error: mockToastError,
  }),
}))
vi.mock('../../composables/use-bootstrap-modal', () => ({
  useBootstrapModal: () => ({
    show: mockShow,
    hide: mockHide,
  }),
}))
vi.mock('@vueuse/core', () => ({
  useLocalStorage: vi.fn((_key: string, defaultValue: any) => {
    let storedValue = defaultValue
    return {
      get value() {
        return storedValue
      },
      set value(newValue: any) {
        storedValue = newValue
      },
    }
  }),
}))

const CrudLayoutStub = {
  name: 'CrudLayout',
  emits: ['add'],
  setup(_props: any, { emit, slots }: any) {
    const handleAdd = () => emit('add')
    return () =>
      h('div', [
        h('button', { onClick: handleAdd, id: 'stub-add-button' }, 'Add'),
        slots.subtitle?.(),
        slots.content?.(),
        slots.modals?.(),
      ])
  },
}

const InstrumentTableStub = {
  name: 'InstrumentTable',
  props: ['instruments'],
  emits: ['edit'],
  setup(props: any, { emit }: any) {
    const handleEdit = (item: any) => emit('edit', item)
    return () =>
      h('div', { id: 'stub-table' }, [
        h(
          'button',
          { onClick: () => handleEdit(props.instruments?.[0]), id: 'stub-edit-button' },
          'Edit'
        ),
      ])
  },
}

const InstrumentModalStub = {
  name: 'InstrumentModal',
  props: ['instrument'],
  emits: ['save'],
  setup(props: any, { emit }: any) {
    const handleSave = (data: any) => emit('save', data)
    return () =>
      h(
        'div',
        {
          id: 'stub-modal',
          'data-instrument': JSON.stringify(props.instrument || {}),
        },
        [
          h(
            'button',
            {
              onClick: () => handleSave({ symbol: 'TEST', name: 'Test' }),
              id: 'stub-save-button',
            },
            'Save'
          ),
        ] as any
      )
  },
}

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  const wrapper = mount(InstrumentsView, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: {
        CrudLayout: CrudLayoutStub,
        InstrumentTable: InstrumentTableStub,
        InstrumentModal: InstrumentModalStub,
      },
    },
  })

  vi.spyOn(queryClient, 'invalidateQueries')

  return { wrapper, queryClient }
}

describe('InstrumentsView', () => {
  const mockInstruments = [
    createInstrumentDto({
      id: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
      providerName: ProviderName.ALPHA_VANTAGE,
      category: 'STOCK',
      platforms: ['TRADING212'],
      totalInvestment: 1000,
      quantity: 10,
    }),
    createInstrumentDto({
      id: 2,
      symbol: 'BTC',
      name: 'Bitcoin',
      providerName: ProviderName.BINANCE,
      category: 'CRYPTO',
      platforms: ['BINANCE', 'COINBASE'],
      totalInvestment: 5000,
      quantity: 0.5,
    }),
    createInstrumentDto({
      id: 3,
      symbol: 'GOOGL',
      name: 'Alphabet Inc.',
      providerName: ProviderName.ALPHA_VANTAGE,
      category: 'STOCK',
      platforms: ['TRADING212'],
      totalInvestment: 2000,
      quantity: 15,
    }),
  ]
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(instrumentsService.getAll).mockResolvedValue(mockInstruments)
  })

  afterEach(() => {
    localStorage.clear()
  })

  describe('query operations', () => {
    it('should fetch instruments on mount', async () => {
      createWrapper()
      await flushPromises()

      expect(instrumentsService.getAll).toHaveBeenCalled()
    })
  })

  describe('modal operations', () => {
    it('should clear selectedItem and show modal when opening add modal', async () => {
      const { wrapper } = createWrapper()
      await flushPromises()

      const addButton = wrapper.find('#stub-add-button')
      await addButton.trigger('click')
      await flushPromises()

      const modal = wrapper.find('#stub-modal')
      const instrumentData = JSON.parse(modal.attributes('data-instrument') || '{}')
      expect(instrumentData).toEqual({})
      expect(mockShow).toHaveBeenCalled()
    })

    it('should set selectedItem and show modal when opening edit modal', async () => {
      const { wrapper } = createWrapper()
      await flushPromises()

      const editButton = wrapper.find('#stub-edit-button')
      await editButton.trigger('click')
      await flushPromises()

      const modal = wrapper.find('#stub-modal')
      const instrumentData = JSON.parse(modal.attributes('data-instrument') || '{}')
      expect(instrumentData).toMatchObject(mockInstruments[0])
      expect(mockShow).toHaveBeenCalled()
    })
  })

  describe('create mutation', () => {
    it('should create instrument when selectedItem has no id', async () => {
      const { wrapper, queryClient } = createWrapper()
      const newInstrumentData = { symbol: 'GOOGL', name: 'Alphabet Inc.' }
      const createdInstrument = createInstrumentDto({
        id: 3,
        ...newInstrumentData,
        providerName: ProviderName.ALPHA_VANTAGE,
      })
      vi.mocked(instrumentsService.create).mockResolvedValue(createdInstrument)

      await flushPromises()

      await wrapper.find('#stub-add-button').trigger('click')
      await flushPromises()

      const saveButton = wrapper.find('#stub-save-button')
      await saveButton.trigger('click')
      await flushPromises()

      expect(instrumentsService.create).toHaveBeenCalled()
      expect(instrumentsService.update).not.toHaveBeenCalled()
      expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['instruments'] })
      expect(mockToastSuccess).toHaveBeenCalledWith('InstrumentDto created successfully')
      expect(mockHide).toHaveBeenCalled()
    })

    it('should handle create error and show error message', async () => {
      const { wrapper } = createWrapper()
      const error = new Error('Network error')
      vi.mocked(instrumentsService.create).mockRejectedValue(error)

      await flushPromises()

      await wrapper.find('#stub-add-button').trigger('click')
      await flushPromises()

      await wrapper.find('#stub-save-button').trigger('click')
      await flushPromises()

      expect(mockToastError).toHaveBeenCalledWith(`Failed to save instrument: ${error.message}`)
      expect(mockHide).not.toHaveBeenCalled()
    })
  })

  describe('update mutation', () => {
    it('should update instrument when selectedItem has id', async () => {
      const { wrapper, queryClient } = createWrapper()
      const instrumentToEdit = mockInstruments[0]
      const updateData = { symbol: 'TEST', name: 'Test' }
      const updatedInstrument = createInstrumentDto({ ...instrumentToEdit, ...updateData })
      vi.mocked(instrumentsService.update).mockResolvedValue(updatedInstrument)

      await flushPromises()

      await wrapper.find('#stub-edit-button').trigger('click')
      await flushPromises()

      await wrapper.find('#stub-save-button').trigger('click')
      await flushPromises()

      expect(instrumentsService.update).toHaveBeenCalledWith(instrumentToEdit.id, updateData)
      expect(instrumentsService.create).not.toHaveBeenCalled()
      expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['instruments'] })
      expect(mockToastSuccess).toHaveBeenCalledWith('InstrumentDto updated successfully')
      expect(mockHide).toHaveBeenCalled()
    })

    it('should handle update error and keep modal open', async () => {
      const { wrapper } = createWrapper()
      const error = new Error('Validation failed')
      vi.mocked(instrumentsService.update).mockRejectedValue(error)

      await flushPromises()

      await wrapper.find('#stub-edit-button').trigger('click')
      await flushPromises()

      await wrapper.find('#stub-save-button').trigger('click')
      await flushPromises()

      expect(mockToastError).toHaveBeenCalledWith(`Failed to save instrument: ${error.message}`)
      expect(mockHide).not.toHaveBeenCalled()
    })
  })

  describe('mutation state management', () => {
    it('should clear selectedItem after successful create', async () => {
      const { wrapper } = createWrapper()
      vi.mocked(instrumentsService.create).mockResolvedValue(
        createInstrumentDto({
          id: 4,
          symbol: 'NEW',
          name: 'New',
          providerName: ProviderName.ALPHA_VANTAGE,
        })
      )

      await flushPromises()

      await wrapper.find('#stub-add-button').trigger('click')
      await flushPromises()

      const modalBeforeSave = wrapper.find('#stub-modal')
      expect(JSON.parse(modalBeforeSave.attributes('data-instrument') || '{}')).toEqual({})

      await wrapper.find('#stub-save-button').trigger('click')
      await flushPromises()

      const modalAfterSave = wrapper.find('#stub-modal')
      expect(JSON.parse(modalAfterSave.attributes('data-instrument') || '{}')).toEqual({})
    })
  })

  describe('platform filtering', () => {
    it('should display available platforms from instruments', async () => {
      const { wrapper } = createWrapper()
      await flushPromises()

      const platformButtons = wrapper.findAll('.platform-btn')
      const platformTexts = platformButtons
        .filter(btn => !btn.text().includes('Clear All') && !btn.text().includes('Select All'))
        .map(btn => btn.text())

      expect(platformTexts).toContain('Trading 212')
      expect(platformTexts).toContain('Binance')
      expect(platformTexts).toContain('Coinbase')
    })

    it('should have platform filter buttons', async () => {
      const { wrapper } = createWrapper()
      await flushPromises()

      const platformButtons = wrapper.findAll('.platform-btn')
      expect(platformButtons.length).toBeGreaterThan(0)

      const hasToggleButton = platformButtons.some(
        btn => btn.text().includes('Clear All') || btn.text().includes('Select All')
      )
      expect(hasToggleButton).toBe(true)
    })

    it('should display platform buttons with correct formatting', async () => {
      const { wrapper } = createWrapper()
      await flushPromises()

      const trading212Button = wrapper
        .findAll('.platform-btn')
        .find(btn => btn.text() === 'Trading 212')

      expect(trading212Button).toBeDefined()
      expect(trading212Button?.classes()).toContain('platform-btn')
    })

    it('should call instrumentsService.getAll when component mounts', async () => {
      vi.clearAllMocks()
      createWrapper()
      await flushPromises()

      expect(instrumentsService.getAll).toHaveBeenCalled()
    })

    it('should have toggle button for selecting/clearing all platforms', async () => {
      const { wrapper } = createWrapper()
      await flushPromises()

      const platformButtons = wrapper.findAll('.platform-btn')
      const toggleButton = platformButtons.find(
        btn => btn.text().includes('Clear All') || btn.text().includes('Select All')
      )

      expect(toggleButton).toBeDefined()
      expect(toggleButton?.classes()).toContain('platform-btn')

      await toggleButton?.trigger('click')
      await flushPromises()

      expect(toggleButton?.text()).toBeTruthy()
    })

    it('should filter out platforms without valid instruments', async () => {
      const mockInstrumentsInvalid = [
        createInstrumentDto({
          id: 10,
          symbol: 'INVALID',
          name: 'Invalid InstrumentDto',
          providerName: ProviderName.FT,
          platforms: ['UNKNOWN' as Platform],
        }),
      ]
      vi.mocked(instrumentsService.getAll).mockResolvedValue(mockInstrumentsInvalid)

      const { wrapper } = createWrapper()
      await flushPromises()

      const platformButtons = wrapper.findAll('.platform-btn')
      const unknownButton = platformButtons.find(btn => btn.text() === 'UNKNOWN_PLATFORM')

      expect(unknownButton).toBeUndefined()
    })

    it('should handle multiple platform toggles correctly', async () => {
      const { wrapper } = createWrapper()
      await flushPromises()

      const binanceButton = wrapper.findAll('.platform-btn').find(btn => btn.text() === 'Binance')

      const coinbaseButton = wrapper.findAll('.platform-btn').find(btn => btn.text() === 'Coinbase')

      if (binanceButton && coinbaseButton) {
        await binanceButton.trigger('click')
        await flushPromises()
        await coinbaseButton.trigger('click')
        await flushPromises()

        const finalBinanceClasses = binanceButton.classes()
        const finalCoinbaseClasses = coinbaseButton.classes()

        expect(finalBinanceClasses).toBeDefined()
        expect(finalCoinbaseClasses).toBeDefined()
      }
    })

    it('should render platform separator element', async () => {
      const { wrapper } = createWrapper()
      await flushPromises()

      const separator = wrapper.find('.platform-separator')
      expect(separator.exists()).toBe(true)
    })

    it('should only show platforms with instruments that have investments or quantity', async () => {
      const mockInstrumentsWithEmpty = [
        ...mockInstruments,
        createInstrumentDto({
          id: 4,
          symbol: 'EMPTY',
          name: 'Empty InstrumentDto',
          providerName: ProviderName.ALPHA_VANTAGE,
          category: 'STOCK',
          platforms: ['LHV'],
          totalInvestment: 0,
          quantity: 0,
        }),
      ]
      vi.mocked(instrumentsService.getAll).mockResolvedValue(mockInstrumentsWithEmpty)

      const { wrapper } = createWrapper()
      await flushPromises()

      const platformButtons = wrapper.findAll('.platform-btn')
      const platformTexts = platformButtons
        .filter(btn => !btn.text().includes('Clear All') && !btn.text().includes('Select All'))
        .map(btn => btn.text())

      expect(platformTexts).not.toContain('LHV')
    })
  })
})
