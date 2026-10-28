import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { radioService } from '../../services/api'
import { deferred } from '../../tests/test-utils'
import RadioSearch from './radio-search.vue'
import { nothingFound, radioSearch } from './radio-fixture'

vi.mock('../../services/api', () => ({ radioService: { search: vi.fn() } }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

let reachEnd = () => {}

const observe = (inView = false) =>
  vi.stubGlobal(
    'IntersectionObserver',
    vi.fn(function (notify: (entries: { isIntersecting: boolean }[]) => void) {
      return {
        observe: () => {
          reachEnd = () => notify([{ isIntersecting: true }])
          if (inView) reachEnd()
        },
        disconnect: vi.fn(),
      }
    })
  )

const page = { template: '<p />' }

const secondPage = () =>
  radioSearch({ groups: [{ ...radioSearch().groups[0], name: '2026-10-10' }] })

const open = async (client = new QueryClient()) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/radio', component: page },
      { path: '/radio/:name', component: page },
    ],
  })
  await router.push('/radio?q=valimis')
  const wrapper = mount(RadioSearch, {
    props: { query: 'valimis' },
    global: { plugins: [router, [VueQueryPlugin, { queryClient: client }]] },
  })
  await flushPromises()
  return { router, wrapper }
}

const staleClient = async () => {
  const client = new QueryClient()
  client.setQueryData(['radio', 'search', 'valimis'], {
    pages: [radioSearch({ next: '2026-10-17' })],
    pageParams: [null],
  })
  await client.invalidateQueries({ queryKey: ['radio', 'search'], refetchType: 'none' })
  return client
}

const lines = async () => (await open()).wrapper.findAll('[data-testid="radio-search-line"]')

const hrefs = (wrapper: VueWrapper) =>
  wrapper.findAll('[data-testid="radio-search-line"] a').map(a => a.attributes('href'))

