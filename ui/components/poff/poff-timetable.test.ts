import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, shallowMount } from '@vue/test-utils'
import { ApiError } from '../../models/api-error'
import { poffService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import PoffRating from './poff-rating.vue'
import PoffTimetable from './poff-timetable.vue'
import { poffCatalogue } from './poff-fixture'

vi.mock('../../services/api', () => ({ poffService: { catalogue: vi.fn() } }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const mount = async () => {
  const wrapper = renderWithProviders(PoffTimetable)
  await flushPromises()
  return wrapper
}

const failed = async (error: Error) => {
  vi.useFakeTimers({ toFake: ['Date', 'setTimeout'] })
  vi.mocked(poffService.catalogue).mockRejectedValue(error)
  const wrapper = renderWithProviders(PoffTimetable)
  await vi.advanceTimersByTimeAsync(10_000)
  return wrapper
}

const attached = async () => {
  vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
  const wrapper = renderWithProviders(PoffTimetable, document.body)
  await flushPromises()
  return wrapper
}

const focusNext = (wrapper: Awaited<ReturnType<typeof mount>>) => {
  const link = wrapper
    .findAll('[data-testid="poff-title"]')
    .find(t => t.text() === 'Fotograaf Cannes’is')
  ;(link?.element as HTMLElement).focus()
  return link?.element
}

const titles = (wrapper: Awaited<ReturnType<typeof mount>>) =>
  wrapper.findAll('[data-testid="poff-title"]').map(t => t.text())

const choose = async (wrapper: Awaited<ReturnType<typeof mount>>, label: string) => {
  await wrapper
    .findAll('button')
    .find(b => b.attributes('aria-label') === label || b.text() === label)
    ?.trigger('click')
  await flushPromises()
}

describe('PoffTimetable', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-10T16:10:00Z'))
    vi.mocked(poffService.catalogue).mockReset().mockResolvedValue(poffCatalogue)
  })

  afterEach(() => vi.useRealTimers())

  it('opens on today with the Tallinn screenings', async () => {
    expect((await mount()).find('[data-testid="poff-band"]').text()).toBe(
      'Tue 10 Nov · 7 screenings in Tallinn'
    )
  })

  it('folds started screenings under earlier today', async () => {
    expect(titles(await mount())).toEqual([
      'Fotograaf Cannes’is',
      'Eluröömu nimel',
      'Armastatu',
      'Josephine',
    ])
  })

  it('shows the started screenings after opening earlier today', async () => {
    const wrapper = await mount()
    await wrapper.find('[data-testid="poff-earlier"]').trigger('click')
    expect(titles(wrapper)).toHaveLength(7)
  })

  it('shows only Tartu after choosing Tartu', async () => {
    const wrapper = await mount()
    await choose(wrapper, 'Tartu')
    expect(titles(wrapper)).toEqual(['Tüdruk ilma nimeta'])
  })

  it('remembers the chosen city', async () => {
    await choose(await mount(), 'Tartu')
    expect(localStorage.getItem('poff-city')).toBe('Tartu')
  })

  it('opens on the remembered city', async () => {
    localStorage.setItem('poff-city', 'Tartu')
    expect(titles(await mount())).toEqual(['Tüdruk ilma nimeta'])
  })

  it('says when a day has no screenings', async () => {
    const wrapper = await mount()
    await choose(wrapper, 'Tartu')
    await choose(wrapper, 'Monday, 9 November 2026')
    expect(wrapper.text()).toContain('No screenings in Tartu on Mon 9 Nov')
  })

  it('moves focus to earlier today when the focused screening starts', async () => {
    const wrapper = await attached()
    focusNext(wrapper)
    await vi.advanceTimersByTimeAsync(51 * 60_000)
    expect(document.activeElement?.getAttribute('data-testid')).toBe('poff-earlier')
  })

  it('keeps focus on a screening that starts while earlier today is open', async () => {
    const wrapper = await attached()
    await wrapper.find('[data-testid="poff-earlier"]').trigger('click')
    const link = focusNext(wrapper)
    await vi.advanceTimersByTimeAsync(51 * 60_000)
    expect(document.activeElement).toBe(link)
  })

  it('follows today again after choosing today', async () => {
    const wrapper = await mount()
    await choose(wrapper, 'Wednesday, 11 November 2026')
    await choose(wrapper, 'Tuesday, 10 November 2026')
    expect(wrapper.vm.$route.query).toEqual({})
  })

  it('marks today among the festival days', async () => {
    expect((await mount()).get('[aria-current="date"]').attributes('aria-label')).toBe(
      'Tuesday, 10 November 2026'
    )
  })

  it('dims the days before today', async () => {
    const dimmed = (await mount())
      .findAll('[aria-label="Festival day"] button')
      .filter(day => day.classes('text-ink-soft'))
      .map(day => day.attributes('aria-label'))
    expect(dimmed).toEqual([
      'Friday, 6 November 2026',
      'Saturday, 7 November 2026',
      'Sunday, 8 November 2026',
      'Monday, 9 November 2026',
    ])
  })

  it('offers a retry when the catalogue fails', async () => {
    const wrapper = await failed(new ApiError(502, 'Bad Gateway', ''))
    vi.mocked(poffService.catalogue).mockResolvedValue(poffCatalogue)
    await wrapper.find('[data-testid="poff-retry"]').trigger('click')
    await vi.advanceTimersByTimeAsync(0)
    expect(wrapper.find('[data-testid="poff-band"]').text()).toBe(
      'Tue 10 Nov · 7 screenings in Tallinn'
    )
  })

  it('says the programme is not imported yet when the catalogue is missing', async () => {
    vi.mocked(poffService.catalogue).mockRejectedValue(new ApiError(404, 'Not Found', ''))
    expect((await mount()).text()).toContain('The PÖFF programme has not been imported yet.')
  })

  it('says the programme could not be loaded when the catalogue fails', async () => {
    expect((await failed(new ApiError(502, 'Bad Gateway', ''))).text()).toContain(
      'The PÖFF programme could not be loaded.'
    )
  })

  it('marks a sold out screening', async () => {
    const row = (await mount()).findAll('[data-testid="poff-row"]')[0]
    expect(row?.text()).toContain('Sold out')
  })

  it('names a rating with its votes', async () => {
    expect((await mount()).text()).toContain('IMDb 7.2 from 4,184 votes')
  })

  it('names a missing rating for a film the API sends without one', () => {
    expect(shallowMount(PoffRating, { props: {} }).text()).toContain('No IMDb rating')
  })

  it('links tickets to the screening on poff.ee', async () => {
    const link = (await mount()).find('a[href$="#screening_15"]')
    expect(link.attributes('target')).toBe('_blank')
  })
})
