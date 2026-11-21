import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h } from 'vue'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioOnAir from './radio-on-air.vue'
import RadioIcon from './radio-icon.vue'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { type RadioStation, RecorderState, Station } from '../../models/generated/domain-models'
import { station } from './radio-fixture'

vi.mock('../../services/api', () => ({
  radioService: {
    stations: vi.fn(),
    liveUrl: '/api/radio/live',
    savePlayer: () => Promise.resolve(),
  },
}))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const SATURDAY_NOON = Date.UTC(2026, 9, 24, 9, 0, 0) / 1000

const idle = {
  station: Station.KUKU,
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

const mount = async (...stations: RadioStation[]) => {
  vi.mocked(radioService.stations).mockResolvedValue(stations)
  const wrapper = renderWithProviders({ render: () => h(RadioOnAir) })
  await flushPromises()
  return wrapper
}

const halfway = { ...broadcast, end: SATURDAY_NOON + 74 * 60 }

const dash = (wrapper: ReturnType<typeof renderWithProviders>) =>
  wrapper.get('[data-testid="radio-ring"] circle:last-child').attributes('stroke-dasharray')

describe('RadioOnAir', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime((SATURDAY_NOON + 37 * 60) * 1000)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should show nothing without a programme, a disk figure or a stopped recorder', async () => {
    expect((await mount(idle)).find('section').exists()).toBe(false)
  })

  it('should title the card Stations', async () => {
    expect((await mount({ ...idle, onAir: broadcast })).find('section').text()).toContain(
      'StationsTarkade klubi'
    )
  })

  it('should title the card Station on a chip', async () => {
    const wrapper = await mount({ ...idle, onAir: broadcast })
    await wrapper.vm.$router.replace({ query: { station: 'kuku' } })
    await flushPromises()
    expect(wrapper.find('h3').text()).toBe('Station')
  })

  it('should say when the programme on air ends', async () => {
    expect((await mount({ ...idle, onAir: broadcast })).text()).toContain('Kuku · until 13:00')
  })

  it('should mark the programme on air that is being recorded', async () => {
    const onAir = { ...broadcast, recorded: true }
    expect((await mount({ ...idle, onAir })).find('[title="Being recorded"]').text()).toBe('Rec')
  })

  it('should leave a programme that is not recorded unmarked', async () => {
    const wrapper = await mount({ ...idle, onAir: broadcast })
    expect(wrapper.find('[title="Being recorded"]').exists()).toBe(false)
  })

  it('should lead the programme on air with the live button', async () => {
    const button = (await mount({ ...idle, onAir: broadcast })).find('button').element
    expect(button.parentElement?.firstElementChild).toBe(button)
  })

  it('should close the ring by the share of the programme that has aired', async () => {
    expect(dash(await mount({ ...idle, onAir: halfway }))).toBe('50 100')
  })

  it('should close the ring fully once the programme has ended', async () => {
    const onAir = { ...broadcast, end: SATURDAY_NOON + 600 }
    expect(dash(await mount({ ...idle, onAir }))).toBeUndefined()
  })

  it('should play the station live through the server', async () => {
    const audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    const player = useRadioPlayer()
    player.attach(audio)
    const wrapper = await mount({ ...idle, onAir: broadcast })
    await wrapper.find('button').trigger('click')
    const src = audio.src
    player.close()
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
    player.close()
    expect(live).toBe(false)
  })

  it('should show the play icon on the live button', async () => {
    const wrapper = await mount({ ...idle, onAir: broadcast })
    expect(wrapper.find('button').findComponent(RadioIcon).props('name')).toBe('play')
  })

  it('should give the station on air a borderless sunken play disc', async () => {
    const classes = (await mount({ ...idle, onAir: broadcast })).find('button').classes()
    expect([classes.includes('border'), classes]).toEqual([
      false,
      expect.arrayContaining(['rounded-full', 'bg-surface-sunken', 'size-11']),
    ])
  })

  it('should keep the station playing on the sunken disc with an ink glyph', async () => {
    const player = useRadioPlayer()
    player.tuned.value = Station.KUKU
    player.playing.value = true
    const classes = (await mount({ ...idle, onAir: broadcast })).find('button').classes()
    player.close()
    expect([classes.includes('text-brass-deep'), classes]).toEqual([
      false,
      expect.arrayContaining(['bg-surface-sunken', 'text-ink']),
    ])
  })

  it('should keep the ring on the programme while the station plays', async () => {
    const player = useRadioPlayer()
    player.tuned.value = Station.KUKU
    player.playing.value = true
    const share = dash(await mount({ ...idle, onAir: halfway }))
    player.close()
    expect(share).toBe('50 100')
  })

  it('should show only the icon on the live button', async () => {
    expect((await mount({ ...idle, onAir: broadcast })).find('button').text()).toBe('')
  })

  it('should name the live button for screen readers', async () => {
    const button = (await mount({ ...idle, onAir: broadcast })).find('button')
    expect(button.attributes('aria-label')).toBe('Listen live: Kuku')
  })

  it('should name the live button Pause while the station plays', async () => {
    const player = useRadioPlayer()
    player.tuned.value = Station.KUKU
    player.playing.value = true
    const label = (await mount({ ...idle, onAir: broadcast }))
      .find('button')
      .attributes('aria-label')
    player.close()
    expect(label).toBe('Pause: Kuku')
  })

  it('should draw the rail as one card named after its heading', async () => {
    vi.mocked(radioService.stations).mockResolvedValue([{ ...idle, onAir: broadcast }])
    const wrapper = renderWithProviders({ render: () => h(RadioOnAir, { aside: true }) })
    await flushPromises()
    const rail = wrapper.find('aside')
    expect([rail.classes().includes('card'), rail.attributes('aria-label')]).toEqual([
      true,
      'Stations',
    ])
  })

  it('should keep the heading above the card between the recordings and the shows', async () => {
    const section = (await mount({ ...idle, onAir: broadcast })).find('section')
    expect(section.classes()).not.toContain('card')
  })

  const aripaev = { ...idle, station: Station.ARIPAEV, onAir: { ...broadcast, name: 'Kuum tool' } }

  const both = () => mount({ ...idle, onAir: broadcast }, aripaev)

  const labels = (wrapper: ReturnType<typeof renderWithProviders>) =>
    wrapper.findAll('button').map(button => button.attributes('aria-label'))

  it('should give each station on air its own row', async () => {
    expect(labels(await both())).toEqual(['Listen live: Kuku', 'Listen live: Äripäev'])
  })

  it('should leave out a station with nothing on air', async () => {
    expect(labels(await mount(idle))).toEqual([])
  })

  it('should lead the end of a show with its station', async () => {
    expect((await both()).text()).toContain('Äripäev · until 13:00')
  })

  it('should tune to the station of the row', async () => {
    const audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    const player = useRadioPlayer()
    player.attach(audio)
    const wrapper = await both()
    await wrapper.findAll('button')[1].trigger('click')
    const src = audio.src
    player.close()
    expect(src).toContain('/api/radio/live?station=ARIPAEV')
  })

  it('should show Pause on the tuned station only', async () => {
    const player = useRadioPlayer()
    player.tuned.value = Station.ARIPAEV
    player.playing.value = true
    const found = labels(await both())
    player.close()
    expect(found).toEqual(['Listen live: Kuku', 'Pause: Äripäev'])
  })

  it('should show only the chosen station', async () => {
    const wrapper = await both()
    await wrapper.vm.$router.replace({ query: { station: 'aripaev' } })
    await flushPromises()
    expect(labels(wrapper)).toEqual(['Listen live: Äripäev'])
  })

  const kuku = station()
  const second = station({ station: Station.ARIPAEV, hours: 2106 })

  const figures = (wrapper: ReturnType<typeof renderWithProviders>) =>
    wrapper.findAll('dd').map(figure => figure.text())

  it('should give the free space and the hours that still fit while nothing is on air', async () => {
    expect((await mount(kuku)).find('dl').text()).toBe('Free space364 GBRoom for≈ 3,159 h')
  })

  it('should rule the disk figures off from the stations above them', async () => {
    const wrapper = await mount({ ...kuku, onAir: broadcast })
    expect(wrapper.find('dl').classes()).toContain('border-t')
  })

  it('should draw no rule above the disk figures while nothing is on air', async () => {
    expect((await mount(kuku)).find('dl').classes()).not.toContain('border-t')
  })

  it('should give the room of Kuku whatever the order', async () => {
    expect(figures(await mount(second, kuku))).toContain('≈ 3,159 h')
  })

  it('should give the room of the first listed station when Kuku is off', async () => {
    expect(figures(await mount(second))).toContain('≈ 2,106 h')
  })

  it('should give the room of the station on a chip', async () => {
    const wrapper = await mount(kuku, second)
    await wrapper.vm.$router.replace({ query: { station: 'aripaev' } })
    await flushPromises()
    expect(figures(wrapper)).toContain('≈ 2,106 h')
  })

  it('should give only the free space while the hours are unknown', async () => {
    expect(figures(await mount({ ...kuku, hours: null }))).toEqual(['364 GB'])
  })

  it('should name the stations whose recorder is not running', async () => {
    const wrapper = await mount(kuku, { ...second, recorder: RecorderState.OFF })
    expect(wrapper.text()).toContain('Not running: Äripäev')
  })

  it('should say nothing about recorders that run', async () => {
    expect((await mount(kuku, second)).text()).not.toContain('Not running')
  })
})
