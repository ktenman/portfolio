import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import PortfolioSummary from './portfolio-summary.vue'
import SummaryIntervalSwitch from './portfolio/summary-interval-switch.vue'
import SkeletonLoader from './shared/skeleton-loader.vue'
import { portfolioSummaryService, transactionsService } from '../services/api'
import { toasts } from '../composables/use-toast'
import { STORAGE_KEYS } from '../constants'
import { deferred } from '../tests/test-utils'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../tests/visual/summary-fixture'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'

const scroll = vi.hoisted(() => ({
  load: (): Promise<void> | void => undefined,
  canLoadMore: (): boolean => true,
}))

vi.mock('../services/api')
vi.mock('../composables/use-auth-state', () => ({
  useAuthState: () => ({
    isAuthenticated: ref(true),
    isAuthChecking: ref(false),
    checkAuth: vi.fn().mockResolvedValue(true),
  }),
}))
vi.mock('../composables/use-confirm', () => ({ useConfirm: () => ({ confirm: vi.fn() }) }))
vi.mock('./portfolio/portfolio-chart.vue', () => ({
  __esModule: true,
  default: { template: '<div />' },
}))
vi.mock('@vueuse/core', async importOriginal => ({
  ...(await importOriginal<typeof import('@vueuse/core')>()),
  useInfiniteScroll: (
    _target: unknown,
    load: () => Promise<void>,
    options: { canLoadMore: () => boolean }
  ) => {
    scroll.load = load
    scroll.canLoadMore = options.canLoadMore
  },
}))

enableAutoUnmount(afterEach)

const PLATFORMS = ['LHV', 'TRADING212']
const LOAD_FAILED = 'Could not load the monthly history. Press Monthly to try again.'

const historicalPage = (totalPages: number) => ({
  content: SERIES_RESPONSE,
  totalElements: SERIES_RESPONSE.length,
  totalPages,
  size: 30,
  number: 0,
})

const history = vi.fn<() => Promise<PortfolioSummaryDto[]>>()

const createQueryClient = () =>
  new QueryClient({ defaultOptions: { queries: { retry: false, retryDelay: 0 } } })

const mountPage = (queryClient = createQueryClient()) =>
  mount(PortfolioSummary, {
    attachTo: document.body,
    global: { plugins: [[VueQueryPlugin, { queryClient }]] },
  })

type Wrapper = ReturnType<typeof mountPage>

const settle = async () => {
  for (let tick = 0; tick < 10; tick++) await new Promise(resolve => setTimeout(resolve, 0))
}

const intervalSwitch = (wrapper: Wrapper) => wrapper.findComponent(SummaryIntervalSwitch)

const chip = (wrapper: Wrapper, label: string) =>
  intervalSwitch(wrapper)
    .findAll('button')
    .find(button => button.text() === label)!

const pressed = (wrapper: Wrapper) =>
  intervalSwitch(wrapper)
    .findAll('button[aria-pressed="true"]')
    .map(button => button.text())

const spoken = (wrapper: Wrapper) => intervalSwitch(wrapper).find('[role="status"]').text()

const has = (wrapper: Wrapper, selector: string) => wrapper.find(selector).exists()

const monthCell = (wrapper: Wrapper, title: string) =>
  wrapper.find(`.monthly-profit-calendar .calendar-grid [aria-label^="${title},"]`)

const monthLabel = (wrapper: Wrapper) =>
  wrapper.find('.monthly-profit-calendar .month-label').text()

const platform = (wrapper: Wrapper, name: string) =>
  wrapper.findAll('.platform-buttons button').find(button => button.text() === name)!

const retryButton = (wrapper: Wrapper) =>
  wrapper.find('.monthly-profit-calendar .alert-warning button')

const nextPageSkeleton = (wrapper: Wrapper) =>
  wrapper.findAllComponents(SkeletonLoader).some(loader => loader.props('type') === 'text')

const recordDaily = (wrapper: Wrapper) => {
  const seen = wrapper.findAll('.daily-profit-calendar').map(() => 'daily')
  new MutationObserver(records =>
    records.forEach(record =>
      record.addedNodes.forEach(node => {
        if (!(node instanceof Element)) return
        if (node.matches('.daily-profit-calendar') || node.querySelector('.daily-profit-calendar'))
          seen.push('daily')
      })
    )
  ).observe(wrapper.element, { childList: true, subtree: true })
  return seen
}

const showMonthly = async (wrapper: Wrapper) => {
  await chip(wrapper, 'Monthly').trigger('click')
  await settle()
}

