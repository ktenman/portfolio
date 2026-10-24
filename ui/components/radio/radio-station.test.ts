import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioStation from './radio-station.vue'
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

  it('should show the programme on air beside the recorder', async () => {
    expect((await mount({ ...idle, onAir: broadcast })).text()).toContain('PaarisMäng')
  })
})
