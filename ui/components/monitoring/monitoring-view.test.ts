import { describe, it, expect, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import MonitoringView from './monitoring-view.vue'
import { monitoringService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import { CollectionStatus, type CollectionStatusDto } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  monitoringService: { getCollections: vi.fn(), rerun: vi.fn() },
}))

const collection = (overrides: Partial<CollectionStatusDto> = {}): CollectionStatusDto => ({
  key: 'LIGHTYEAR_PRICES',
  provider: 'lightyear',
  operation: 'prices',
  status: CollectionStatus.OK,
  expected: 12,
  fetched: 12,
  persisted: 12,
  failed: 0,
  failedItems: [],
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
  it('reports every collection healthy when all are ok', async () => {
    const wrapper = await render([collection(), collection({ provider: 'ft' })])
    expect(wrapper.find('[data-testid="monitoring-verdict"]').text()).toBe(
      'All 2 collections healthy'
    )
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
})
