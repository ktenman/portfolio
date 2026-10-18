import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioProgrammes from './radio-programmes.vue'
import { DayOfWeek } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({ radioService: { programmes: vi.fn(), choose: vi.fn() } }))
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
      },
    ])
  })

  it('should show a weekday run as a range of day letters', async () => {
    expect((await mount()).text()).toContain('E–K, R 01:00–02:00')
  })

  it('should say the schedule is not loaded before the first import', async () => {
    vi.mocked(radioService.programmes).mockResolvedValue([])
    expect((await mount()).text()).toContain('Kuku kava pole veel laaditud.')
  })

  it('should show the air time of a programme', async () => {
    expect((await mount()).text()).toContain('L 12:00–13:00')
  })

  it('should ask to record a programme every time it is ticked', async () => {
    vi.mocked(radioService.choose).mockResolvedValue()
    await (await mount())
      .find('input[aria-label="Salvesta iga kord: Keskpäevatund"]')
      .setValue(true)
    expect(radioService.choose).toHaveBeenCalledWith(175, { recordEvery: true })
  })

  it('should untick the box when the choice cannot be saved', async () => {
    vi.mocked(radioService.choose).mockRejectedValue(new Error('offline'))
    const wrapper = await mount()
    const box = wrapper.find<HTMLInputElement>(
      'input[aria-label="Salvesta iga kord: Keskpäevatund"]'
    )
    await box.setValue(true)
    await flushPromises()
    expect(box.element.checked).toBe(false)
  })
})
