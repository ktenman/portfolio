import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, isProxy, nextTick, ref } from 'vue'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { onlineManager, QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { useHoldingsSearch } from './use-holdings-search'
import { diversificationService } from '../services/api'
import { createHoldingDto, createHoldingsPage } from '../tests/fixtures'
import type { DiversificationHoldingDto } from '../models/generated/domain-models'
import type { Page } from '../models/page'

vi.mock('../services/api', () => ({ diversificationService: { searchHoldings: vi.fn() } }))

enableAutoUnmount(afterEach)

const response = (name = 'NVIDIA', number = 0) =>
  createHoldingsPage([createHoldingDto(name, 4)], 201, number)

const firstPage = (query = '', allocations = [{ instrumentId: 7, percentage: 100 }]) => ({
  allocations,
  query,
  page: 0,
})

function deferred() {
  let resolve!: (value: Page<DiversificationHoldingDto>) => void
  const promise = new Promise<Page<DiversificationHoldingDto>>(done => {
    resolve = done
  })
  return { promise, resolve }
}

function setup(enabled = true) {
  const allocations = ref([{ instrumentId: 7, percentage: 100 }])
  const active = ref(enabled)
  let search!: ReturnType<typeof useHoldingsSearch>
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  mount(
    defineComponent({
      setup() {
        search = useHoldingsSearch(allocations, active)
        return () => null
      },
    }),
    { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } }
  )
  const type = async (text: string, wait = 300) => {
    search.query.value = text
    await nextTick()
    await vi.advanceTimersByTimeAsync(wait)
  }
  return { search, allocations, active, type }
}

