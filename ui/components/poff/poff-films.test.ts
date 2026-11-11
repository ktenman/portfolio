import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { ApiError } from '../../models/api-error'
import { poffService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import PoffFilms from './poff-films.vue'
import { poffCatalogue } from './poff-fixture'

vi.mock('../../services/api', () => ({
  poffService: { catalogue: vi.fn(), stillUrl: (key: string) => `/still/${key}` },
}))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const mount = async (query: Record<string, string> = {}) => {
  const wrapper = renderWithProviders(PoffFilms)
  await wrapper.vm.$router.replace({ query })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mount>>

const titles = (wrapper: Wrapper) =>
  wrapper.findAll('[data-testid="poff-film-title"]').map(t => t.text())

const card = (wrapper: Wrapper, title: string) =>
  wrapper.findAll('[data-testid="poff-film"]').find(c => c.text().includes(title))

const press = async (wrapper: Wrapper, label: string) => {
  await wrapper
    .findAll('button')
    .find(b => b.text() === label)
    ?.trigger('click')
  await flushPromises()
}

describe('PoffFilms', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-10T15:00:00Z'))
    vi.mocked(poffService.catalogue).mockReset().mockResolvedValue(poffCatalogue)
  })

  afterEach(() => vi.useRealTimers())

  it('opens the page of a film from its title', async () => {
    const title = (await mount()).find('[data-testid="poff-film-title"]')
    expect(title.attributes('href')).toBe('/poff/film/american-doctor')
  })

  it('lists the films still showing by rating', async () => {
    expect(titles(await mount())).toEqual([
      'Ameerika arst',
      'Josephine',
      'Armastatu',
      'Kõlakamber',
      'Eluröömu nimel',
      'Tüdruk ilma nimeta',
      'Fotograaf Cannes’is',
      'Hilisõhtu ulme',
      'Shorts Rahvuslik võistlusprogramm 1',
    ])
  })

  it('counts the films still showing', async () => {
    expect((await mount()).get('[data-testid="poff-count"]').text()).toBe('9 films still showing')
  })

  it('puts the rating on the still', async () => {
    expect(
      card(await mount(), 'Ameerika arst')
        ?.get('[data-testid="poff-pill"] [aria-hidden="true"]')
        .text()
    ).toBe('IMDb 8.5')
  })

  it('shows a rating with under 100 votes lighter', async () => {
    expect(
      card(await mount(), 'Eluröömu nimel')
        ?.get('[data-testid="poff-pill"]')
        .classes()
    ).toContain('text-ink-soft')
  })

  it('leaves the still of an unrated film without a pill', async () => {
    expect(
      card(await mount(), 'Hilisõhtu ulme')
        ?.find('[data-testid="poff-pill"]')
        .exists()
    ).toBe(false)
  })

  it('marks a film whose screenings left are sold out', async () => {
    expect(card(await mount(), 'Fotograaf Cannes’is')?.text()).toContain('Sold out')
  })

  it('keeps tonight’s films after pressing Tonight', async () => {
    const wrapper = await mount()
    await press(wrapper, 'Tonight')
    expect(titles(wrapper)).not.toContain('Hilisõhtu ulme')
  })

  it('marks a pressed chip', async () => {
    const wrapper = await mount()
    await press(wrapper, 'Documentary')
    expect(
      wrapper
        .findAll('button')
        .find(b => b.text() === 'Documentary')
        ?.attributes('aria-pressed')
    ).toBe('true')
  })

  it('keeps the documentaries after pressing Documentary', async () => {
    const wrapper = await mount()
    await press(wrapper, 'Documentary')
    expect(titles(wrapper)).toEqual(['Fotograaf Cannes’is'])
  })

  it('keeps the films of a genre chosen from all genres', async () => {
    const wrapper = await mount()
    await press(wrapper, 'All genres')
    await press(wrapper, 'Historical film')
    expect(titles(wrapper)).toEqual(['Eluröömu nimel'])
  })

  it('shows a person filter from the address as a chip that removes it', async () => {
    const wrapper = await mount({ person: 'Tõnu Kark' })
    await press(wrapper, 'Tõnu Kark ×')
    expect(titles(wrapper)).toHaveLength(9)
  })

  it('sorts by title after choosing A–Z', async () => {
    const wrapper = await mount()
    await wrapper.get('[data-testid="poff-sort"]').setValue('az')
    await flushPromises()
    expect(titles(wrapper).slice(0, 2)).toEqual(['Ameerika arst', 'Armastatu'])
  })

  it('says when no film matches the filters', async () => {
    expect((await mount({ genre: 'Comedy' })).text()).toContain('No films match these filters')
  })

  it('says the programme is not imported yet when the catalogue is missing', async () => {
    vi.mocked(poffService.catalogue).mockRejectedValue(new ApiError(404, 'Not Found', ''))
    expect((await mount()).text()).toContain('The PÖFF programme has not been imported yet.')
  })

  it('shows skeleton cards while the catalogue loads', async () => {
    vi.mocked(poffService.catalogue).mockReturnValue(new Promise(() => {}))
    expect((await mount()).find('[aria-busy="true"]').exists()).toBe(true)
  })
})