describe('PortfolioSummary interval', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem(STORAGE_KEYS.SELECTED_SUMMARY_PLATFORMS, JSON.stringify(PLATFORMS))
    toasts.value = []
    vi.clearAllMocks()
    vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue(historicalPage(1))
    vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(TODAY_SUMMARY)
    vi.mocked(portfolioSummaryService.getRangeChange).mockResolvedValue({
      changeAmount: 0,
      changePercent: 0,
    })
    vi.mocked(portfolioSummaryService.getBenchmark).mockResolvedValue([])
    vi.mocked(portfolioSummaryService.getIntraday).mockResolvedValue([])
    vi.mocked(transactionsService.getPlatforms).mockResolvedValue(PLATFORMS)
    history.mockReset().mockResolvedValue(SERIES_RESPONSE)
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range =>
      range === 'MAX' ? history() : SERIES_RESPONSE
    )
  })

  it('opens Daily by default', async () => {
    const wrapper = mountPage()

    await settle()

    expect([
      has(wrapper, '.daily-profit-calendar'),
      has(wrapper, '.monthly-profit-calendar'),
      pressed(wrapper),
    ]).toEqual([true, false, ['Daily']])
  })

  it('opens a stored Monthly with placeholders and never renders Daily first', async () => {
    localStorage.setItem(STORAGE_KEYS.SUMMARY_INTERVAL, 'monthly')
    const max = deferred<PortfolioSummaryDto[]>()
    history.mockReturnValue(max.promise)
    const wrapper = mountPage()
    const daily = recordDaily(wrapper)
    await settle()
    const placeholder = has(wrapper, '.monthly-profit-calendar[aria-hidden="true"]')

    max.resolve(SERIES_RESPONSE)
    await settle()

    expect([daily, placeholder, has(wrapper, '.monthly-profit-calendar .calendar-grid')]).toEqual([
      [],
      true,
      true,
    ])
  })

  it('keeps Daily busy behind the delayed veil while Monthly first loads', async () => {
    const max = deferred<PortfolioSummaryDto[]>()
    history.mockReturnValue(max.promise)
    const wrapper = mountPage()
    await settle()
    const monthly = chip(wrapper, 'Monthly')
    monthly.element.focus()

    await showMonthly(wrapper)
    const pending = [
      pressed(wrapper),
      document.activeElement === monthly.element,
      has(wrapper, '.daily-profit-calendar'),
      wrapper.find('[aria-busy="true"] .loading-veil.delayed').text(),
    ]
    max.resolve(SERIES_RESPONSE)
    await settle()

    expect([
      pending,
      has(wrapper, '.monthly-profit-calendar .calendar-grid'),
      wrapper.findAll('tbody tr').length,
      spoken(wrapper),
    ]).toEqual([
      [['Monthly'], true, true, 'Loading months'],
      true,
      7,
      'Monthly profit Jun – Dec 2025',
    ])
  })

  it('takes the veiled Daily out of the keyboard order while Monthly first loads', async () => {
    history.mockReturnValue(new Promise(() => {}))
    const wrapper = mountPage()
    await settle()
    const daily = wrapper.findAll('[inert]').length

    await showMonthly(wrapper)

    expect([daily, wrapper.findAll('[aria-busy="true"] > [inert]').length]).toEqual([0, 2])
  })

  it('stops daily paging as soon as Monthly is pressed', async () => {
    vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue(historicalPage(3))
    history.mockReturnValue(new Promise(() => {}))
    const wrapper = mountPage()
    await settle()
    const daily = scroll.canLoadMore()

    await showMonthly(wrapper)

    expect([daily, scroll.canLoadMore()]).toEqual([true, false])
  })

  it('never asks for another daily page in Monthly', async () => {
    localStorage.setItem(STORAGE_KEYS.SUMMARY_INTERVAL, 'monthly')
    vi.mocked(portfolioSummaryService.getHistorical)
      .mockResolvedValueOnce(historicalPage(3))
      .mockReturnValue(new Promise(() => {}))
    const wrapper = mountPage()
    await settle()
    scroll.load()
    await settle()
    const monthly = [scroll.canLoadMore(), nextPageSkeleton(wrapper)]

    await chip(wrapper, 'Daily').trigger('click')
    await settle()

    expect([monthly, [scroll.canLoadMore(), nextPageSkeleton(wrapper)]]).toEqual([
      [false, false],
      [true, true],
    ])
  })

  it('returns to Daily with a toast when MAX history fails', async () => {
    history.mockRejectedValue(new Error('down'))
    const wrapper = mountPage()
    await settle()

    await showMonthly(wrapper)

    expect([
      history.mock.calls.length,
      pressed(wrapper),
      localStorage.getItem(STORAGE_KEYS.SUMMARY_INTERVAL),
      toasts.value.map(toast => toast.message),
    ]).toEqual([2, ['Daily'], 'daily', [LOAD_FAILED]])
  })

  it('ignores a late MAX failure after switching back to Daily', async () => {
    const max = deferred<PortfolioSummaryDto[]>()
    history.mockReturnValueOnce(max.promise).mockRejectedValue(new Error('down'))
    const wrapper = mountPage()
    await settle()
    await showMonthly(wrapper)
    await chip(wrapper, 'Daily').trigger('click')

    max.reject(new Error('down'))
    await settle()

    expect([history.mock.calls.length, pressed(wrapper), toasts.value]).toEqual([2, ['Daily'], []])
  })

  it('shows placeholders, not old months, after a platform change', async () => {
    localStorage.setItem(STORAGE_KEYS.SUMMARY_INTERVAL, 'monthly')
    history.mockResolvedValueOnce(SERIES_RESPONSE).mockReturnValue(new Promise(() => {}))
    const wrapper = mountPage()
    await settle()
    const before = has(wrapper, '.monthly-profit-calendar .calendar-grid')

    await platform(wrapper, 'LHV').trigger('click')
    await settle()

    expect([
      before,
      has(wrapper, '.monthly-profit-calendar[aria-hidden="true"]'),
      wrapper.findAll('tbody tr').length,
    ]).toEqual([true, true, 0])
  })

  it('retries a failed MAX refresh from the notice', async () => {
    localStorage.setItem(STORAGE_KEYS.SUMMARY_INTERVAL, 'monthly')
    const retried = deferred<PortfolioSummaryDto[]>()
    history
      .mockResolvedValueOnce(SERIES_RESPONSE)
      .mockRejectedValueOnce(new Error('down'))
      .mockRejectedValueOnce(new Error('down'))
      .mockReturnValueOnce(retried.promise)
    const queryClient = createQueryClient()
    const wrapper = mountPage(queryClient)
    await settle()
    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await settle()
    const failed = retryButton(wrapper).text()
    await retryButton(wrapper).trigger('click')
    await settle()
    const retrying = retryButton(wrapper).text()

    retried.resolve(SERIES_RESPONSE)
    await settle()

    expect([failed, retrying, retryButton(wrapper).exists()]).toEqual([
      'Try again',
      'Retrying…',
      false,
    ])
  })

  it('selects the latest month after switching to cached platforms', async () => {
    localStorage.setItem(STORAGE_KEYS.SUMMARY_INTERVAL, 'monthly')
    const wrapper = mountPage()
    await settle()
    await platform(wrapper, 'TRADING212').trigger('click')
    await settle()
    await monthCell(wrapper, 'November 2025').trigger('click')
    const chosen = monthLabel(wrapper)

    await platform(wrapper, 'TRADING212').trigger('click')
    await settle()

    expect([chosen, monthLabel(wrapper)]).toEqual(['November 2025', 'December 2025'])
  })

  it("restores Daily's stored day after visiting Monthly", async () => {
    localStorage.setItem(STORAGE_KEYS.SUMMARY_CALENDAR_DAY, '2025-12-02')
    const wrapper = mountPage()
    await settle()
    await showMonthly(wrapper)
    await monthCell(wrapper, 'November 2025').trigger('click')

    await chip(wrapper, 'Daily').trigger('click')
    await settle()

    expect([
      wrapper.find('.daily-profit-calendar .selected').attributes('aria-label'),
      localStorage.getItem(STORAGE_KEYS.SUMMARY_CALENDAR_DAY),
    ]).toEqual([expect.stringMatching(/^Tue 2 Dec 2025,/), '2025-12-02'])
  })

  it('selects the latest month again after leaving Monthly', async () => {
    const wrapper = mountPage()
    await settle()
    await showMonthly(wrapper)
    await monthCell(wrapper, 'November 2025').trigger('click')
    const chosen = monthLabel(wrapper)

    await chip(wrapper, 'Daily').trigger('click')
    await showMonthly(wrapper)

    expect([chosen, monthLabel(wrapper)]).toEqual(['November 2025', 'December 2025'])
  })
})
