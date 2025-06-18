import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import InstrumentsView from './instruments-view.vue'
import { instrumentsService } from '../../services/instruments-service'
import type { Instrument } from '../../models/instrument'
import { ProviderName } from '../../models/provider-name'
import { h } from 'vue'

const mockShow = vi.fn()
const mockHide = vi.fn()
const mockToastSuccess = vi.fn()
const mockToastError = vi.fn()

vi.mock('../../services/instruments-service')
vi.mock('vue-toastification', () => ({
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

const CrudLayoutStub = {
  name: 'CrudLayout',
  emits: ['add'],
  setup(props: any, { emit, slots }: any) {
    const handleAdd = () => emit('add')
    return () => h('div', [
      h('button', { onClick: handleAdd, id: 'stub-add-button' }, 'Add'),
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
    return () => h('div', { id: 'stub-table' }, [
      h('button', { onClick: () => handleEdit(props.instruments?.[0]), id: 'stub-edit-button' }, 'Edit'),
    ])
  },
}

const InstrumentModalStub = {
  name: 'InstrumentModal',
  props: ['instrument'],
  emits: ['save'],
  setup(props: any, { emit }: any) {
    const handleSave = (data: any) => emit('save', data)
    return () => h('div', { 
      id: 'stub-modal',
      'data-instrument': JSON.stringify(props.instrument || {})
    }, [
      h('button', { 
        onClick: () => handleSave({ symbol: 'TEST', name: 'Test' }), 
        id: 'stub-save-button' 
      }, 'Save'),
    ])
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
      const createdInstrument = { id: 3, ...newInstrumentData, providerName: ProviderName.ALPHA_VANTAGE }
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
      expect(mockToastSuccess).toHaveBeenCalledWith('Instrument created successfully')
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
      const updatedInstrument = { ...instrumentToEdit, ...updateData }
      vi.mocked(instrumentsService.update).mockResolvedValue(updatedInstrument)

      await flushPromises()

      await wrapper.find('#stub-edit-button').trigger('click')
      await flushPromises()

      await wrapper.find('#stub-save-button').trigger('click')
      await flushPromises()

      expect(instrumentsService.update).toHaveBeenCalledWith(instrumentToEdit.id, updateData)
      expect(instrumentsService.create).not.toHaveBeenCalled()
      expect(queryClient.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['instruments'] })
      expect(mockToastSuccess).toHaveBeenCalledWith('Instrument updated successfully')
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
      vi.mocked(instrumentsService.create).mockResolvedValue({ 
        id: 4, 
        symbol: 'NEW', 
        name: 'New', 
        providerName: ProviderName.ALPHA_VANTAGE 
      })

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
})