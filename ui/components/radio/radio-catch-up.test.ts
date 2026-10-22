import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioCatchUp from './radio-catch-up.vue'
import { airing, radioDay, recording } from './radio-fixture'
import { AiringState } from '../../models/generated/domain-models'

const player = vi.hoisted(() => ({
  load: vi.fn(),
  playFrom: vi.fn(),
  listen: vi.fn(),
  toggle: vi.fn(),
  live: { value: false },
  playing: { value: false },
}))

vi.mock('../../services/api', () => ({
  radioService: { day: vi.fn(), record: vi.fn(), recordings: vi.fn() },
}))
vi.mock('../../composables/use-radio-player', () => ({ useRadioPlayer: () => player }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const mount = async () => {
  const wrapper = renderWithProviders(RadioCatchUp)
  await flushPromises()
  return wrapper
}

const row = async (name: string) => {
  const wrapper = await mount()
  return wrapper.findAll('[data-testid="radio-airing"]').find(r => r.text().includes(name))!
}

const button = async (label: string) =>
  (await mount()).findAll('button').find(b => b.attributes('aria-label')?.startsWith(label))!

describe('RadioCatchUp', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    player.live.value = false
    player.playing.value = false
    vi.mocked(radioService.day).mockResolvedValue(radioDay())
    vi.mocked(radioService.recordings).mockResolvedValue([recording()])
    vi.mocked(radioService.record).mockResolvedValue()
  })

  it('should fold the morning that was not recorded into one row', async () => {
    expect((await mount()).text()).toContain('05:00–10:00 · 3 shows, not recorded')
  })

  it('should hide the airings of a folded morning', async () => {
    expect((await mount()).text()).not.toContain('Laupäevane hommik')
  })

  it('should show the airings of a folded morning once it is opened', async () => {
    const wrapper = await mount()
    await wrapper.find('button[aria-expanded="false"]').trigger('click')
    expect(wrapper.text()).toContain('Laupäevane hommik')
  })

  it('should set the night after midnight apart from the day', async () => {
    expect((await mount()).text()).toContain('Overnight into Sunday, 18.10')
  })

  it('should mark today among the days to choose from', async () => {
    const today = (await mount()).get('[aria-current="date"]')
    expect(today.findAll('span').map(s => s.text())).toEqual(['Sa', '17'])
  })

  it('should tell the days apart that share a first letter', async () => {
    const days = (await mount()).get('nav[aria-label="Day"]').findAll('button')
    expect(days.map(d => d.get('span').text())).toEqual(['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'])
  })

  it('should offer every day of the week from Monday', async () => {
    const days = (await mount()).get('nav[aria-label="Day"]').findAll('button')
    expect(days.map(d => d.attributes('aria-label'))).toEqual([
      'Mon 12.10',
      'Tue 13.10',
      'Wed 14.10',
      'Thu 15.10',
      'Fri 16.10',
      'Sat 17.10',
      'Sun 18.10',
    ])
  })

  it("should list Monday's schedule under Sunday's on a phone", async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: true,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }))
    vi.mocked(radioService.day).mockResolvedValue(
      radioDay({ date: '2026-10-18', today: '2026-10-18' })
    )
    await mount()
    vi.unstubAllGlobals()
    expect(radioService.day).toHaveBeenLastCalledWith('2026-10-19')
  })

  it('should leave the day buttons to screens wider than a phone', async () => {
    expect((await mount()).get('nav[aria-label="Day"]').classes()).toContain('max-md:hidden')
  })

  it("should list tomorrow's schedule under today's on a phone", async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: true,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }))
    vi.mocked(radioService.day).mockImplementation(async date =>
      radioDay(date ? { date, from: radioDay().from + 86_400 } : {})
    )
    const wrapper = await mount()
    vi.unstubAllGlobals()
    expect(wrapper.findAll('h3').filter(h => h.text() === 'Schedule')).toHaveLength(2)
  })

  it("should show only the chosen day's schedule on a wider screen", async () => {
    expect((await mount()).findAll('h3').filter(h => h.text() === 'Schedule')).toHaveLength(1)
  })

  it('should ask for the day that is chosen', async () => {
    const wrapper = await mount()
    await wrapper
      .findAll('button')
      .find(b => b.attributes('aria-label') === 'Fri 16.10')!
      .trigger('click')
    await flushPromises()
    expect(radioService.day).toHaveBeenLastCalledWith('2026-10-16')
  })

  it('should say when the recording of an airing was made', async () => {
    expect((await row('12:00')).text()).toContain('Recorded 11:55–13:05')
  })

  it('should say how much of a partly recorded airing is kept and where', async () => {
    expect((await row('10:00')).text()).toContain('Last 5 min recorded in “Keskpäevatund”')
  })

  it('should say a repeat can be recorded in full when its original is partial', async () => {
    expect((await row('00:00')).text()).toContain('Repeat · can be recorded in full')
  })

  it('should say when the original of a repeat was missed', async () => {
    expect((await row('21:00Meretund')).text()).toContain('Repeat · Sat 17.10 09:00 not recorded')
  })

  it('should show a repeat whose original is archived', async () => {
    expect((await row('18:00')).text()).toContain('Original archived')
  })

  it('should play a partly recorded airing from where it is covered', async () => {
    await (await button('Hear the end')).trigger('click')
    expect(player.playFrom).toHaveBeenCalledWith(0)
  })

  it('should play the original of an archived repeat from its start', async () => {
    await (await button('Listen: Keskpäevatund 18:00')).trigger('click')
    expect(player.playFrom).toHaveBeenCalledWith(300)
  })

  it('should listen live to an airing that is on air', async () => {
    await (await button('Listen live')).trigger('click')
    expect(player.listen).toHaveBeenCalled()
  })

  it('should open the recording of an airing it plays', async () => {
    const wrapper = await mount()
    const play = wrapper.findAll('button').find(b => b.text().includes('Hear the end'))!
    await play.trigger('click')
    await vi.waitFor(() =>
      expect(wrapper.vm.$router.currentRoute.value.path).toBe(`/radio/${recording().name}`)
    )
  })

  it('should show the play icon on the live button of an airing on air', async () => {
    expect((await button('Listen live')).find('path').attributes('d')).toBe('M7 4v16l13-8z')
  })

  it('should show the pause icon on the stop button of an airing on air', async () => {
    player.live.value = true
    player.playing.value = true
    expect((await button('Pause')).find('path').attributes('d')).toBe('M6 4h4v16H6z')
  })

  it('should stop the live radio from an airing on air', async () => {
    player.live.value = true
    player.playing.value = true
    await (await button('Pause')).trigger('click')
    expect(player.toggle).toHaveBeenCalled()
  })

  it("should show the player's round play button on a recorded airing", async () => {
    expect((await button('Listen: Keskpäevatund 12:00')).classes()).toEqual(
      expect.arrayContaining(['rounded-full', 'bg-brass'])
    )
  })

  it('should show only the icon on the play button of a recorded airing', async () => {
    expect((await button('Listen: Keskpäevatund 12:00')).text()).toBe('')
  })

  it("should show the player's round play button on an airing on air", async () => {
    expect((await button('Listen live')).classes()).toEqual(
      expect.arrayContaining(['rounded-full', 'bg-brass'])
    )
  })

  it('should ask to record a later airing once', async () => {
    await (await button('Record: Ilmaparandaja')).trigger('click')
    expect(radioService.record).toHaveBeenCalledWith(7, { record: true })
  })

  it('should ask to cancel an airing chosen once', async () => {
    await (await button('Cancel')).trigger('click')
    expect(radioService.record).toHaveBeenCalledWith(17, { record: false })
  })

  it('should not offer to play an airing whose recording is not listed', async () => {
    vi.mocked(radioService.recordings).mockResolvedValue([])
    expect((await button('Listen: Keskpäevatund 12:00')).attributes('disabled')).toBeDefined()
  })

  it('should say the schedule is not loaded for a day without airings', async () => {
    vi.mocked(radioService.day).mockResolvedValue(radioDay({ day: [], night: [] }))
    expect((await mount()).text()).toContain('The schedule for this day has not been loaded yet.')
  })

  it('should give the length of an airing in whole hours', async () => {
    vi.mocked(radioService.day).mockResolvedValue(
      radioDay({ day: [{ missed: false, airings: [airing({ state: AiringState.OPEN })] }] })
    )
    expect((await row('14:00')).text()).toContain('1 h')
  })
})
