import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { Chart } from 'chart.js'
import BreakdownPanel from './breakdown-panel.vue'
import { diversificationService } from '../../services/api'
import { createHoldingDto as holding, createHoldingsPage as page } from '../../tests/fixtures'
import type { DiversificationHoldingDto } from '../../models/generated/domain-models'
import type { Page } from '../../models/page'

vi.mock('../../services/api', () => ({ diversificationService: { searchHoldings: vi.fn() } }))
vi.mock('chart.js', () => ({
  Chart: Object.assign(
    vi.fn(function (_canvas: unknown, config: { data: unknown }) {
      return { data: config.data, destroy: vi.fn(), update: vi.fn(), setActiveElements: vi.fn() }
    }),
    { register: vi.fn() }
  ),
  DoughnutController: vi.fn(),
  ArcElement: vi.fn(),
}))

enableAutoUnmount(afterEach)

let reachEnd = () => {}

const observeEnd = (stays = false) =>
  vi.stubGlobal(
    'IntersectionObserver',
    vi.fn(function (notify: (entries: { isIntersecting: boolean }[]) => void) {
      const reach = () => notify([{ isIntersecting: true }])
      return {
        observe: () => {
          reachEnd = reach
          if (stays) reach()
        },
        disconnect: vi.fn(),
      }
    })
  )

const mountPanel = (attachTo?: HTMLElement) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  return mount(BreakdownPanel, {
    attachTo,
    props: {
      allocations: [{ instrumentId: 7, percentage: 100 }],
      breakdowns: { holdings: [], sectors: [], industries: [], countries: [] },
      benchmark: {
        holdings: [{ label: 'nvidia', value: 2 }],
        sectors: [],
        industries: [],
        countries: [],
      },
      benchmarkLabel: 'VGLA',
    },
    global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
  })
}

