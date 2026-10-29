import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { flushPromises, VueWrapper } from '@vue/test-utils'
import { instrumentsService } from '../../services/api'
import { createInstrumentDto } from '../../tests/fixtures'
import {
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
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}))

const viewTab = (wrapper: VueWrapper, label: string) =>
  wrapper.findAll('nav[aria-label="Instrument views"] a').find(link => link.text() === label)!

const showHeatmap = async (wrapper: VueWrapper) => {
  await viewTab(wrapper, 'Heatmap').trigger('click')
  await flushPromises()
}

const heatmapSymbols = (wrapper: VueWrapper) =>
  JSON.parse(wrapper.find('#stub-heatmap').attributes('data-instruments') ?? '[]').map(
    ({ symbol }: { symbol: string }) => symbol
  )

describe('InstrumentsView views', () => {
  setupInstrumentsViewMocks()

  beforeEach(() => localStorage.clear())

  it('shows the table at /instruments', async () => {
    const { wrapper } = await mountAndSettle()

    expect([wrapper.find('#stub-table').exists(), wrapper.find('#stub-heatmap').exists()]).toEqual([
      true,
      false,
    ])
  })

  it('opens the heatmap from its address', async () => {
    const { wrapper } = await mountAndSettle('/instruments/heatmap')

    expect([
      wrapper.find('#stub-heatmap').exists(),
      viewTab(wrapper, 'Heatmap').attributes('aria-current'),
    ]).toEqual([true, 'page'])
  })

  it('puts the heatmap in the address', async () => {
    const { wrapper, router } = await mountAndSettle()

    await showHeatmap(wrapper)

    expect(router.currentRoute.value.fullPath).toBe('/instruments/heatmap')
  })

  it('puts the table back in the address', async () => {
    const { wrapper, router } = await mountAndSettle('/instruments/heatmap')

    await viewTab(wrapper, 'Table').trigger('click')
    await flushPromises()

    expect([router.currentRoute.value.fullPath, wrapper.find('#stub-table').exists()]).toEqual([
      '/instruments',
      true,
    ])
  })

  it('keeps the period and platforms when switching views', async () => {
    const { wrapper } = await mountAndSettle()
    const calls = vi.mocked(instrumentsService.getAll).mock.calls.length

    await showHeatmap(wrapper)

    expect(vi.mocked(instrumentsService.getAll).mock.calls.length).toBe(calls)
  })

  it('hides Active only in the heatmap without changing it', async () => {
    localStorage.setItem('portfolio_show_active_only', 'false')
    const { wrapper } = await mountAndSettle()

    await showHeatmap(wrapper)

    expect([
      wrapper.find('input[role="switch"]').exists(),
      localStorage.getItem('portfolio_show_active_only'),
    ]).toEqual([false, 'false'])
  })

  it('draws only open positions', async () => {
    stubInstruments([
      createInstrumentDto({ id: 1, symbol: 'ÕUN', currentValue: 1200, profit: 200 }),
      createInstrumentDto({ id: 2, symbol: 'SÕLD', currentValue: 0, profit: 340 }),
    ])
    const { wrapper } = await mountAndSettle()

    await showHeatmap(wrapper)

    expect(heatmapSymbols(wrapper)).toEqual(['ÕUN'])
  })
})
