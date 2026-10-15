import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import PortfolioSummary from './portfolio-summary.vue'
import { toasts } from '../composables/use-toast'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { portfolioSummaryService, transactionsService } from '../services/api'
import { renderWithProviders } from '../tests/test-utils'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../tests/visual/summary-fixture'

const scroll = vi.hoisted(() => ({ load: async () => {} }))

vi.mock('../services/api')
vi.mock('./portfolio/portfolio-chart.vue', () => ({
  [Symbol.toStringTag]: 'Module',
  default: { render: () => null },
}))
vi.mock('../composables/use-auth-state', () => ({
  useAuthState: () => ({
    isAuthenticated: ref(true),
    isAuthChecking: ref(false),
    checkAuth: vi.fn().mockResolvedValue(true),
  }),
}))
vi.mock('../composables/use-confirm', () => ({ useConfirm: () => ({ confirm: vi.fn() }) }))
vi.mock('@vueuse/core', async original => ({
  ...(await original<typeof import('@vueuse/core')>()),
  useInfiniteScroll: (_target: unknown, load: () => Promise<void>) => {
    scroll.load = load
  },
}))

enableAutoUnmount(afterEach)

const INTERVAL_KEY = 'portfolio_summary_interval'
const RETRY_DELAY = 1000
const DECEMBER = SERIES_RESPONSE.filter(row => row.date >= '2025-12-10')
const FAILURE = 'Could not load the monthly history. Press Monthly to try again.'

type Answer = PortfolioSummaryDto[] | Error
interface Pending {
  platforms: string[] | undefined
  settle: (answer: Answer) => void
}