describe('Holdings tab', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('portfolio_diversification_breakdown_tab', 'holdings')
    observeEnd()
    vi.mocked(diversificationService.searchHoldings)
      .mockReset()
      .mockResolvedValue(
        page([holding('NVIDIA', 4), holding('Nestlé S.A.', 0.0049), holding('Rounded', 0)])
      )
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('lists a full server page and the total number of holdings', async () => {
    vi.mocked(diversificationService.searchHoldings).mockResolvedValue(
      page(
        Array.from({ length: 100 }, (_, index) => holding(`Company ${index}`, 100 - index)),
        3632
      )
    )
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.findAll('.breakdown-row')).toHaveLength(100)
    expect(wrapper.text()).toContain('3,632 holdings')
    expect(wrapper.find('.holdings-more').text()).toBe('Loading more…')
  })

  it('appends the next hundred when the end of the list comes into view', async () => {
    vi.mocked(diversificationService.searchHoldings)
      .mockResolvedValueOnce(page([holding('First', 10)], 201))
      .mockResolvedValueOnce(page([holding('Second', 5)], 201, 1))
    const wrapper = mountPanel()
    await flushPromises()
    reachEnd()
    await flushPromises()
    expect(wrapper.text()).toContain('201 holdings')
    expect(wrapper.findAll('.row-label').map(row => row.text())).toEqual(['First', 'Second'])
  })

  it('keeps loading while the end of the list stays in view', async () => {
    observeEnd(true)
    vi.mocked(diversificationService.searchHoldings)
      .mockResolvedValueOnce(page([holding('First', 10)], 201))
      .mockResolvedValueOnce(page([holding('Second', 5)], 201, 1))
      .mockResolvedValueOnce(page([holding('Third', 1)], 201, 2))
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.findAll('.row-label').map(row => row.text())).toEqual([
      'First',
      'Second',
      'Third',
    ])
  })

  it('keeps loading after a new search returns as many rows as were loaded', async () => {
    vi.useFakeTimers()
    observeEnd(true)
    vi.mocked(diversificationService.searchHoldings)
      .mockResolvedValueOnce(page([holding('First', 10)], 201))
      .mockReturnValueOnce(new Promise(() => {}))
      .mockResolvedValueOnce(page([holding('Other', 9)], 201))
      .mockResolvedValueOnce(page([holding('More', 5)], 201, 1))
      .mockResolvedValueOnce(page([holding('Last', 1)], 201, 2))
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.find('input.search-input').setValue('o')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(wrapper.findAll('.row-label').map(row => row.text())).toEqual(['Other', 'More', 'Last'])
  })

  it('shows no loading line when every match is loaded', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('.holdings-more').exists()).toBe(false)
  })

  it('offers another attempt when more holdings cannot be loaded', async () => {
    vi.mocked(diversificationService.searchHoldings)
      .mockResolvedValueOnce(page([holding('First', 10)], 201))
      .mockRejectedValueOnce(new Error('Search failed'))
      .mockResolvedValueOnce(page([holding('Second', 5)], 201, 1))
    const wrapper = mountPanel()
    await flushPromises()
    reachEnd()
    await flushPromises()
    expect(wrapper.find('.holdings-more [role="alert"]').text()).toBe('Couldn’t load more holdings')
    await wrapper.find('.holdings-more button').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.row-label').map(row => row.text())).toEqual(['First', 'Second'])
  })

  it('asks once for more when the load fails while the end of the list stays in view', async () => {
    observeEnd(true)
    vi.mocked(diversificationService.searchHoldings)
      .mockResolvedValueOnce(page([holding('First', 10)], 201))
      .mockRejectedValue(new Error('Search failed'))
    mountPanel()
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(2)
  })

  it('keeps Retry focused and marks it in progress while another attempt runs', async () => {
    vi.mocked(diversificationService.searchHoldings)
      .mockResolvedValueOnce(page([holding('First', 10)], 201))
      .mockRejectedValueOnce(new Error('Search failed'))
      .mockReturnValueOnce(new Promise(() => {}))
    const wrapper = mountPanel(document.body)
    await flushPromises()
    reachEnd()
    await flushPromises()
    const retry = wrapper.find<HTMLButtonElement>('.holdings-more button')
    retry.element.focus()
    await retry.trigger('click')
    await flushPromises()
    expect(retry.text()).toBe('Retrying…')
    expect(document.activeElement).toBe(retry.element)
  })

  it('loads nothing more behind the donut', async () => {
    vi.mocked(diversificationService.searchHoldings).mockResolvedValue(
      page([holding('First', 10)], 201)
    )
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('.view-btn')[0].trigger('click')
    expect(wrapper.find('.holdings-more').exists()).toBe(false)
  })

  it('joins benchmark figures by name and omits them for unowned holdings', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    const rows = wrapper.findAll('.breakdown-row')
    expect(rows[0].find('.row-benchmark').text()).toBe('2.00% · 2.00×')
    expect(rows[1].find('.row-benchmark').exists()).toBe(false)
    expect(rows.map(row => row.find('.row-value').text())).toEqual(['4.00%', '0.0049%', '<0.0001%'])
  })

  it('shows the empty query state and keeps the search usable', async () => {
    vi.useFakeTimers()
    const wrapper = mountPanel()
    await flushPromises()
    vi.mocked(diversificationService.searchHoldings).mockResolvedValueOnce(page([]))
    await wrapper.find('input.search-input').setValue('xyz')
    await vi.advanceTimersByTimeAsync(300)
    expect(wrapper.text()).toContain('No holdings match “xyz”')
    expect(wrapper.text()).toContain('0 matches')
    expect(wrapper.findAll('.breakdown-row')).toHaveLength(0)
    await wrapper.find('[aria-label="Clear search"]').trigger('click')
    await vi.advanceTimersByTimeAsync(300)
    expect(wrapper.findAll('.breakdown-row')).toHaveLength(3)
  })

  it('shows an error in place of the list and allows another search', async () => {
    vi.useFakeTimers()
    vi.mocked(diversificationService.searchHoldings).mockRejectedValueOnce(
      new Error('Search failed')
    )
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('Search failed')
    expect(wrapper.findAll('.breakdown-row')).toHaveLength(0)
    await wrapper.find('input.search-input').setValue('nvda')
    vi.mocked(diversificationService.searchHoldings).mockResolvedValueOnce(
      page([holding('NVIDIA', 4)])
    )
    await vi.advanceTimersByTimeAsync(300)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('1 match')
  })

  it('keeps and dims the rows while searching', async () => {
    vi.useFakeTimers()
    const wrapper = mountPanel()
    await flushPromises()
    let resolvePending!: (value: Page<DiversificationHoldingDto>) => void
    const pending = new Promise<Page<DiversificationHoldingDto>>(resolve => {
      resolvePending = resolve
    })
    vi.mocked(diversificationService.searchHoldings).mockReturnValueOnce(pending)
    await wrapper.find('input.search-input').setValue('nestle')
    await vi.advanceTimersByTimeAsync(300)
    expect(wrapper.text()).toContain('Searching…')
    expect(wrapper.find('[aria-busy="true"]').classes()).toContain('searching')
    expect(wrapper.findAll('.breakdown-row')).toHaveLength(3)
    resolvePending(page([holding('Nestlé S.A.', 0.0049)]))
    await flushPromises()
    expect(wrapper.text()).toContain('1 match')
  })

  it('draws the fifteen largest loaded holdings in the donut', async () => {
    const holdings = Array.from({ length: 100 }, (_, index) =>
      holding(`Company ${index}`, 1 - index / 100)
    )
    vi.mocked(diversificationService.searchHoldings).mockResolvedValue(page(holdings, 200))
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('.view-btn')[0].trigger('click')
    await vi.dynamicImportSettled()
    expect(wrapper.findAll('.legend-label').map(row => row.text())).toEqual(
      Array.from({ length: 15 }, (_, index) => `Company ${index}`)
    )
  })

  it('uses small weight formatting in the Holdings donut legend', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('.view-btn')[0].trigger('click')
    await vi.dynamicImportSettled()
    expect(wrapper.findAll('.legend-value').map(row => row.text())).toEqual([
      '4.00%',
      '0.0049%',
      '<0.0001%',
    ])
  })

  it('clears the donut while the first holdings page loads and redraws it afterwards', async () => {
    localStorage.setItem('portfolio_diversification_breakdown_tab', 'sectors')
    localStorage.setItem('portfolio_diversification_breakdown_view', 'donut')
    let resolvePending!: (value: Page<DiversificationHoldingDto>) => void
    const pending = new Promise<Page<DiversificationHoldingDto>>(resolve => {
      resolvePending = resolve
    })
    vi.mocked(diversificationService.searchHoldings).mockReturnValueOnce(pending)
    vi.mocked(Chart).mockClear()
    const wrapper = mountPanel()
    await wrapper.setProps({
      breakdowns: {
        holdings: [],
        sectors: [{ label: 'Énergie', value: 30 }],
        industries: [],
        countries: [],
      },
    })
    await vi.dynamicImportSettled()
    await wrapper.findAll('.breakdown-tab')[2].trigger('click')
    expect(vi.mocked(Chart).mock.results[0].value.destroy).toHaveBeenCalled()
    resolvePending(page([holding('Nestlé S.A.', 4)]))
    await flushPromises()
    expect(vi.mocked(Chart).mock.calls.map(([, config]) => config.data.labels)).toEqual([
      ['Énergie'],
      ['Nestlé S.A.'],
    ])
  })
})
