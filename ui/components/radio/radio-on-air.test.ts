import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h } from 'vue'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioOnAir from './radio-on-air.vue'
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

const idle = {
  onAir: null,
  next: null,
  capture: null,
  freeBytes: null,
  hours: null,
  recorder: RecorderState.WAITING,
  last: null,
  format: null,
}

const broadcast = {
  name: 'Tarkade klubi',
  start: SATURDAY_NOON,
  end: SATURDAY_NOON + 3600,
  recorded: false,
}

const next = {
  name: 'Ilmaparandaja',
  start: SATURDAY_NOON + 3600,
  end: SATURDAY_NOON + 7200,
  recorded: false,
}

const mount = async (
  station: Awaited<ReturnType<typeof radioService.station>>,
  props: { aside?: boolean } = {}
) => {
  vi.mocked(radioService.station).mockResolvedValue(station)
  const wrapper = renderWithProviders({ render: () => h(RadioOnAir, props) })
  await flushPromises()
  return wrapper
}

describe('RadioOnAir', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime((SATURDAY_NOON + 37 * 60) * 1000)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should show nothing while no programme is on air', async () => {
    expect((await mount(idle)).find('section').exists()).toBe(false)
  })

  it('should title the programme on air live', async () => {
    expect((await mount({ ...idle, onAir: broadcast })).find('section').text()).toContain(
      'On airLive'
    )
  })

  it('should show the hours of the programme on air', async () => {
    expect((await mount({ ...idle, onAir: broadcast })).text()).toContain(
      '12:00–13:00 · not recorded'
    )
  })

  it('should say the programme on air is being recorded', async () => {
    const onAir = { ...broadcast, recorded: true }
    expect((await mount({ ...idle, onAir })).text()).toContain('12:00–13:00 · recording')
  })

  it('should lead the programme on air with the live button', async () => {
    const button = (await mount({ ...idle, onAir: broadcast })).find('button').element
    expect(button.parentElement?.firstElementChild).toBe(button)
  })

  it('should fill the progress line with the share of the programme that has aired', async () => {
    const onAir = { ...broadcast, end: SATURDAY_NOON + 74 * 60 }
    const line = (await mount({ ...idle, onAir })).find('[data-testid="radio-on-air-progress"]')
    expect(line.attributes('style')).toContain('width: 50%')
  })

  it('should fill the progress line no further than the end of the programme', async () => {
    const onAir = { ...broadcast, end: SATURDAY_NOON + 600 }
    const line = (await mount({ ...idle, onAir })).find('[data-testid="radio-on-air-progress"]')
    expect(line.attributes('style')).toContain('width: 100%')
  })

  it('should say in how many minutes the next programme starts', async () => {
    expect((await mount({ ...idle, onAir: broadcast, next })).text()).toContain(
      'Ilmaparandaja in 23 min'
    )
  })

  it('should show no next row without a next programme', async () => {
    expect((await mount({ ...idle, onAir: broadcast })).text()).not.toContain('Next')
  })

  it('should play the station live through the server', async () => {
    const audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    const player = useRadioPlayer()
    player.attach(audio)
    const wrapper = await mount({ ...idle, onAir: broadcast })
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
    const wrapper = await mount({ ...idle, onAir: broadcast })
    await wrapper.find('button').trigger('click')
    audio.dispatchEvent(new Event('play'))
    await wrapper.find('button').trigger('click')
    const live = audio.hasAttribute('src')
    player.detach()
    expect(live).toBe(false)
  })

  it('should show the play icon on the live button', async () => {
    const wrapper = await mount({ ...idle, onAir: broadcast })
    expect(wrapper.find('button').find('path').attributes('d')).toBe('M7 4v16l13-8z')
  })

  it("should show the live button as the player's round play button", async () => {
    expect((await mount({ ...idle, onAir: broadcast })).find('button').classes()).toEqual(
      expect.arrayContaining(['rounded-full', 'bg-brass', 'size-13', '[&>svg]:size-5'])
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

  it('should draw the whole section as one card beside the recorder', async () => {
    const section = (await mount({ ...idle, onAir: broadcast }, { aside: true })).find('section')
    expect(section.classes()).toContain('card')
  })

  it('should keep the heading above the card between the recordings and the shows', async () => {
    const section = (await mount({ ...idle, onAir: broadcast })).find('section')
    expect(section.classes()).not.toContain('card')
  })
})