describe('PortfolioSummary interval', () => {
  let pending: Pending[]
  let recent: PortfolioSummaryDto[]

  const mockServices = () => {
    vi.mocked(transactionsService.getPlatforms).mockResolvedValue(['LHV', 'SWEDBANK'])
    vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue({
      content: SERIES_RESPONSE.slice(-3),
      totalElements: 60,
      totalPages: 2,
      size: 30,
      number: 0,
    })
    vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(TODAY_SUMMARY)
    vi.mocked(portfolioSummaryService.getRangeChange).mockResolvedValue({
      changeAmount: 0,
      changePercent: 0,
    })
    vi.mocked(portfolioSummaryService.getBenchmark).mockResolvedValue([])
    vi.mocked(portfolioSummaryService.getIntraday).mockResolvedValue([])
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation((range, platforms) => {
      if (range !== 'MAX') return Promise.resolve(recent)
      return new Promise((resolve, reject) => {
        pending.push({
          platforms,
          settle: answer => (answer instanceof Error ? reject(answer) : resolve(answer)),
        })
      })
    })
  }

  const open = async () => {
    const wrapper = renderWithProviders(PortfolioSummary)
    await flushPromises()
    return wrapper
  }

  type Page = Awaited<ReturnType<typeof open>>

  const chip = (wrapper: Page, label: string) =>
    wrapper.findAll('[aria-label=Interval] button').find(button => button.text() === label)!
  const press = async (wrapper: Page, label: string) => {
    await chip(wrapper, label).trigger('click')
    await flushPromises()
  }
  const answer = async (index: number, rows: Answer) => {
    pending[index].settle(rows)
    await flushPromises()
  }
  const fail = async (first: number) => {
    await answer(first, new Error('down'))
    await vi.advanceTimersByTimeAsync(RETRY_DELAY)
    await answer(first + 1, new Error('down'))
  }
  const shown = (wrapper: Page) => [
    wrapper.find('.daily-profit-calendar').exists(),
    wrapper.find('.monthly-profit-calendar').exists(),
    wrapper.findAll('table tbody tr').length,
  ]
  const months = (wrapper: Page) =>
    wrapper.findAll('table tbody tr').map(row => row.find('td').text().slice(0, 8))
  const status = (wrapper: Page) => wrapper.find('.interval-status').text()

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    localStorage.setItem('portfolio_selected_summary_platforms', '["LHV","SWEDBANK"]')
    toasts.value = []
    pending = []
    recent = SERIES_RESPONSE
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-01-15T12:00:00'))
    mockServices()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('opens in Daily with the switch between the chart controls and the calendar', async () => {
    const wrapper = await open()
    const group = wrapper.find('[aria-label=Interval]').element
    expect([
      chip(wrapper, 'Daily').attributes('aria-pressed'),
      group.previousElementSibling?.querySelector('.platform-buttons') !== null,
      group.nextElementSibling?.querySelector('.daily-profit-calendar') !== null,
      shown(wrapper),
      pending.length,
    ]).toEqual(['true', true, true, [true, false, 4], 0])
  })

  it('keeps Daily under a veil while the months load, then swaps card and table together', async () => {
    const wrapper = await open()

    await press(wrapper, 'Monthly')
    const loading = [
      wrapper.find('[aria-busy]').attributes('aria-busy'),
      wrapper.find('.loading-veil').text(),
      shown(wrapper),
    ]
    await answer(0, SERIES_RESPONSE)

    expect([
      loading,
      shown(wrapper),
      wrapper.find('[aria-busy]').exists(),
      wrapper.find('.monthly-profit-calendar h2').text(),
      pending.length,
    ]).toEqual([
      ['true', 'Loading months', [true, false, 4]],
      [false, true, 7],
      false,
      'Monthly profit',
      1,
    ])
  })

  it('never shows Daily when it opens on a stored Monthly', async () => {
    localStorage.setItem(INTERVAL_KEY, 'monthly')
    const wrapper = await open()
    const waiting = [
      shown(wrapper),
      wrapper.find('.monthly-profit-calendar').attributes('aria-hidden'),
    ]

    await answer(0, SERIES_RESPONSE)

    expect([waiting, shown(wrapper), status(wrapper)]).toEqual([
      [[false, true, 0], 'true'],
      [false, true, 7],
      '',
    ])
  })

  it('returns to Daily with one message when the months fail and asks again on the next press', async () => {
    const wrapper = await open()
    await press(wrapper, 'Monthly')

    await fail(0)
    const failed = [
      toasts.value.map(toast => toast.message),
      chip(wrapper, 'Daily').attributes('aria-pressed'),
      localStorage.getItem(INTERVAL_KEY),
      shown(wrapper),
      wrapper.find('[aria-busy]').exists(),
    ]
    await press(wrapper, 'Monthly')

    expect([failed, pending.length]).toEqual([
      [[FAILURE], 'true', 'daily', [true, false, 4], false],
      3,
    ])
  })

  it('stays in Daily without announcing when the months arrive after Daily was pressed', async () => {
    const wrapper = await open()
    await press(wrapper, 'Monthly')
    await press(wrapper, 'Daily')

    await answer(0, SERIES_RESPONSE)

    expect([shown(wrapper), status(wrapper), localStorage.getItem(INTERVAL_KEY)]).toEqual([
      [true, false, 4],
      '',
      'daily',
    ])
  })

  it('announces a pressed Monthly once with its span', async () => {
    const wrapper = await open()
    await press(wrapper, 'Monthly')
    await answer(0, SERIES_RESPONSE)
    expect(status(wrapper)).toBe('Monthly profit Jun – Dec 2025')
  })

  it('shows a placeholder for a new platform selection and only its own months', async () => {
    const wrapper = await open()
    await press(wrapper, 'Monthly')
    await answer(0, SERIES_RESPONSE)

    recent = DECEMBER
    await wrapper
      .findAll('.platform-btn')
      .find(button => button.text() === 'LHV')!
      .trigger('click')
    await flushPromises()
    const waiting = [shown(wrapper), pending[1].platforms]
    await answer(1, DECEMBER)

    expect([waiting, months(wrapper)]).toEqual([[[false, true, 0], ['SWEDBANK']], ['Dec 2025']])
  })

  it('loads no further daily pages while Monthly is selected', async () => {
    const wrapper = await open()
    await scroll.load()
    await flushPromises()
    const daily = vi.mocked(portfolioSummaryService.getHistorical).mock.calls.length
    await press(wrapper, 'Monthly')
    await answer(0, SERIES_RESPONSE)

    await scroll.load()
    await flushPromises()

    expect([daily, vi.mocked(portfolioSummaryService.getHistorical).mock.calls.length]).toEqual([
      2, 2,
    ])
  })

  it('replaces the months when the summary is recalculated', async () => {
    const wrapper = await open()
    await press(wrapper, 'Monthly')
    await answer(0, SERIES_RESPONSE)

    recent = DECEMBER
    wrapper.queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()
    await answer(1, DECEMBER)

    expect(months(wrapper)).toEqual(['Dec 2025'])
  })

  it('keeps the months with a notice when a refresh fails and asks again on Retry', async () => {
    const wrapper = await open()
    await press(wrapper, 'Monthly')
    await answer(0, SERIES_RESPONSE)
    wrapper.queryClient.invalidateQueries({ queryKey: ['portfolio-summary'] })
    await flushPromises()

    await fail(1)
    const notice = wrapper.find('.stale-notice')
    const stale = [notice.text().replace(/\s+/g, ' '), shown(wrapper), toasts.value.length]
    await notice.find('button').trigger('click')
    await flushPromises()
    await answer(3, SERIES_RESPONSE)

    expect([stale, pending.length, wrapper.find('.stale-notice').exists()]).toEqual([
      [
        'Could not refresh the monthly history. Showing months as of 31.12.25. Retry',
        [false, true, 7],
        0,
      ],
      4,
      false,
    ])
  })

  it('returns to the stored day when Daily is pressed again', async () => {
    localStorage.setItem('portfolio_summary_calendar_day', '2025-12-20')
    const wrapper = await open()
    await press(wrapper, 'Monthly')
    await answer(0, SERIES_RESPONSE)

    await press(wrapper, 'Daily')

    expect(wrapper.find('.day-label').text()).toContain('20 Dec 2025')
  })

  it('says so inside the loaded page when the history is too short for a month', async () => {
    recent = []
    const wrapper = await open()
    await press(wrapper, 'Monthly')

    await answer(0, [])

    expect([
      wrapper.text().includes('Not enough daily summary history to calculate monthly changes.'),
      wrapper.find('[aria-label=Interval]').exists(),
      wrapper.findAll('table tbody tr').length,
    ]).toEqual([true, true, 0])
  })
})
