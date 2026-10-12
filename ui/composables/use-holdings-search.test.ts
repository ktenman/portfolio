import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { useHoldingsSearch } from './use-holdings-search'
import { diversificationService } from '../services/api'
import type { DiversificationHoldingDto } from '../models/generated/domain-models'
import type { Page } from '../models/page'

vi.mock('../services/api', () => ({ diversificationService: { searchHoldings: vi.fn() } }))

const response = (name = 'NVIDIA', number = 0): Page<DiversificationHoldingDto> => ({
  content: [{ name, ticker: 'NVDA', percentage: 4, inEtfs: 'VWCE' }],
  number,
  size: 100,
  totalElements: 201,
  totalPages: 3,
})

const wrappers: VueWrapper[] = []
const clients: QueryClient[] = []

function setup(enabled = true) {
  const allocations = ref([{ instrumentId: 7, percentage: 100 }])
  const active = ref(enabled)
  let search!: ReturnType<typeof useHoldingsSearch>
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  const wrapper = mount(
    defineComponent({
      setup() {
        search = useHoldingsSearch(allocations, active)
        return () => null
      },
    }),
    { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } }
  )
  wrappers.push(wrapper)
  clients.push(client)
  return { search, allocations, active }
}

describe('useHoldingsSearch', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(diversificationService.searchHoldings).mockReset().mockResolvedValue(response())
  })

  afterEach(() => {
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
    clients.splice(0).forEach(client => client.clear())
    vi.useRealTimers()
  })

  it('fetches the first unfiltered page when the tab opens', async () => {
    const { search } = setup()
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenCalledWith({
      allocations: [{ instrumentId: 7, percentage: 100 }],
      query: '',
      page: 0,
    })
    expect(search.data.value?.totalElements).toBe(201)
  })

  it('waits 300 milliseconds after the last keystroke before requesting a query', async () => {
    const { search } = setup()
    await flushPromises()
    search.query.value = 'nv'
    await nextTick()
    await vi.advanceTimersByTimeAsync(200)
    search.query.value = 'nvda'
    await nextTick()
    await vi.advanceTimersByTimeAsync(299)
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith({
      allocations: [{ instrumentId: 7, percentage: 100 }],
      query: 'nvda',
      page: 0,
    })
  })

  it('keeps the debounce when clearing text before the first query is sent', async () => {
    const { search } = setup()
    await flushPromises()
    search.query.value = 'nv'
    await nextTick()
    await vi.advanceTimersByTimeAsync(200)
    search.query.value = ''
    await nextTick()
    await vi.advanceTimersByTimeAsync(299)
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(2)
  })

  it('resets a new query and a cleared query to the first page', async () => {
    const { search } = setup()
    await flushPromises()
    search.page.value = 1
    await flushPromises()
    search.query.value = 'nestle'
    await nextTick()
    await vi.advanceTimersByTimeAsync(300)
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith({
      allocations: [{ instrumentId: 7, percentage: 100 }],
      query: 'nestle',
      page: 0,
    })
    search.page.value = 1
    await flushPromises()
    search.query.value = ''
    await nextTick()
    await vi.advanceTimersByTimeAsync(300)
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith({
      allocations: [{ instrumentId: 7, percentage: 100 }],
      query: '',
      page: 0,
    })
  })

  it('keeps the previous rows while the next page is pending', async () => {
    const { search } = setup()
    await flushPromises()
    let resolveNext!: (value: Page<DiversificationHoldingDto>) => void
    const next = new Promise<Page<DiversificationHoldingDto>>(resolve => {
      resolveNext = resolve
    })
    vi.mocked(diversificationService.searchHoldings).mockReturnValueOnce(next)
    search.page.value = 1
    await flushPromises()
    expect(search.data.value?.content[0].name).toBe('NVIDIA')
    expect(search.isSearching.value).toBe(true)
    resolveNext(response('Nestlé', 1))
    await flushPromises()
    expect(search.data.value?.content[0].name).toBe('Nestlé')
    expect(search.isSearching.value).toBe(false)
  })

  it('never replaces a newer query result with a slower earlier response', async () => {
    const { search } = setup()
    await flushPromises()
    let resolveEarlier!: (value: Page<DiversificationHoldingDto>) => void
    const earlier = new Promise<Page<DiversificationHoldingDto>>(resolve => {
      resolveEarlier = resolve
    })
    vi.mocked(diversificationService.searchHoldings)
      .mockReturnValueOnce(earlier)
      .mockResolvedValueOnce(response('Nestlé'))
    search.query.value = 'nvda'
    await nextTick()
    await vi.advanceTimersByTimeAsync(300)
    search.query.value = 'nestle'
    await nextTick()
    await vi.advanceTimersByTimeAsync(300)
    expect(search.data.value?.content[0].name).toBe('Nestlé')
    resolveEarlier(response('NVIDIA'))
    await flushPromises()
    expect(search.data.value?.content[0].name).toBe('Nestlé')
  })

  it('makes no request on other tabs even when the query or allocations change', async () => {
    const { search, allocations, active } = setup(false)
    search.query.value = 'nestle'
    allocations.value = [{ instrumentId: 8, percentage: 100 }]
    await nextTick()
    await vi.advanceTimersByTimeAsync(300)
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
    expect(diversificationService.searchHoldings).toHaveBeenLastCalledWith({
      allocations: [{ instrumentId: 8, percentage: 75 }],
      query: '',
      page: 0,
    })
    allocations.value = [{ instrumentId: 8, percentage: 75 }]
    await flushPromises()
    expect(diversificationService.searchHoldings).toHaveBeenCalledTimes(4)
  })

  it('exposes an error and recovers when another query succeeds', async () => {
    vi.mocked(diversificationService.searchHoldings).mockRejectedValueOnce(
      new Error('Search failed')
    )
    const { search } = setup()
    await flushPromises()
    expect(search.error.value).toBe('Search failed')
    search.query.value = 'nvda'
    await nextTick()
    await vi.advanceTimersByTimeAsync(300)
    expect(search.error.value).toBe('')
    expect(search.data.value?.content[0].name).toBe('NVIDIA')
  })
})
