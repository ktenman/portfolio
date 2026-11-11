import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { ApiError } from '../../models/api-error'
import { poffService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import PoffFilm from './poff-film.vue'
import { film, screening } from './poff-fixture'

vi.mock('../../services/api', () => ({
  poffService: {
    catalogue: vi.fn(),
    stillUrl: (key: string, width: number) => `/still/${key}?width=${width}`,
  },
}))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const beloved = film(
  1,
  'Armastatu',
  'The Beloved',
  [screening(1, '2026-11-10T18:00:00Z'), screening(2, '2026-11-15T18:00:00Z', { soldOut: true })],
  {
    genres: ['Drama'],
    programmes: ['Best of Festivals'],
    synopsis: ['Sorogoyen returns.', 'When legendary filmmaker Esteban…'],
    credits: [
      { role: 'Director', names: ['Rodrigo Sorogoyen'] },
      { role: 'World sales', names: ['Film Factory'] },
    ],
    stills: ['a', 'b'],
    trailer: 'NZGHgVd1T4A',
    poster: 'p',
    imdb: { id: 'tt27714581', rating: 7.2, votes: 4184 },
  }
)

const mount = async (slug = 'the-beloved') => {
  const wrapper = renderWithProviders(PoffFilm, document.body)
  await wrapper.vm.$router.replace(`/poff/film/${slug}`)
  await flushPromises()
  return wrapper
}

describe('PoffFilm', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-10T15:00:00Z'))
    vi.mocked(poffService.catalogue)
      .mockReset()
      .mockResolvedValue({ syncedAt: '2026-11-10T15:32:00Z', films: [beloved] })
  })

  afterEach(() => vi.useRealTimers())

  it('shows the title of the film', async () => {
    expect((await mount()).find('h2').text()).toBe('Armastatu')
  })

  it('reads the rating out with its votes', async () => {
    expect((await mount()).find('[data-testid="poff-film-rating"]').text()).toContain(
      'IMDb 7.2 from 4,184 votes'
    )
  })

  it('says when a film has no IMDb rating yet', async () => {
    vi.mocked(poffService.catalogue).mockResolvedValue({
      syncedAt: '2026-11-10T15:32:00Z',
      films: [{ ...beloved, imdb: null }],
    })
    expect((await mount()).find('[data-testid="poff-film-rating"]').text()).toBe(
      'No IMDb rating yet'
    )
  })

  it('links a genre to the films of that genre', async () => {
    const chip = (await mount()).findAll('a').find(a => a.text() === 'Drama')
    expect(chip?.attributes('href')).toBe('/poff/films?genre=Drama')
  })

  it('offers tickets for an open screening and marks a sold out one', async () => {
    const rows = (await mount()).findAll('[data-testid="poff-film-screening"]')
    expect(rows.map(r => [r.find('a').exists(), r.text().includes('Sold out')])).toEqual([
      [true, false],
      [false, true],
    ])
  })

  it('loads nothing from YouTube until the trailer is played', async () => {
    expect((await mount()).find('iframe').exists()).toBe(false)
  })

  it('plays the trailer from youtube-nocookie', async () => {
    const wrapper = await mount()
    await wrapper.find('[aria-label="Play the trailer of Armastatu"]').trigger('click')
    expect(wrapper.find('iframe').attributes('src')).toBe(
      'https://www.youtube-nocookie.com/embed/NZGHgVd1T4A?autoplay=1'
    )
  })

  it('opens a still larger', async () => {
    const wrapper = await mount()
    await wrapper.find('[aria-label="Still 2 of 2 from Armastatu"]').trigger('click')
    expect(wrapper.find('dialog[open] img').attributes('src')).toBe('/still/b?width=1280')
  })

  it('closes a still from its close button', async () => {
    const wrapper = await mount()
    await wrapper.find('[aria-label="Still 1 of 2 from Armastatu"]').trigger('click')
    await wrapper.find('[aria-label="Close the still"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('dialog[open]').exists()).toBe(false)
  })

  it('keeps the minor credits behind all credits', async () => {
    expect((await mount()).find('details').text()).toContain('World sales')
  })

  it('says when the film is not in the programme', async () => {
    expect((await mount('unknown')).text()).toContain('This film is not in the PÖFF programme')
  })

  it('shows a skeleton while the programme loads', async () => {
    vi.mocked(poffService.catalogue).mockReturnValue(new Promise(() => {}))
    expect((await mount()).find('[aria-busy="true"]').exists()).toBe(true)
  })

  it('offers a retry when the programme cannot be loaded', async () => {
    vi.mocked(poffService.catalogue).mockRejectedValue(new ApiError(404, 'Not Found', ''))
    expect((await mount()).find('[data-testid="poff-retry"]').exists()).toBe(true)
  })
})
