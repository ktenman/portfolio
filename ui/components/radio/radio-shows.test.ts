import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioShows from './radio-shows.vue'
import { DayOfWeek, RecorderState } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  radioService: {
    programmes: vi.fn(),
    choose: vi.fn(),
    station: vi.fn(),
    liveUrl: '/api/radio/live',
  },
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
  freeBytes: 364_000_000_000,
  hours: 3159,
  recorder: RecorderState.WAITING,
  last: null,
  format: null,
}

const capture = {
  name: '2026-10-24-1200-keskpaevatund',
  programmes: ['Keskpäevatund'],
  start: SATURDAY_NOON - 30,
  end: SATURDAY_NOON + 3630,
  slot: { start: SATURDAY_NOON, end: SATURDAY_NOON + 3600 },
}

describe('RadioShows', () => {
  it('should name a show as the next capture once it is ticked', async () => {
    vi.mocked(radioService.programmes).mockResolvedValue([
      {
        id: 175,
        name: 'Keskpäevatund',
        description: null,
        recordEvery: false,
        slots: [{ days: [DayOfWeek.SATURDAY], start: '12:00:00', end: '13:00:00' }],
        recordings: 3,
      },
    ])
    vi.mocked(radioService.station)
      .mockResolvedValueOnce(idle)
      .mockResolvedValue({ ...idle, capture })
    vi.mocked(radioService.choose).mockResolvedValue()
    const wrapper = renderWithProviders(RadioShows)
    await flushPromises()
    await wrapper.find('input[aria-label="Record every time: Keskpäevatund"]').setValue(true)
    await flushPromises()
    expect(wrapper.text()).toContain('Keskpäevatund, Sat 24.10 12:00')
  })
})
