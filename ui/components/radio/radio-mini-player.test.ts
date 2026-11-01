import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useRadioPlayer, type Version } from '../../composables/use-radio-player'
import type { RecordingSummary } from '../../models/generated/domain-models'
import { RecorderState, Station } from '../../models/generated/domain-models'
import { radioService } from '../../services/api'
import { recording } from './radio-fixture'
import RadioMiniPlayer from './radio-mini-player.vue'

vi.mock('../../services/api', () => ({
  radioService: {
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
    liveUrl: '/api/radio/live',
    position: vi.fn(),
    savePosition: vi.fn(),
    stations: vi.fn(),
  },
}))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const PAGE = { template: '<p />' }
const BROADCAST = { name: 'Tarkade klubi', start: 0, end: 3600, recorded: false }
const SILENT = {
  station: Station.KUKU,
  next: null,
  capture: null,
  freeBytes: null,
  hours: null,
  recorder: RecorderState.WAITING,
  last: null,
  format: null,
}

describe('RadioMiniPlayer', () => {
  const player = useRadioPlayer()
  let audio: HTMLAudioElement

  beforeEach(() => {
    vi.mocked(radioService.position).mockReset().mockResolvedValue({ seconds: 0 })
    vi.mocked(radioService.savePosition).mockReset().mockResolvedValue()
    audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    vi.spyOn(audio, 'load').mockImplementation(() => {})
    player.attach(audio)
  })

  afterEach(() => player.close())

  const open = async (path = '/', onAir: typeof BROADCAST | null = null) => {
    vi.mocked(radioService.stations).mockResolvedValue([{ ...SILENT, onAir }])
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'Portfolio Summary', component: PAGE },
        { path: '/calculator', name: 'Calculator', component: PAGE },
        { path: '/radio', name: 'Radio', component: PAGE },
        { path: '/radio/shows', name: 'RadioShows', component: PAGE },
        { path: '/radio/catch-up', name: 'RadioCatchUp', component: PAGE },
        { path: '/radio/:name', component: PAGE },
      ],
    })
    await router.push(path)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(RadioMiniPlayer, {
      global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
    })
    await flushPromises()
    return wrapper
  }

  const card = (wrapper: VueWrapper) => wrapper.find('[data-testid="radio-mini-player"]')

  const play = async (item: RecordingSummary = recording(), version: Version = 'raw', at = 0) => {
    player.load(item, version)
    await flushPromises()
    audio.currentTime = at
    audio.dispatchEvent(new Event('timeupdate'))
    audio.dispatchEvent(new Event('play'))
  }

  const listen = () => {
    player.listen()
    audio.dispatchEvent(new Event('play'))
  }

  it('should show the programme and the topic at the position', async () => {
    await play(recording(), 'raw', 1000)
    expect(card(await open()).text()).toContain('KeskpäevatundRahvusooperi tüli')
  })

  it('should show the topic of the clean version at its own position', async () => {
    await play(recording(), 'episode', 2000)
    expect(card(await open()).text()).toContain('Mis toimub poliitikas?')
  })

  it('should show the title of a cut inside a cut', async () => {
    await play(recording(), 'raw', 2200)
    expect(card(await open()).text()).toContain('Ad break')
  })

  it('should show the day where no part covers the position', async () => {
    await play(recording({ timeline: [] }), 'raw', 1000)
    expect(card(await open()).text()).toContain('Saturday, 17.10')
  })

  it('should show elapsed and total time around the scrubber', async () => {
    await play(recording(), 'raw', 1000)
    const times = card(await open()).findAll('span')
    expect(times.map(time => time.text())).toEqual(expect.arrayContaining(['16:40', '1:10:00']))
  })

  it('should stay away from the page of the playing recording', async () => {
    await play()
    expect(card(await open('/radio/2026-10-17')).exists()).toBe(false)
  })

  it('should show on the page of another recording until that one loads', async () => {
    await play()
    expect(card(await open('/radio/2026-10-18')).exists()).toBe(true)
  })

  it('should show on the recordings page while a recording plays', async () => {
    await play()
    expect(card(await open('/radio', BROADCAST)).exists()).toBe(true)
  })

  it('should show Kuku and Live for the live stream', async () => {
    listen()
    expect(card(await open()).text()).toContain('KukuLive')
  })

  it('should leave the scrubber off the live stream', async () => {
    listen()
    expect(
      card(await open())
        .find('input')
        .exists()
    ).toBe(false)
  })

  it('should leave speed and skip off the live stream', async () => {
    listen()
    const wrapper = card(await open())
    expect([
      wrapper.find('[data-testid="radio-speed"]').exists(),
      wrapper.find('[aria-label="Back 15 s"]').exists(),
      wrapper.find('[aria-label="Forward 30 s"]').exists(),
    ]).toEqual([false, false, false])
  })

  it('should leave the link to the episode off the live stream', async () => {
    listen()
    expect(
      card(await open())
        .find('[aria-label="Open the episode"]')
        .exists()
    ).toBe(false)
  })

  it.each(['/radio', '/radio/shows'])('should stay away from the On air card at %s', async path => {
    listen()
    expect(card(await open(path, BROADCAST)).exists()).toBe(false)
  })

  it.each(['/radio/catch-up', '/radio?q=kuku'])(
    'should show the live stream at %s, where no On air card is',
    async path => {
      listen()
      expect(card(await open(path, BROADCAST)).exists()).toBe(true)
    }
  )

  it('should show the live stream while nothing is on air', async () => {
    listen()
    expect(card(await open('/radio', null)).exists()).toBe(true)
  })

  it('should link to the episode', async () => {
    await play()
    const link = card(await open()).find('[aria-label="Open the episode"]')
    expect(link.attributes('href')).toBe('/radio/2026-10-17')
  })

  it('should save the position when closed', async () => {
    vi.mocked(radioService.position).mockResolvedValue({ seconds: 600 })
    await play(recording(), 'raw', 604)
    await card(await open())
      .find('[aria-label="Stop and close"]')
      .trigger('click')
    expect(vi.mocked(radioService.savePosition).mock.calls).toEqual([
      ['2026-10-17', { seconds: 604 }],
    ])
  })

  it('should clear the player when closed', async () => {
    await play()
    await card(await open())
      .find('[aria-label="Stop and close"]')
      .trigger('click')
    expect([player.started.value, player.recording.value]).toEqual([false, null])
  })

  it('should leave out the volume slider', async () => {
    await play()
    expect(
      card(await open())
        .find('[aria-label="Volume"]')
        .exists()
    ).toBe(false)
  })

  it.each([
    '[data-testid="radio-speed"]',
    '[aria-label="Back 15 s"]',
    '[data-testid="radio-play"]',
    '[aria-label="Forward 30 s"]',
    '[aria-label="Mute"]',
    '[aria-label="Open the episode"]',
    '[aria-label="Stop and close"]',
  ])('should give %s a 44 px target', async selector => {
    await play()
    expect(
      card(await open())
        .find(selector)
        .classes()
    ).toContain('size-11')
  })

  it('should rise above the Reset bar on the calculator', async () => {
    await play()
    expect(card(await open('/calculator')).classes()).toContain('max-md:bottom-22')
  })

  it('should stay at the bottom of every other page', async () => {
    await play()
    expect(card(await open()).classes()).not.toContain('max-md:bottom-22')
  })
})
