import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { RouterView } from 'vue-router'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { poffService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import { film, poffCatalogue } from './poff-fixture'

vi.mock('../../services/api', () => ({
  poffService: { catalogue: vi.fn(), stillUrl: (key: string) => `/still/${key}` },
}))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const App = defineComponent({ render: () => h(RouterView) })

const cast = ['Wilma Ots', 'Wim Pärn', 'Willem Kask', 'Wiljar Tamm', 'Wilhelm Mets']
const films = [
  film(1, 'Metsik hobu üheksa', 'Wild Horse Nine', [], {
    credits: [{ role: 'Cast', names: cast }],
  }),
  ...['Wiki', 'Wind', 'Wisp', 'Wish'].map((title, i) => film(i + 2, title, title, [])),
]

const mount = async (query: Record<string, string> = {}) => {
  vi.mocked(poffService.catalogue).mockResolvedValue({ ...poffCatalogue, films })
  const wrapper = renderWithProviders(App, document.body)
  await wrapper.vm.$router.replace({ path: '/poff', query })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mount>>

const field = (wrapper: Wrapper) => wrapper.find<HTMLInputElement>('[data-testid="poff-search"]')

const type = async (wrapper: Wrapper, text: string) => {
  await field(wrapper).setValue(text)
  await flushPromises()
}

const people = (wrapper: Wrapper) =>
  wrapper.findAll('[data-testid="poff-search-person"]').map(p => p.text())

const press = async (wrapper: Wrapper, label: string) => {
  await wrapper
    .findAll('button')
    .find(b => b.text().startsWith(label))
    ?.trigger('click')
  await flushPromises()
}

describe('PoffSearch', () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ['Date'] }))

  afterEach(() => vi.useRealTimers())

  it('keeps the query in the address', async () => {
    const wrapper = await mount()
    await type(wrapper, 'wil')
    expect(wrapper.vm.$route.query).toEqual({ q: 'wil' })
  })

  it('keeps the view under one letter', async () => {
    const wrapper = await mount({ q: 'w' })
    expect(wrapper.find('[aria-label="PÖFF views"]').exists()).toBe(true)
  })

  it('replaces the view with results from two letters', async () => {
    const wrapper = await mount({ q: 'wi' })
    expect(wrapper.find('[aria-label="PÖFF views"]').exists()).toBe(false)
  })

  it('counts the results on the chips', async () => {
    const wrapper = await mount({ q: 'wi' })
    expect(wrapper.findAll('[aria-label="Results"] button').map(b => b.text())).toEqual([
      'All 10',
      'Films 5',
      'People 5',
    ])
  })

  it('shows the first three people under all on a phone', async () => {
    expect(people(await mount({ q: 'wi' }))).toHaveLength(3)
  })

  it('folds the rest of the people', async () => {
    const more = (await mount({ q: 'wi' })).findAll('[data-testid="poff-search-more"]')
    expect(more.map(m => m.text())).toEqual(['2 more films', '2 more people'])
  })

  it('shows every person under people', async () => {
    const wrapper = await mount({ q: 'wi' })
    await press(wrapper, 'People')
    expect(people(wrapper)).toHaveLength(5)
  })

  it('shows only films under films', async () => {
    const wrapper = await mount({ q: 'wi' })
    await press(wrapper, 'Films')
    expect(wrapper.find('[data-testid="poff-search-people"]').exists()).toBe(false)
  })

  it('opens films filtered to a person', async () => {
    const link = (await mount({ q: 'wilma' })).find('[data-testid="poff-search-person"]')
    expect(link.attributes('href')).toBe('/poff/films?person=Wilma+Ots&showing=0')
  })

  it('shows the films of a person opened from the results', async () => {
    const wrapper = await mount({ q: 'wilma' })
    await wrapper.find('[data-testid="poff-search-person"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.find('[data-testid="poff-sort"]').exists()).toBe(true))
  })

  it('shows films after the timetable', async () => {
    const wrapper = await mount()
    await wrapper.vm.$router.push('/poff/films')
    await vi.waitFor(() => expect(wrapper.find('[data-testid="poff-sort"]').exists()).toBe(true))
  })

  it('opens the page of a film', async () => {
    const link = (await mount({ q: 'horse' })).find('[data-testid="poff-search-film"]')
    expect(link.attributes('href')).toBe('/poff/film/wild-horse-nine')
  })

  it('says when nothing matches', async () => {
    expect((await mount({ q: 'xyz' })).text()).toContain('Nothing matches “xyz”')
  })

  it('closes the search on Escape', async () => {
    const wrapper = await mount({ q: 'wil' })
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(wrapper.vm.$route.query).toEqual({})
  })

  it('closes the search from the clear button', async () => {
    const wrapper = await mount({ q: 'wil' })
    await wrapper.find('[aria-label="Clear the search"]').trigger('click')
    await flushPromises()
    expect(wrapper.vm.$route.query).toEqual({})
  })

  it('focuses the field on a slash', async () => {
    const wrapper = await mount()
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true }))
    expect(document.activeElement).toBe(field(wrapper).element)
  })

  it('keeps the open folds after a visit to a film', async () => {
    const wrapper = await mount({ q: 'wi' })
    await press(wrapper, '2 more people')
    await wrapper.vm.$router.push('/poff/film/wild-horse-nine')
    await flushPromises()
    wrapper.vm.$router.back()
    await vi.waitFor(() => expect(people(wrapper)).toHaveLength(5))
  })

  it('opens the search from the button on a film page', async () => {
    const wrapper = await mount()
    await wrapper.vm.$router.replace('/poff/film/wild-horse-nine')
    await flushPromises()
    await wrapper.find('[data-testid="poff-search-open"]').trigger('click')
    await flushPromises()
    expect(document.activeElement).toBe(field(wrapper).element)
  })
})
