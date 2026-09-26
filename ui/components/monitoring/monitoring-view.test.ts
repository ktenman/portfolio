import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import MonitoringView from './monitoring-view.vue'
import { monitoringService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import { CollectionStatus, type CollectionStatusDto } from '../../models/generated/domain-models'

enableAutoUnmount(afterEach)

vi.mock('../../services/api', () => ({
  monitoringService: { getCollections: vi.fn(), rerun: vi.fn() },
}))

const collection = (overrides: Partial<CollectionStatusDto> = {}): CollectionStatusDto => ({
  key: 'LIGHTYEAR_PRICES',
  provider: 'lightyear',
  operation: 'prices',
  job: 'LightyearPriceRetrievalJob',
  status: CollectionStatus.OK,
  expected: 12,
  fetched: 12,
  persisted: 12,
  failed: 0,
  failedItems: [],
  items: [],
  consecutiveEmptyRuns: 0,
  durationSeconds: 1.5,
  lastAttempt: null,
  lastCompletion: null,
  lastFullSuccess: null,
  deadline: null,
  breakerOpen: false,
  ...overrides,
})

const render = async (items: CollectionStatusDto[]) => {
  vi.mocked(monitoringService.getCollections).mockResolvedValue(items)
  const wrapper = renderWithProviders(MonitoringView)
  await flushPromises()
  return wrapper
}

describe('monitoring-view', () => {
  beforeEach(() => vi.clearAllMocks())

  it('reports every collection healthy when all are ok', async () => {
    const wrapper = await render([collection(), collection({ provider: 'ft' })])
    expect(wrapper.find('[data-testid="monitoring-verdict"]').text()).toBe(
      'All 2 collections healthy'
    )
  })

  it('shows a checkmark when every collection is healthy', async () => {
    const wrapper = await render([collection()])
    expect(wrapper.find('[data-testid="monitoring-verdict"] svg').exists()).toBe(true)
  })

  it('counts overdue collections as needing attention', async () => {
    const wrapper = await render([collection(), collection({ status: CollectionStatus.OVERDUE })])
    expect(wrapper.find('[data-testid="monitoring-verdict"]').text()).toBe('1 of 2 need attention')
  })

  it('shows failed items beside the persisted count', async () => {
    const wrapper = await render([collection({ persisted: 10, failed: 2 })])
    expect(wrapper.text()).toContain('10 / 12 · 2 failed')
  })

  it('names the items that failed', async () => {
    const wrapper = await render([collection({ failed: 2, failedItems: ['AAPL', 'NVDA'] })])
    expect(wrapper.find('[data-testid="monitoring-failed-items"]').text()).toBe('AAPL, NVDA')
  })

  it('starts the collection job when run now is clicked', async () => {
    vi.mocked(monitoringService.rerun).mockResolvedValue(undefined)
    const wrapper = await render([collection({ key: 'VANGUARD_HOLDINGS', provider: 'vanguard' })])
    await wrapper.find('[data-testid="monitoring-rerun"]').trigger('click')
    expect(monitoringService.rerun).toHaveBeenCalledWith('VANGUARD_HOLDINGS')
  })

  it('shows the row as running until the job completes', async () => {
    vi.mocked(monitoringService.rerun).mockResolvedValue(undefined)
    const wrapper = await render([collection()])
    await wrapper.find('[data-testid="monitoring-rerun"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="monitoring-rerun"]').attributes('aria-label')).toBe(
      'Lightyear prices running'
    )
  })

  it('lists failing collections before healthy ones', async () => {
    const wrapper = await render([
      collection({ provider: 'binance' }),
      collection({ provider: 'vanguard', status: CollectionStatus.OVERDUE }),
    ])
    expect(wrapper.find('tbody tr').text()).toContain('Vanguard')
  })

  it('sorts by provider when its header is clicked', async () => {
    const wrapper = await render([
      collection({ key: 'B', provider: 'vanguard' }),
      collection({ key: 'A', provider: 'binance' }),
    ])
    await wrapper.find('th').trigger('click')
    expect(wrapper.find('tbody tr').text()).toContain('Binance')
  })

  it('shows the failure reason of each item when a row is expanded', async () => {
    const wrapper = await render([
      collection({
        failed: 1,
        items: [
          { symbol: 'WEBN', lastSuccess: null, failed: true, error: 'Nimetu osalus' },
          { symbol: 'VWCE', lastSuccess: null, failed: false, error: null },
        ],
      }),
    ])
    await wrapper.find('tbody tr').trigger('click')
    expect(wrapper.find('[data-testid="monitoring-item-error"]').text()).toBe('Nimetu osalus')
  })

  it('does not expand the row when run now is clicked', async () => {
    vi.mocked(monitoringService.rerun).mockResolvedValue(undefined)
    const wrapper = await render([collection()])
    await wrapper.find('tbody [data-testid="monitoring-rerun"]').trigger('click')
    expect(wrapper.find('tbody [data-testid="monitoring-items"]').exists()).toBe(false)
  })

  it('starts every collection that is neither running nor disabled when run all is clicked', async () => {
    vi.mocked(monitoringService.rerun).mockResolvedValue(undefined)
    const wrapper = await render([
      collection({ key: 'LIGHTYEAR_PRICES' }),
      collection({ key: 'FT_HISTORY', status: CollectionStatus.OVERDUE }),
      collection({ key: 'BINANCE_PRICES', status: CollectionStatus.RUNNING }),
      collection({ key: 'VANGUARD_HOLDINGS', status: CollectionStatus.DISABLED }),
    ])
    await wrapper.find('[data-testid="monitoring-run-all"]').trigger('click')
    expect(vi.mocked(monitoringService.rerun).mock.calls).toEqual([
      ['LIGHTYEAR_PRICES'],
      ['FT_HISTORY'],
    ])
  })

  it('refreshes the collections as soon as run all has started the jobs', async () => {
    vi.mocked(monitoringService.rerun).mockResolvedValue(undefined)
    const wrapper = await render([collection()])
    await wrapper.find('[data-testid="monitoring-run-all"]').trigger('click')
    await flushPromises()
    expect(monitoringService.getCollections).toHaveBeenCalledTimes(2)
  })

  it('offers run all while no rerun is in flight', async () => {
    const wrapper = await render([collection()])
    const button = wrapper.find('[data-testid="monitoring-run-all"]')
    expect({
      title: button.attributes('title'),
      spinning: button.find('svg').classes('motion-safe:animate-spin'),
    }).toEqual({ title: 'Run all', spinning: false })
  })

  it('counts down the reruns in flight on a spinning run all as each collection completes', async () => {
    vi.mocked(monitoringService.rerun).mockResolvedValue(undefined)
    const wrapper = await render([
      collection({ key: 'LIGHTYEAR_PRICES' }),
      collection({ key: 'FT_HISTORY', provider: 'ft', operation: 'history' }),
    ])
    vi.mocked(monitoringService.getCollections).mockResolvedValue([
      collection({ key: 'LIGHTYEAR_PRICES' }),
      collection({
        key: 'FT_HISTORY',
        provider: 'ft',
        operation: 'history',
        lastCompletion: '2999-01-01T00:00:00Z',
      }),
    ])
    await wrapper.find('[data-testid="monitoring-run-all"]').trigger('click')
    await flushPromises()
    const button = wrapper.find('[data-testid="monitoring-run-all"]')
    expect({
      title: button.attributes('title'),
      spinning: button.find('svg').classes('motion-safe:animate-spin'),
    }).toEqual({ title: 'Running · 1 left', spinning: true })
  })

  it('starts the remaining collections when run all is clicked while one is already rerunning', async () => {
    vi.mocked(monitoringService.rerun).mockResolvedValue(undefined)
    const wrapper = await render([
      collection({ key: 'LIGHTYEAR_PRICES' }),
      collection({ key: 'FT_HISTORY', provider: 'ft', operation: 'history' }),
    ])
    await wrapper.find('tbody [data-testid="monitoring-rerun"]').trigger('click')
    await wrapper.find('[data-testid="monitoring-run-all"]').trigger('click')
    expect(vi.mocked(monitoringService.rerun).mock.calls).toEqual([
      ['LIGHTYEAR_PRICES'],
      ['FT_HISTORY'],
    ])
  })

  it('names every collection that run all could not start', async () => {
    vi.mocked(monitoringService.rerun).mockRejectedValue(new Error('Bad gateway'))
    const wrapper = await render([
      collection({ key: 'LIGHTYEAR_PRICES' }),
      collection({ key: 'FT_HISTORY', provider: 'ft', operation: 'history' }),
    ])
    await wrapper.find('[data-testid="monitoring-run-all"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toBe(
      'Could not start Lightyear prices, FT history. Check the backend log and try again.'
    )
  })

  it('starts the collection jobs when the title is tapped', async () => {
    vi.mocked(monitoringService.rerun).mockResolvedValue(undefined)
    const wrapper = await render([collection({ key: 'VANGUARD_HOLDINGS', provider: 'vanguard' })])
    await wrapper.find('[data-testid="monitoring-title"]').trigger('click')
    expect(monitoringService.rerun).toHaveBeenCalledWith('VANGUARD_HOLDINGS')
  })

  it('names the job without its Job suffix when a row is expanded', async () => {
    const wrapper = await render([collection()])
    await wrapper.find('tbody tr').trigger('click')
    expect(wrapper.find('[data-testid="monitoring-job"]').text()).toBe('LightyearPriceRetrieval')
  })
})
