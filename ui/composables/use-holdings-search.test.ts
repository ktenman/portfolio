import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { useHoldingsSearch } from './use-holdings-search'
import { diversificationService } from '../services/api'
import type { DiversificationHoldingDto } from '../models/generated/domain-models'
import type { Page } from '../models/page'

vi.mock('../services/api', () => ({ diversificationService: { searchHoldings: vi.fn() } }))

enableAutoUnmount(afterEach)

const response = (name = 'NVIDIA', number = 0): Page<DiversificationHoldingDto> => ({
  content: [{ name, ticker: 'NVDA', percentage: 4, inEtfs: 'VWCE' }],
  number,
  size: 100,
  totalElements: 201,
  totalPages: 3,
})

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
    vi.mocked(diversificationService.searchHoldings).mockReset().mockResolvedValue(response())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('fetches the first unfiltered page when the tab opens', async () => {
    const { search } = setup()
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenCalledWith(firstPage())
    expect(search.data.value?.totalElements).toBe(201)
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

  it('resets a new query and a cleared query to the first page', async () => {
    const { search, type } = setup()
    await flushPromises()
    search.page.value = 1
    await flushPromises()
    await type('nestle')
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith(firstPage('nestle'))
    search.page.value = 1
    await flushPromises()
    await type('')
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith(firstPage())
  })

  it('keeps the previous rows while the next page is pending', async () => {
    const { search } = setup()
    await flushPromises()
    const next = deferred()
    vi.mocked(diversificationService.searchHoldings).mockReturnValueOnce(next.promise)
    search.page.value = 1
    await flushPromises()
    expect(search.data.value?.content[0].name).toBe('NVIDIA')
    expect(search.isSearching.value).toBe(true)
    next.resolve(response('Nestlé', 1))
    await flushPromises()
    expect(search.data.value?.content[0].name).toBe('Nestlé')
    expect(search.isSearching.value).toBe(false)
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
    expect(search.data.value?.content[0].name).toBe('Nestlé')
    earlier.resolve(response('NVIDIA'))
    await flushPromises()
    expect(search.data.value?.content[0].name).toBe('Nestlé')
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
    search.page.value = 1
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
    expect(search.data.value?.content[0].name).toBe('NVIDIA')
  })
})
