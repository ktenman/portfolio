import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioProgrammes from './radio-programmes.vue'
import { station } from './radio-fixture'
import { DayOfWeek, Station } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  radioService: {
    programmes: vi.fn(),
    choose: vi.fn(),
    station: vi.fn(),
  },
}))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const mount = async () => {
  const wrapper = renderWithProviders(RadioProgrammes)
  await flushPromises()
  return wrapper
}

describe('RadioProgrammes', () => {
  beforeEach(() => {
    vi.mocked(radioService.choose).mockReset()
    vi.mocked(radioService.station).mockReset().mockResolvedValue(station())
    vi.mocked(radioService.programmes).mockResolvedValue([
      {
        id: 175,
        name: 'Keskpäevatund',
        description: 'Ülle ja Õie arutavad nädala uudiseid',
        recordEvery: false,
        slots: [
          { days: [DayOfWeek.SATURDAY], start: '12:00:00', end: '13:00:00' },
          {
            days: [DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY],
            start: '01:00:00',
            end: '02:00:00',
          },
        ],
        recordings: 3,
        station: Station.KUKU,
      },
    ])
  })

  it('should show a weekday run as a range of weekdays', async () => {
    expect((await mount()).text()).toContain('Mon–Wed, Fri 01:00–02:00')
  })

  it('should say the schedule is not loaded before the first import', async () => {
    vi.mocked(radioService.programmes).mockResolvedValue([])
    expect((await mount()).text()).toContain("Kuku's schedule has not been loaded yet.")
  })

  it('should show the air time of a programme', async () => {
    expect((await mount()).text()).toContain('Sat 12:00–13:00')
  })

  it('should ask to record a programme every time it is ticked', async () => {
    vi.mocked(radioService.choose).mockResolvedValue()
    await (await mount())
      .find('input[aria-label="Record every time: Keskpäevatund"]')
      .setValue(true)
    expect(radioService.choose).toHaveBeenCalledWith(175, { recordEvery: true })
  })

  it('should name a show as the next capture once it is ticked', async () => {
    const noon = Date.UTC(2026, 9, 24, 9, 0, 0) / 1000
    const capture = {
      name: '2026-10-24-1200-keskpaevatund',
      programmes: ['Keskpäevatund'],
      start: noon - 30,
      end: noon + 3630,
      slot: { start: noon, end: noon + 3600 },
    }
    vi.mocked(radioService.station)
      .mockResolvedValueOnce(station())
      .mockResolvedValue(station({ capture }))
    vi.mocked(radioService.choose).mockResolvedValue()
    const wrapper = await mount()
    await wrapper.find('input[aria-label="Record every time: Keskpäevatund"]').setValue(true)
    await flushPromises()
    expect(wrapper.text()).toContain('Keskpäevatund, Sat 24.10 12:00')
  })

  it('should untick the box when the choice cannot be saved', async () => {
    vi.mocked(radioService.choose).mockRejectedValue(new Error('offline'))
    const wrapper = await mount()
    const box = wrapper.find<HTMLInputElement>(
      'input[aria-label="Record every time: Keskpäevatund"]'
    )
    await box.setValue(true)
    await flushPromises()
    expect(box.element.checked).toBe(false)
  })

  const many = (count: number, recordEvery = false) =>
    Array.from({ length: count }, (_, i) => ({
      id: i,
      name: `Saade ${i}`,
      description: null,
      recordEvery,
      slots: [{ days: [DayOfWeek.SATURDAY], start: '09:00:00', end: '10:00:00' }],
      recordings: 0,
      station: Station.KUKU,
    }))

  const rows = (wrapper: { findAll: (s: string) => unknown[] }) =>
    wrapper.findAll('[data-testid="radio-programme-row"]').length

  it('should show a programme on a phone as one row with its name and air time', async () => {
    expect((await mount()).find('[data-testid="radio-programme-row"]').text()).toBe(
      'KeskpäevatundSat 12:00–13:00 · Mon–Wed, Fri 01:00–02:00 Every time'
    )
  })

  it('should show every programme on the schedule', async () => {
    vi.mocked(radioService.programmes).mockResolvedValue(many(10))
    expect(rows(await mount())).toBe(10)
  })

  it('should offer no link to more programmes', async () => {
    vi.mocked(radioService.programmes).mockResolvedValue(many(10))
    expect((await mount()).text()).not.toContain('shows on the schedule')
  })
})
