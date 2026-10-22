import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioStation from './radio-station.vue'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { RecorderState } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  radioService: { station: vi.fn(), liveUrl: '/api/radio/live' },
}))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const SATURDAY_NOON = Date.UTC(2026, 9, 24, 9, 0, 0) / 1000

const mount = async (station: Awaited<ReturnType<typeof radioService.station>>) => {
  vi.mocked(radioService.station).mockResolvedValue(station)
  const wrapper = renderWithProviders(RadioStation)
  await flushPromises()
  return wrapper
}

const idle = {
  onAir: null,
  next: null,
  capture: null,
  freeBytes: 364_000_000_000,
  hours: 3159,
  recorder: RecorderState.WAITING,
  last: null,
  format: null,
}

const broadcast = {
  name: 'PaarisMäng',
  start: SATURDAY_NOON,
  end: SATURDAY_NOON + 3600,
  recorded: false,
}

describe('RadioStation', () => {
  it('should say no programme is chosen when nothing is planned', async () => {
    expect((await mount(idle)).text()).toContain('None selected')
  })

  it('should name the next capture with the day and time its airing starts', async () => {
    const capture = {
      name: '2026-10-24-1200-keskpaevatund',
      programmes: ['Keskpäevatund'],
      start: SATURDAY_NOON - 30,
      end: SATURDAY_NOON + 3630,
      slot: { start: SATURDAY_NOON, end: SATURDAY_NOON + 3600 },
    }
    expect((await mount({ ...idle, capture })).text()).toContain('Keskpäevatund, Sat 24.10 12:00')
  })

  it('should show the recorder waiting between recordings', async () => {
    expect((await mount(idle)).text()).toContain('RecorderWaiting')
  })

  it('should show the recorder off when the worker is not seen', async () => {
    expect((await mount({ ...idle, recorder: RecorderState.OFF })).text()).toContain('RecorderOff')
  })

  it('should name the last recording with the day and time it ended', async () => {
    const last = { programme: 'Keskpäevatund', end: SATURDAY_NOON + 3900 }
    expect((await mount({ ...idle, last })).text()).toContain('LastKeskpäevatund, Sat 24.10 13:05')
  })

  it('should give the format of the last recording', async () => {
    const format = { codec: 'mp3', bitRate: 256_000 }
    expect((await mount({ ...idle, format })).text()).toContain('FormatMP3 256 kb/s')
  })

  it('should show a dash before the first recording', async () => {
    expect((await mount(idle)).text()).toContain('Last—')
  })

  it('should say how many hours of radio still fit', async () => {
    expect((await mount(idle)).text()).toContain('≈ 3,159 h')
  })

  it('should show no free space without a recordings directory', async () => {
    expect((await mount({ ...idle, freeBytes: null, hours: null })).text()).toContain('Free space—')
  })

  it('should play the station live through the server', async () => {
    const audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    const player = useRadioPlayer()
    player.attach(audio)
    const onAir = {
      name: 'PaarisMäng',
      start: SATURDAY_NOON,
      end: SATURDAY_NOON + 3600,
      recorded: false,
    }
    const wrapper = await mount({ ...idle, onAir })
    await wrapper.find('button').trigger('click')
    const src = audio.src
    player.detach()
    expect(src).toContain('/api/radio/live')
  })

  it('should stop the live station from the card', async () => {
    const audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    vi.spyOn(audio, 'pause').mockImplementation(() => {})
    vi.spyOn(audio, 'load').mockImplementation(() => {})
    const player = useRadioPlayer()
    player.attach(audio)
    const onAir = {
      name: 'PaarisMäng',
      start: SATURDAY_NOON,
      end: SATURDAY_NOON + 3600,
      recorded: false,
    }
    const wrapper = await mount({ ...idle, onAir })
    await wrapper.find('button').trigger('click')
    audio.dispatchEvent(new Event('play'))
    await wrapper.find('button').trigger('click')
    const live = audio.hasAttribute('src')
    player.detach()
    expect(live).toBe(false)
  })

  it('should show the play icon on the live button', async () => {
    const onAir = {
      name: 'PaarisMäng',
      start: SATURDAY_NOON,
      end: SATURDAY_NOON + 3600,
      recorded: false,
    }
    const wrapper = await mount({ ...idle, onAir })
    expect(wrapper.find('button').find('path').attributes('d')).toBe('M7 4v16l13-8z')
  })

  it("should show the live button as the player's round play button", async () => {
    expect((await mount({ ...idle, onAir: broadcast })).find('button').classes()).toEqual(
      expect.arrayContaining(['rounded-full', 'bg-brass'])
    )
  })

  it('should show only the icon on the live button', async () => {
    expect((await mount({ ...idle, onAir: broadcast })).find('button').text()).toBe('')
  })

  it('should name the live button for screen readers', async () => {
    const button = (await mount({ ...idle, onAir: broadcast })).find('button')
    expect(button.attributes('aria-label')).toBe('Listen live')
  })

  it('should name the live button Pause while the station plays', async () => {
    const player = useRadioPlayer()
    player.live.value = true
    player.playing.value = true
    const label = (await mount({ ...idle, onAir: broadcast }))
      .find('button')
      .attributes('aria-label')
    player.detach()
    expect(label).toBe('Pause')
  })

  it('should say the programme on air is not recorded', async () => {
    const onAir = {
      name: 'Tarkade klubi',
      start: SATURDAY_NOON + 3600,
      end: SATURDAY_NOON + 7200,
      recorded: false,
    }
    expect((await mount({ ...idle, onAir })).text()).toContain('13:00–14:00 · not being recorded')
  })
})