describe('useHoldingsSearch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    onlineManager.setOnline(true)
    vi.mocked(diversificationService.searchHoldings).mockReset().mockResolvedValue(response())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('fetches the first unfiltered page when the tab opens', async () => {
    const { search } = setup()
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenCalledWith(firstPage())
    expect(search.total.value).toBe(201)
  })

  it('returns the loaded holdings as plain objects instead of reactive proxies', async () => {
    const { search } = setup()
    await flushPromises()
    expect(search.holdings.value?.map(isProxy)).toEqual([false])
  })

  it('waits 300 milliseconds after the last keystroke before requesting a query', async () => {
    const { type } = setup()
    await flushPromises()
    await type('nv', 200)
    await type('nvda', 299)
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith(firstPage('nvda'))
  })

  it('sends no request when the text is cleared before its query is sent', async () => {
    const { search, type } = setup()
    await flushPromises()
    await type('nv', 200)
    await type('', 1000)
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(1)
    expect(search.isSearching.value).toBe(false)
  })

  it('sends no request when spaces are added around a query already sent', async () => {
    const { type } = setup()
    await flushPromises()
    await type('nvda')
    await type(' nvda ')
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(2)
  })

  it('keeps searching while a new query waits for the connection', async () => {
    const { search, type } = setup()
    await flushPromises()
    onlineManager.setOnline(false)
    await type('nestle')
    expect(search.isSearching.value).toBe(true)
  })

  it('resets a new query and a cleared query to the first page', async () => {
    const { search, type } = setup()
    await flushPromises()
    search.loadMore()
    await flushPromises()
    await type('nestle')
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith(firstPage('nestle'))
    search.loadMore()
    await flushPromises()
    await type('')
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith(firstPage())
  })

  it('appends the next page below the rows already loaded', async () => {
    const { search } = setup()
    await flushPromises()
    const next = deferred()
    vi.mocked(diversificationService.searchHoldings).mockReturnValueOnce(next.promise)
    search.loadMore()
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith({
      ...firstPage(),
      page: 1,
    })
    expect(search.isSearching.value).toBe(false)
    next.resolve(response('Nestlé', 1))
    await flushPromises()
    expect(search.holdings.value?.map(holding => holding.name)).toEqual(['NVIDIA', 'Nestlé'])
  })

  it('sends one request when more is asked for twice', async () => {
    const { search } = setup()
    await flushPromises()
    vi.mocked(diversificationService.searchHoldings).mockReturnValueOnce(deferred().promise)
    search.loadMore()
    search.loadMore()
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(2)
  })

  it('stops asking once the last page has arrived', async () => {
    vi.mocked(diversificationService.searchHoldings).mockResolvedValue({
      ...response(),
      totalElements: 1,
      totalPages: 1,
    })
    const { search } = setup()
    await flushPromises()
    search.loadMore()
    await flushPromises()
    expect(search.hasMore.value).toBe(false)
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(1)
  })

  it('keeps the loaded rows when the next page fails and loads it on another attempt', async () => {
    const { search } = setup()
    await flushPromises()
    vi.mocked(diversificationService.searchHoldings).mockRejectedValueOnce(
      new Error('Search failed')
    )
    search.loadMore()
    await flushPromises()
    expect(search.moreFailed.value).toBe(true)
    expect(search.error.value).toBe('')
    expect(search.holdings.value).toHaveLength(1)
    vi.mocked(diversificationService.searchHoldings).mockResolvedValueOnce(response('Nestlé', 1))
    search.loadMore()
    await flushPromises()
    expect(search.moreFailed.value).toBe(false)
    expect(search.holdings.value?.map(holding => holding.name)).toEqual(['NVIDIA', 'Nestlé'])
  })

  it('does not refetch the loaded pages when Holdings is reopened', async () => {
    const { search, active } = setup()
    await flushPromises()
    search.loadMore()
    await flushPromises()
    active.value = false
    await flushPromises()
    active.value = true
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(2)
  })

  it('keeps the rows and the retry offer when Holdings is reopened after a failed load', async () => {
    const { search, active } = setup()
    await flushPromises()
    vi.mocked(diversificationService.searchHoldings).mockRejectedValueOnce(
      new Error('Search failed')
    )
    search.loadMore()
    await flushPromises()
    active.value = false
    await flushPromises()
    vi.mocked(diversificationService.searchHoldings).mockReturnValueOnce(deferred().promise)
    active.value = true
    await flushPromises()
    expect(search.error.value).toBe('')
    expect(search.moreFailed.value).toBe(true)
    expect(search.holdings.value).toHaveLength(1)
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(2)
  })

  it('never replaces a newer query result with a slower earlier response', async () => {
    const { search, type } = setup()
    await flushPromises()
    const earlier = deferred()
    vi.mocked(diversificationService.searchHoldings)
      .mockReturnValueOnce(earlier.promise)
      .mockResolvedValueOnce(response('Nestlé'))
    await type('nvda')
    await type('nestle')
    expect(search.holdings.value?.[0].name).toBe('Nestlé')
    earlier.resolve(response('NVIDIA'))
    await flushPromises()
    expect(search.holdings.value?.[0].name).toBe('Nestlé')
  })

  it('makes no request on other tabs even when the query or allocations change', async () => {
    const { allocations, active, type } = setup(false)
    allocations.value = [{ instrumentId: 8, percentage: 100 }]
    await type('nestle')
    expect(diversificationService.searchHoldings).not.toHaveBeenCalled()
    active.value = true
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(1)
  })

  it('does not send a delayed request after leaving Holdings', async () => {
    const { search, active } = setup()
    await flushPromises()
    search.query.value = 'nestle'
    await nextTick()
    active.value = false
    await vi.advanceTimersByTimeAsync(1000)
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(1)
  })

  it('requests the first page again when the displayed calculation changes', async () => {
    const { search, allocations } = setup()
    await flushPromises()
    search.loadMore()
    await flushPromises()
    allocations.value = [{ instrumentId: 8, percentage: 75 }]
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith(
      firstPage('', [{ instrumentId: 8, percentage: 75 }])
    )
    allocations.value = [{ instrumentId: 8, percentage: 75 }]
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(4)
  })

  it('exposes an error and recovers when another query succeeds', async () => {
    vi.mocked(diversificationService.searchHoldings).mockRejectedValueOnce(
      new Error('Search failed')
    )
    const { search, type } = setup()
    await flushPromises()
    expect(search.error.value).toBe('Search failed')
    await type('nvda')
    expect(search.error.value).toBe('')
    expect(search.holdings.value?.[0].name).toBe('NVIDIA')
  })
})
