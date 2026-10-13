import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import PortfolioSummary from './portfolio-summary.vue'
import { portfolioSummaryService, transactionsService } from '../services/api'
import { createPortfolioSummaryDto } from '../tests/fixtures'
import { TimeRange, type PortfolioSummaryDto } from '../models/generated/domain-models'
import { toasts } from '../composables/use-toast'

vi.mock('../services/api')
vi.mock('../composables/use-auth-state', () => ({
  useAuthState: () => ({ isAuthenticated: ref(true) }),
}))
vi.mock('../composables/use-confirm', () => ({
  useConfirm: () => ({ confirm: vi.fn().mockResolvedValue(true) }),
}))

enableAutoUnmount(afterEach)

const ROWS = [
  createPortfolioSummaryDto({ date: '2025-06-27', totalProfit: 1000 }),
  createPortfolioSummaryDto({ date: '2025-06-30', totalProfit: 1200 }),
  createPortfolioSummaryDto({ date: '2025-07-31', totalProfit: 1500 }),
]

const setup = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  const wrapper = mount(PortfolioSummary, {
    attachTo: document.body,
    global: { plugins: [[VueQueryPlugin, { queryClient }]], stubs: { PortfolioChart: true } },
  })
  return { wrapper, queryClient }
}

const heldHistory = () => {
  let finish: (rows: PortfolioSummaryDto[]) => void = () => {}
  const history = new Promise<PortfolioSummaryDto[]>(resolve => {
    finish = resolve
  })
  vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range =>
    range === TimeRange.MAX ? history : ROWS
  )
  return finish
}

const chip = (wrapper: ReturnType<typeof setup>['wrapper'], label: string) =>
  wrapper.findAll('[aria-label="Interval"] button').find(button => button.text() === label)!

