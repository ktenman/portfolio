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

const open = async (still: number) => {
  const wrapper = await mount()
  const thumbs = wrapper.findAll('li > button')
  await thumbs[still - 1]!.trigger('click')
  return wrapper
}

const shown = (wrapper: Awaited<ReturnType<typeof mount>>) =>
  wrapper.findAll('dialog[open] img').pop()?.attributes('src')

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
    const wrapper = await open(2)
    expect(shown(wrapper)).toBe('/still/b?width=1280')
  })

  it('shows the thumbnail under the still while the still loads', async () => {
    const wrapper = await open(2)
    expect(wrapper.findAll('dialog img').map(img => img.attributes('src'))).toEqual([
      '/still/b?width=640',
      '/still/b?width=1280',
    ])
  })

  it('keeps the page from scrolling while a still is open', async () => {
    await open(1)
    await flushPromises()
    expect(document.body.style.overflow).toBe('hidden')
  })

  it('lets the page scroll again once the still is closed', async () => {
    const wrapper = await open(1)
    await wrapper.find('[aria-label="Close the still"]').trigger('click')
    await flushPromises()
    expect(document.body.style.overflow).toBe('')
  })

  it('announces the count of the open still', async () => {
    expect((await open(2)).find('dialog [role="status"]').text()).toBe('2 / 2')
  })

  it('closes a still from its close button', async () => {
    const wrapper = await open(1)
    await wrapper.find('[aria-label="Close the still"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('dialog[open]').exists()).toBe(false)
  })

  it('steps to the next still', async () => {
    const wrapper = await open(1)
    await wrapper.find('[aria-label="Next still"]').trigger('click')
    expect(shown(wrapper)).toBe('/still/b?width=1280')
  })

  it('steps back with the left arrow key', async () => {
    const wrapper = await open(2)
    await wrapper.find('dialog').trigger('keydown', { key: 'ArrowLeft' })
    expect(shown(wrapper)).toBe('/still/a?width=1280')
  })

  it('steps to the next still on a swipe left', async () => {
    const wrapper = await open(1)
    const still = wrapper.find('dialog img')
    await still.trigger('pointerdown', { clientX: 300, buttons: 1 })
    await still.trigger('pointermove', { clientX: 120, buttons: 1 })
    await still.trigger('pointerup', { clientX: 120, buttons: 0 })
    expect(shown(wrapper)).toBe('/still/b?width=1280')
  })

  it('stops at the last still', async () => {
    const wrapper = await open(2)
    await wrapper.find('dialog').trigger('keydown', { key: 'ArrowRight' })
    expect(shown(wrapper)).toBe('/still/b?width=1280')
  })

  it('hides the next button on the last still', async () => {
    expect((await open(2)).find('[aria-label="Next still"]').exists()).toBe(false)
  })

  it('hides the previous button on the first still', async () => {
    expect((await open(1)).find('[aria-label="Previous still"]').exists()).toBe(false)
  })

  it('counts the stills under the open one', async () => {
    expect((await open(2)).find('dialog').text()).toContain('2 / 2')
  })

  it('names the open still in the label of the dialog', async () => {
    expect((await open(2)).find('dialog').attributes('aria-label')).toBe(
      'Still 2 of 2 from Armastatu'
    )
  })

  it('returns focus to the thumbnail of the still last shown', async () => {
    const wrapper = await open(1)
    await wrapper.find('[aria-label="Next still"]').trigger('click')
    await wrapper.find('[aria-label="Close the still"]').trigger('click')
    await flushPromises()
    expect(document.activeElement).toBe(wrapper.findAll('li > button')[1]!.element)
  })

  it('shows no counter for a single still', async () => {
    vi.mocked(poffService.catalogue).mockResolvedValue({
      syncedAt: '2026-11-10T15:32:00Z',
      films: [{ ...beloved, stills: ['a'] }],
    })
    expect((await open(1)).find('dialog').text()).not.toContain('1 / 1')
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
