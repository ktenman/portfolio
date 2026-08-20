import { vi, beforeEach } from 'vitest'
import { h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import InstrumentsView from './instruments-view.vue'
import type { InstrumentsResponse } from '../../models/generated/domain-models'
import { ProviderName } from '../../models/generated/domain-models'
import { instrumentsService, portfolioSummaryService } from '../../services/api'
import { createInstrumentDto, mockPlatforms } from '../../tests/fixtures'
import { setPlatformDisplayNames } from '../../utils/platform-utils'

setPlatformDisplayNames(mockPlatforms)

const CrudLayoutStub = {
  name: 'CrudLayout',
  props: { showAddButton: { type: Boolean, default: true } },
  emits: ['add'],
  setup(props: any, { emit, slots }: any) {
    return () =>
      h('div', [
        props.showAddButton
          ? h('button', { onClick: () => emit('add'), id: 'stub-add-button' }, 'Add')
          : null,
        slots['title-suffix']?.(),
        slots.toolbar?.(),
        slots.subtitle?.(),
        slots['subtitle-end']?.(),
        slots.content?.(),
        slots.modals?.(),
      ])
  },
}

const InstrumentTableStub = {
  name: 'InstrumentTable',
  props: ['instruments'],
  setup(props: any) {
    return () =>
      h('div', {
        id: 'stub-table',
        'data-instruments': JSON.stringify(props.instruments || []),
      })
  },
}

const InstrumentModalStub = {
  name: 'InstrumentModal',
  props: ['instrument', 'open'],
  emits: ['save'],
  setup(props: any, { emit }: any) {
    return () =>
      h(
        'div',
        {
          id: 'stub-modal',
          'data-open': String(props.open),
          'data-instrument': JSON.stringify(props.instrument || {}),
        },
        [
          h(
            'button',
            {
              onClick: () => emit('save', { symbol: 'TEST', name: 'Test' }),
              id: 'stub-save-button',
            },
            'Save'
          ),
        ]
      )
  },
}

export const mockInstruments = [
  createInstrumentDto({
    id: 1,
    symbol: 'AAPL',
    name: 'Apple Inc.',
    providerName: ProviderName.FT,
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
    providerName: ProviderName.FT,
    category: 'STOCK',
    platforms: ['TRADING212'],
    totalInvestment: 2000,
    quantity: 15,
  }),
]

const mockResponse: InstrumentsResponse = {
  instruments: mockInstruments,
  portfolioXirr: 0.125,
}

export const stubInstruments = (instruments: InstrumentsResponse['instruments']) =>
  vi.mocked(instrumentsService.getAll).mockResolvedValue({ ...mockResponse, instruments })

export const setupInstrumentsViewMocks = () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(instrumentsService.getAll).mockResolvedValue(mockResponse)
    vi.mocked(portfolioSummaryService.getRangeChange).mockResolvedValue({
      changeAmount: 0,
      changePercent: 0,
    })
  })
}

export const mountAndSettle = async () => {
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
  await flushPromises()

  return { wrapper, queryClient }
}
