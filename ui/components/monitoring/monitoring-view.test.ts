import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import MonitoringView from './monitoring-view.vue'
import { monitoringService } from '../../services/api'
import { CollectionStatus, type CollectionStatusDto } from '../../models/generated/domain-models'

enableAutoUnmount(afterEach)

vi.mock('../../services/api', () => ({
  monitoringService: { streamUrl: '/api/monitoring/collections/stream', rerun: vi.fn() },
}))

class FakeEventSource {
  static instances: FakeEventSource[] = []
  readyState = 0
  onerror: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null

  constructor() {
    FakeEventSource.instances.push(this)
  }

  close() {}
}

vi.stubGlobal('EventSource', FakeEventSource)

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

const stream = () => FakeEventSource.instances[FakeEventSource.instances.length - 1]

const push = async (items: CollectionStatusDto[]) => {
  stream().onmessage?.({ data: JSON.stringify(items) } as MessageEvent)
  await flushPromises()
}

const drop = async () => {
  stream().onerror?.(new Event('error'))
  await flushPromises()
}

const refuse = async () => {
  stream().readyState = 2
  await drop()
}

const render = async (items: CollectionStatusDto[]) => {
  const wrapper = mount(MonitoringView)
  await push(items)
  return wrapper
}

describe('monitoring-view', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    vi.clearAllMocks()
    FakeEventSource.instances = []
    vi.mocked(monitoringService.rerun).mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

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
    const wrapper = await render([collection({ key: 'VANGUARD_HOLDINGS', provider: 'vanguard' })])
    await wrapper.find('[data-testid="monitoring-rerun"]').trigger('click')
    expect(monitoringService.rerun).toHaveBeenCalledWith('VANGUARD_HOLDINGS')
  })

  it('shows the row as running while the server reports the run in progress', async () => {
    const wrapper = await render([collection({ status: CollectionStatus.RUNNING })])
    expect(wrapper.find('[data-testid="monitoring-rerun"]').attributes('aria-label')).toBe(
      'Lightyear prices running'
    )
  })

  it('offers run now again once the server reports the run finished', async () => {
    const wrapper = await render([collection({ status: CollectionStatus.RUNNING })])
    await push([collection()])
    expect(wrapper.find('[data-testid="monitoring-rerun"]').attributes('aria-label')).toBe(
      'Run Lightyear prices now'
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
    const wrapper = await render([collection()])
    await wrapper.find('tbody [data-testid="monitoring-rerun"]').trigger('click')
    expect(wrapper.find('tbody [data-testid="monitoring-items"]').exists()).toBe(false)
  })

  it('starts every collection that is not running, disabled or breaker open when run all is clicked', async () => {
    const wrapper = await render([
      collection({ key: 'LIGHTYEAR_PRICES' }),
      collection({ key: 'FT_HISTORY', status: CollectionStatus.OVERDUE }),
      collection({ key: 'BINANCE_PRICES', status: CollectionStatus.RUNNING }),
      collection({ key: 'VANGUARD_HOLDINGS', status: CollectionStatus.DISABLED }),
      collection({ key: 'TRADING212_PRICES', status: CollectionStatus.BREAKER_OPEN }),
    ])
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
    const wrapper = await render([collection({ key: 'VANGUARD_HOLDINGS', provider: 'vanguard' })])
    await wrapper.find('[data-testid="monitoring-title"]').trigger('click')
    expect(monitoringService.rerun).toHaveBeenCalledWith('VANGUARD_HOLDINGS')
  })

  it('hints that tapping the title runs all collections', async () => {
    const wrapper = await render([collection()])
    expect(wrapper.find('[data-testid="monitoring-title"]').attributes('title')).toBe('Run all')
  })

  it('names the job without its Job suffix when a row is expanded', async () => {
    const wrapper = await render([collection()])
    await wrapper.find('tbody tr').trigger('click')
    expect(wrapper.find('[data-testid="monitoring-job"]').text()).toBe('LightyearPriceRetrieval')
  })

  it('reports that the status cannot be loaded when the stream fails before sending any', async () => {
    const wrapper = mount(MonitoringView)
    await drop()
    expect(wrapper.text()).toContain(
      'Could not load collection status. It reconnects automatically.'
    )
  })

  it('keeps showing the last status while the stream reconnects', async () => {
    const wrapper = await render([collection()])
    await drop()
    expect(wrapper.find('[data-testid="monitoring-verdict"]').text()).toBe(
      'All 1 collections healthy'
    )
  })

  it('keeps reconnecting every 45 seconds while the server stays silent', async () => {
    await render([collection()])
    vi.advanceTimersByTime(90_000)
    expect(FakeEventSource.instances).toHaveLength(3)
  })

  it('does not open a second stream while the failed one is reconnecting', async () => {
    await render([collection()])
    await drop()
    vi.advanceTimersByTime(45_000)
    expect(FakeEventSource.instances).toHaveLength(1)
  })

  it('keeps the stream open while the server keeps sending', async () => {
    await render([collection()])
    vi.advanceTimersByTime(30_000)
    await push([collection()])
    vi.advanceTimersByTime(30_000)
    expect(FakeEventSource.instances).toHaveLength(1)
  })

  it('waits 15 seconds before retrying a stream the server refused', async () => {
    mount(MonitoringView)
    await refuse()
    vi.advanceTimersByTime(14_999)
    expect(FakeEventSource.instances).toHaveLength(1)
  })

  it('retries a stream the server refused after 15 seconds', async () => {
    mount(MonitoringView)
    await refuse()
    vi.advanceTimersByTime(15_000)
    expect(FakeEventSource.instances).toHaveLength(2)
  })

  it('drops the stale status once the server has been silent for 45 seconds', async () => {
    const wrapper = await render([collection()])
    vi.advanceTimersByTime(45_000)
    await flushPromises()
    expect(wrapper.find('[data-testid="monitoring-verdict"]').exists()).toBe(false)
  })
})
