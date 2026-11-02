import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioStation from './radio-station.vue'
import { RecorderState, Station } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  radioService: { stations: vi.fn(), liveUrl: '/api/radio/live' },
}))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const SATURDAY_NOON = Date.UTC(2026, 9, 24, 9, 0, 0) / 1000

type Stations = Awaited<ReturnType<typeof radioService.stations>>

const all = async (...stations: Stations) => {
  vi.mocked(radioService.stations).mockResolvedValue(stations)
  const wrapper = renderWithProviders(RadioStation)
  await flushPromises()
  return wrapper
}

const chosen = async (station: string, ...stations: Stations) => {
  const wrapper = await all(...stations)
  await wrapper.vm.$router.replace({ query: { station } })
  await flushPromises()
  return wrapper
}

const mount = (...stations: Stations) => chosen('kuku', ...stations)

const idle = {
  station: Station.KUKU,
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
  const aripaev = { ...idle, station: Station.ARIPAEV, hours: 2106 }

  const rows = async (...stations: Stations) =>
    (await all(...stations)).findAll('[data-testid="radio-recorder"]').map(row => row.text())

  it('should title the card Recorders on All', async () => {
    expect((await all(idle, aripaev)).find('h3').text()).toBe('Recorders')
  })

  it('should title the card Recorder on a chip', async () => {
    expect((await chosen('aripaev', idle, aripaev)).find('h3').text()).toBe('Recorder')
  })

  it('should name the rail after its card', async () => {
    expect((await all(idle, aripaev)).get('aside').attributes('aria-label')).toBe(
      'Recorders and broadcast'
    )
  })

  it('should give each station a row on All', async () => {
    expect(await rows(idle, aripaev)).toEqual([
      expect.stringMatching(/^Kuku/),
      expect.stringMatching(/^Äripäev/),
    ])
  })

  it('should show one station off while the other records', async () => {
    const off = { ...idle, recorder: RecorderState.OFF }
    const recording = { ...aripaev, recorder: RecorderState.RECORDING }
    expect(await rows(off, recording)).toEqual([
      expect.stringContaining('KukuOff'),
      expect.stringContaining('ÄripäevRecording'),
    ])
  })

  it('should give the next show and the format in the row of a station', async () => {
    const format = { codec: 'mp3', bitRate: 128_000 }
    const capture = {
      name: '2026-10-24-1200-aripaev-kuum-tool',
      programmes: ['Kuum tool'],
      start: SATURDAY_NOON - 30,
      end: SATURDAY_NOON + 3630,
      slot: { start: SATURDAY_NOON, end: SATURDAY_NOON + 3600 },
    }
    const [row] = await rows({ ...aripaev, format, capture })
    expect(row).toBe('ÄripäevWaitingNext Kuum tool, Sat 24.10 12:00MP3 128 kb/s')
  })

  it('should say no show is selected in the row of a station with no capture', async () => {
    const [row] = await rows({ ...aripaev, format: { codec: 'mp3', bitRate: 128_000 } })
    expect(row).toBe('ÄripäevWaitingNo show selectedMP3 128 kb/s')
  })

  it('should leave the format out of the row of a station with no format', async () => {
    expect(await rows(aripaev)).toEqual(['ÄripäevWaitingNo show selected'])
  })

  it('should share one Free space and one Room for between the rows', async () => {
    const text = (await all(idle, aripaev)).text()
    expect(text.match(/Free space/g)).toHaveLength(1)
  })

  it('should show the current Room for figure on All whatever the order', async () => {
    expect((await all(aripaev, idle)).text()).toContain('Room for≈ 3,159 h')
  })

  it('should show the Room for figure of the station on a chip', async () => {
    expect((await chosen('aripaev', idle, aripaev)).text()).toContain('Room for≈ 2,106 h')
  })

  it('should keep the recorder facts of the station on a chip', async () => {
    const last = { programme: 'Kuum tool', end: SATURDAY_NOON + 3900 }
    expect((await chosen('aripaev', idle, { ...aripaev, last })).text()).toContain(
      'LastKuum tool, Sat 24.10 13:05'
    )
  })

  it.each([
    ['All', () => all({ ...idle, onAir: broadcast })],
    ['a chip', () => mount({ ...idle, onAir: broadcast })],
  ])('should put On air above the recorder on %s', async (_, open) => {
    expect((await open()).findAll('h3').map(heading => heading.text())).toEqual([
      'On air',
      expect.stringMatching(/^Recorders?$/),
    ])
  })

  it('should show the rows only on All', async () => {
    const wrapper = await chosen('aripaev', idle, aripaev)
    expect(wrapper.find('[data-testid="radio-recorder"]').exists()).toBe(false)
  })

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

  it('should show the programme on air beside the recorder', async () => {
    expect((await mount({ ...idle, onAir: broadcast })).text()).toContain('PaarisMäng')
  })
})