describe('Summary interval integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    toasts.value = []
    vi.mocked(transactionsService.getPlatforms).mockResolvedValue(['LHV'])
    vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue({
      content: ROWS,
      totalPages: 2,
      totalElements: 60,
      size: 30,
      number: 0,
    })
    vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(ROWS[2])
    vi.mocked(portfolioSummaryService.getSeries).mockResolvedValue(ROWS)
    vi.mocked(portfolioSummaryService.getBenchmark).mockResolvedValue([])
    vi.mocked(portfolioSummaryService.getIntraday).mockResolvedValue([])
    vi.mocked(portfolioSummaryService.getRangeChange).mockResolvedValue({
      changeAmount: 300,
      changePercent: 3,
    })
    vi.mocked(portfolioSummaryService.recalculate).mockResolvedValue({ message: 'Recalculated' })
  })

  it('shows Daily by default and switches both calendar and table together after MAX', async () => {
    const finish = heldHistory()
    const { wrapper } = setup()
    await flushPromises()
    expect(chip(wrapper, 'Daily').attributes('aria-pressed')).toBe('true')
    const monthly = chip(wrapper, 'Monthly')
    ;(monthly.element as HTMLElement).focus()
    await monthly.trigger('click')
    await flushPromises()
    expect(monthly.attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('.summary-history').attributes('aria-busy')).toBe('true')
    expect(wrapper.find('.daily-profit-calendar').exists()).toBe(true)
    expect(wrapper.find('.monthly-profit-calendar').exists()).toBe(false)
    expect(wrapper.find('thead').text()).toContain('24h Change')
    expect(wrapper.find('.loading-veil.delayed').text()).toContain('Loading months')
    finish(ROWS)
    await flushPromises()
    expect(wrapper.find('.daily-profit-calendar').exists()).toBe(false)
    expect(wrapper.find('.monthly-profit-calendar').exists()).toBe(true)
    expect(wrapper.find('thead').text()).toContain('Month Change')
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
    expect(document.activeElement).toBe(monthly.element)
    expect(wrapper.find('.summary-interval-announcement').text()).toBe(
      'Monthly profit Jun – Jul 2025'
    )
  })

  it('restores Monthly without first displaying Daily and reuses its cache on reentry', async () => {
    localStorage.setItem('portfolio_summary_interval', 'monthly')
    localStorage.setItem('portfolio_selected_summary_platforms', '["LHV"]')
    const finish = heldHistory()
    const { wrapper } = setup()
    await flushPromises()
    expect(wrapper.find('.daily-profit-calendar').exists()).toBe(false)
    expect(wrapper.find('.summary-monthly-placeholder').exists()).toBe(true)
    finish(ROWS)
    await flushPromises()
    expect(wrapper.find('.summary-interval-announcement').text()).toBe('')
    await chip(wrapper, 'Daily').trigger('click')
    await chip(wrapper, 'Monthly').trigger('click')
    await flushPromises()
    expect(wrapper.find('.monthly-profit-calendar').exists()).toBe(true)
    expect(
      vi.mocked(portfolioSummaryService.getSeries).mock.calls.filter(([range]) => range === 'MAX')
    ).toHaveLength(1)
  })

  it('ignores MAX arriving after the user returns to Daily', async () => {
    const finish = heldHistory()
    const { wrapper } = setup()
    await flushPromises()
    await chip(wrapper, 'Monthly').trigger('click')
    await chip(wrapper, 'Daily').trigger('click')
    finish(ROWS)
    await flushPromises()
    expect(wrapper.find('.daily-profit-calendar').exists()).toBe(true)
    expect(wrapper.find('thead').text()).toContain('24h Change')
    expect(chip(wrapper, 'Daily').attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('.summary-interval-announcement').text()).toBe('')
    expect(toasts.value).toHaveLength(0)
  })

  it('restores the picked Daily day after leaving Monthly', async () => {
    const { wrapper } = setup()
    await flushPromises()
    await wrapper.find('.calendar-body [aria-label^="Mon 30 Jun 2025"]').trigger('click')
    expect(localStorage.getItem('portfolio_summary_calendar_day')).toBe('2025-06-30')
    await chip(wrapper, 'Monthly').trigger('click')
    await flushPromises()
    await chip(wrapper, 'Daily').trigger('click')
    await flushPromises()
    expect(wrapper.find('.calendar-body .selected').attributes('aria-label')).toContain(
      'Mon 30 Jun 2025'
    )
    expect(localStorage.getItem('portfolio_summary_calendar_day')).toBe('2025-06-30')
  })

  it('keeps new-platform placeholders when an old-platform MAX response arrives', async () => {
    localStorage.setItem('portfolio_selected_summary_platforms', '["LHV"]')
    vi.mocked(transactionsService.getPlatforms).mockResolvedValue(['LHV', 'SWEDBANK'])
    let finishOld: (rows: PortfolioSummaryDto[]) => void = () => {}
    let finishNew: (rows: PortfolioSummaryDto[]) => void = () => {}
    const oldHistory = new Promise<PortfolioSummaryDto[]>(resolve => {
      finishOld = resolve
    })
    const newHistory = new Promise<PortfolioSummaryDto[]>(resolve => {
      finishNew = resolve
    })
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async (range, platforms) => {
      if (range !== TimeRange.MAX) return ROWS
      return platforms?.includes('LHV') ? oldHistory : newHistory
    })
    const { wrapper } = setup()
    await flushPromises()
    await chip(wrapper, 'Monthly').trigger('click')
    const platformButton = (name: string) =>
      wrapper.findAll('.platform-buttons button').find(button => button.text() === name)!
    await platformButton('LHV').trigger('click')
    await platformButton('SWEDBANK').trigger('click')
    await flushPromises()
    finishOld(ROWS)
    await flushPromises()
    expect(wrapper.find('.summary-monthly-placeholder').exists()).toBe(true)
    expect(wrapper.find('.monthly-profit-calendar').exists()).toBe(false)
    expect(chip(wrapper, 'Monthly').attributes('aria-pressed')).toBe('true')
    expect(toasts.value).toHaveLength(0)
    finishNew(ROWS)
    await flushPromises()
    expect(wrapper.find('.monthly-profit-calendar').exists()).toBe(true)
    expect(wrapper.find('.summary-interval-announcement').text()).toBe('')
  })

  it('retains monthly results and offers retry after a failed background refresh', async () => {
    const { wrapper, queryClient } = setup()
    await flushPromises()
    await chip(wrapper, 'Monthly').trigger('click')
    await flushPromises()
    const before = wrapper.find('tbody').text()
    queryClient.setQueryDefaults(['portfolio-summary', 'daily-calendar-history'], { retry: false })
    vi.mocked(portfolioSummaryService.getSeries).mockImplementation(async range => {
      if (range === TimeRange.MAX) throw new Error('offline')
      return ROWS
    })
    await queryClient.invalidateQueries({
      queryKey: ['portfolio-summary', 'daily-calendar-history'],
    })
    await flushPromises()
    expect(wrapper.find('tbody').text()).toBe(before)
    expect(wrapper.find('.summary-history-notice').text()).toContain('as of')
    expect(wrapper.find('.summary-history-notice button').text()).toBe('Retry monthly history')
    expect(chip(wrapper, 'Monthly').attributes('aria-pressed')).toBe('true')
  })

  it('retains the successful monthly view when background daily-table history fails', async () => {
    const { wrapper, queryClient } = setup()
    await flushPromises()
    await chip(wrapper, 'Monthly').trigger('click')
    await flushPromises()
    const before = wrapper.find('tbody').text()
    vi.mocked(portfolioSummaryService.getHistorical).mockRejectedValue(new Error('offline'))
    await queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'historical'] })
    await flushPromises()
    expect(wrapper.find('.monthly-profit-calendar').exists()).toBe(true)
    expect(wrapper.find('tbody').text()).toBe(before)
    expect(wrapper.find('.summary-history-notice').text()).toContain('as of')
    vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue({
      content: ROWS,
      totalPages: 1,
      totalElements: 3,
      size: 30,
      number: 0,
    })
    await wrapper.find('.summary-history-notice button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.summary-history-notice').exists()).toBe(false)
  })
})