describe('RadioSearch', () => {
  beforeEach(() => {
    observe()
    vi.mocked(radioService.search).mockReset().mockResolvedValue(radioSearch())
  })

  afterEach(() => vi.unstubAllGlobals())

  it('should mark the words that were found', async () => {
    expect((await lines()).map(line => line.get('mark').text())).toEqual([
      'valimistest',
      'valimised',
    ])
  })

  it('should open a line at its second in the recording', async () => {
    expect((await lines())[1].get('a').attributes('href')).toBe('/radio/2026-10-17?t=2300')
  })

  it('should open a line cut from the episode on the raw recording', async () => {
    expect((await lines())[0].get('a').attributes('href')).toBe('/radio/2026-10-17?t=2150&raw=1')
  })

  it('should show when a line was said by the clock', async () => {
    expect((await lines())[1].get('[data-testid="radio-search-time"]').text()).toBe('12:33')
  })

  it('should say which lines were cut from the episode', async () => {
    const note = 'Cut from the episode as news or ads. Plays the original recording.'
    expect((await lines()).map(line => line.text().includes(note))).toEqual([true, false])
  })

  it('should list the topics before the lines', async () => {
    expect((await open()).wrapper.findAll('h3').map(heading => heading.text())).toEqual([
      'Topics',
      'Said on air',
    ])
  })

  it('should open a topic at its start in the recording', async () => {
    const topic = (await open()).wrapper.get('[data-testid="radio-search-topic"] a')
    expect(topic.attributes('href')).toBe('/radio/2026-10-17?t=2700')
  })

  it('should count the lines and the recordings they were said in', async () => {
    expect((await open()).wrapper.get('[role="status"]').text()).toBe('2 lines in 1 recording')
  })

  it('should say it is searching while the next search replaces the results', async () => {
    const { wrapper } = await open()
    vi.mocked(radioService.search).mockReturnValue(new Promise(() => {}))
    await wrapper.setProps({ query: 'eelarve' })
    await flushPromises()
    expect(wrapper.get('[role="status"]').text()).toBe('Searching…')
  })

  it('should say when no line has every word', async () => {
    vi.mocked(radioService.search).mockResolvedValue(radioSearch({ partial: true }))
    expect((await open()).wrapper.text()).toContain(
      'No line has every word, so these have most of them.'
    )
  })

  it('should offer the close words that were said when nobody said the word', async () => {
    vi.mocked(radioService.search).mockResolvedValue(nothingFound(['valised', 'valitsus']))
    const links = (await open()).wrapper.findAll('[data-testid="radio-search-close"]')
    expect(links.map(link => link.attributes('href'))).toEqual([
      '/radio?q=valised',
      '/radio?q=valitsus',
    ])
  })

  it('should introduce the close words as words that were said', async () => {
    vi.mocked(radioService.search).mockResolvedValue(nothingFound(['valised', 'valitsus']))
    const tip = (await open()).wrapper
      .get('[data-testid="radio-search-close"]')
      .element.closest('p')!
    expect(tip.textContent!.replace(/\s+/g, ' ').trim()).toBe(
      'Close words that were said: valised, valitsus'
    )
  })

  it('should say nobody said the words when no line has them', async () => {
    vi.mocked(radioService.search).mockResolvedValue(nothingFound())
    expect((await open()).wrapper.get('[role="status"]').text()).toBe('Nobody said “valimis”')
  })

  it('should say the words are too short or too common to search instead of that nobody said them', async () => {
    vi.mocked(radioService.search).mockResolvedValue({ ...nothingFound(), searchable: false })
    expect((await open()).wrapper.get('[role="status"]').text()).toBe(
      'These words are too short or too common to search'
    )
  })

  it('should load more lines when the end of the list comes into view', async () => {
    vi.mocked(radioService.search)
      .mockResolvedValueOnce(radioSearch({ next: '2026-10-17' }))
      .mockResolvedValueOnce(secondPage())
    const { wrapper } = await open()
    reachEnd()
    await flushPromises()
    expect(hrefs(wrapper)).toEqual([
      '/radio/2026-10-17?t=2150&raw=1',
      '/radio/2026-10-17?t=2300',
      '/radio/2026-10-10?t=2150&raw=1',
      '/radio/2026-10-10?t=2300',
    ])
  })

  it('should show a recording once when the next page lists it again', async () => {
    vi.mocked(radioService.search)
      .mockResolvedValueOnce(radioSearch({ next: '2026-10-17' }))
      .mockResolvedValueOnce(radioSearch())
    const { wrapper } = await open()
    reachEnd()
    await flushPromises()
    expect(hrefs(wrapper)).toEqual(['/radio/2026-10-17?t=2150&raw=1', '/radio/2026-10-17?t=2300'])
  })

  it('should keep loading while the end of the list stays in view after a page of recordings already shown', async () => {
    observe(true)
    vi.mocked(radioService.search)
      .mockResolvedValueOnce(radioSearch({ next: '2026-10-17' }))
      .mockResolvedValueOnce(radioSearch({ next: '2026-10-17' }))
      .mockResolvedValueOnce(secondPage())
    const { wrapper } = await open()
    expect([vi.mocked(radioService.search).mock.calls.length, hrefs(wrapper)]).toEqual([
      3,
      [
        '/radio/2026-10-17?t=2150&raw=1',
        '/radio/2026-10-17?t=2300',
        '/radio/2026-10-10?t=2150&raw=1',
        '/radio/2026-10-10?t=2300',
      ],
    ])
  })

  it('should load more lines when the end of the list came into view while the results refreshed', async () => {
    observe(true)
    const refresh = deferred<ReturnType<typeof radioSearch>>()
    vi.mocked(radioService.search)
      .mockReturnValueOnce(refresh.promise)
      .mockResolvedValueOnce(secondPage())
    await open(await staleClient())
    refresh.resolve(radioSearch({ next: '2026-10-17' }))
    await flushPromises()
    expect(vi.mocked(radioService.search).mock.calls).toEqual([
      ['valimis', null],
      ['valimis', '2026-10-17'],
    ])
  })

  it('should warn that the results could not be refreshed while it keeps showing them', async () => {
    const client = new QueryClient()
    const { wrapper } = await open(client)
    vi.mocked(radioService.search).mockRejectedValue(new Error('Search failed'))
    await client.invalidateQueries({ queryKey: ['radio', 'search'] })
    await flushPromises()
    expect([
      wrapper.get('[role="alert"]').text(),
      wrapper.findAll('[data-testid="radio-search-line"]').length,
    ]).toEqual(['Couldn’t refresh the results', 2])
  })

  it('should stop loading more lines when the results could not be refreshed', async () => {
    observe(true)
    vi.mocked(radioService.search).mockRejectedValue(new Error('Search failed'))
    await open(await staleClient())
    expect(vi.mocked(radioService.search).mock.calls).toEqual([['valimis', null]])
  })

  it('should refresh every page of a search with its own words when the words change meanwhile', async () => {
    const refresh = deferred<ReturnType<typeof radioSearch>>()
    vi.mocked(radioService.search)
      .mockResolvedValueOnce(radioSearch({ next: '2026-10-17' }))
      .mockResolvedValueOnce(secondPage())
      .mockReturnValueOnce(refresh.promise)
    const client = new QueryClient()
    const { wrapper } = await open(client)
    reachEnd()
    await flushPromises()
    void client.invalidateQueries({ queryKey: ['radio', 'search'] })
    await wrapper.setProps({ query: 'eelarve' })
    refresh.resolve(radioSearch({ next: '2026-10-17' }))
    await flushPromises()
    expect(vi.mocked(radioService.search).mock.calls).toEqual([
      ['valimis', null],
      ['valimis', '2026-10-17'],
      ['valimis', null],
      ['eelarve', null],
      ['valimis', '2026-10-17'],
    ])
  })

  it('should show the pages it loaded again without searching when it comes back', async () => {
    vi.mocked(radioService.search)
      .mockResolvedValueOnce(radioSearch({ next: '2026-10-17' }))
      .mockResolvedValueOnce(secondPage())
    const client = new QueryClient()
    const first = await open(client)
    reachEnd()
    await flushPromises()
    first.wrapper.unmount()
    await new Promise(resolve => setTimeout(resolve, 20))
    const { wrapper } = await open(client)
    expect([
      vi.mocked(radioService.search).mock.calls.length,
      wrapper.findAll('[data-testid="radio-search-line"]').length,
    ]).toEqual([2, 4])
  })

  it('should refresh a stale search when it comes back after loading more lines', async () => {
    vi.mocked(radioService.search)
      .mockResolvedValueOnce(radioSearch({ next: '2026-10-17' }))
      .mockResolvedValueOnce(secondPage())
    const client = new QueryClient()
    const first = await open(client)
    await client.invalidateQueries({ queryKey: ['radio', 'search'], refetchType: 'none' })
    reachEnd()
    await flushPromises()
    first.wrapper.unmount()
    await new Promise(resolve => setTimeout(resolve, 20))
    await open(client)
    expect(vi.mocked(radioService.search).mock.calls).toEqual([
      ['valimis', null],
      ['valimis', '2026-10-17'],
      ['valimis', null],
    ])
  })

  it('should show the refreshed lines when a saved decision invalidates the search on screen', async () => {
    const client = new QueryClient()
    const { wrapper } = await open(client)
    const [cut, kept] = radioSearch().groups[0].lines
    vi.mocked(radioService.search).mockResolvedValue(
      radioSearch({
        groups: [{ ...radioSearch().groups[0], lines: [{ ...cut, kept: true }, kept] }],
      })
    )
    await client.invalidateQueries({ queryKey: ['radio', 'search'] })
    await flushPromises()
    expect(hrefs(wrapper)).toEqual(['/radio/2026-10-17?t=2150', '/radio/2026-10-17?t=2300'])
  })

  it('should try again to load more lines after a failure', async () => {
    vi.mocked(radioService.search)
      .mockResolvedValueOnce(radioSearch({ next: '2026-10-17' }))
      .mockRejectedValueOnce(new Error('Search failed'))
      .mockResolvedValueOnce(secondPage())
    const { wrapper } = await open()
    reachEnd()
    await flushPromises()
    await wrapper.get('[role="alert"] + button').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="radio-search-line"]')).toHaveLength(4)
  })

  it('should not warn that the results could not be refreshed while they refresh after more lines failed to load', async () => {
    vi.mocked(radioService.search)
      .mockResolvedValueOnce(radioSearch({ next: '2026-10-17' }))
      .mockRejectedValueOnce(new Error('Search failed'))
      .mockReturnValue(new Promise(() => {}))
    const client = new QueryClient()
    const { wrapper } = await open(client)
    reachEnd()
    await flushPromises()
    void client.invalidateQueries({ queryKey: ['radio', 'search'] })
    await flushPromises()
    expect(wrapper.findAll('[role="alert"]')).toHaveLength(0)
  })

  it('should search again when Retry is clicked after the first search failed', async () => {
    vi.mocked(radioService.search).mockRejectedValueOnce(new Error('Search failed'))
    const { wrapper } = await open()
    await wrapper.get('[role="status"] + button').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-testid="radio-search-line"]')).toHaveLength(2)
  })
})
