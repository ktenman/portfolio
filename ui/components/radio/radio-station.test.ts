import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioStation from './radio-station.vue'

vi.mock('../../services/api', () => ({ radioService: { station: vi.fn() } }))
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
}

describe('RadioStation', () => {
  it('should say no programme is chosen when nothing is planned', async () => {
    expect((await mount(idle)).text()).toContain('Pole valitud')
  })

  it('should name the next capture with its day and start', async () => {
    const capture = {
      name: '2026-10-24-keskpaevatund',
      programmes: ['Keskpäevatund'],
      start: SATURDAY_NOON - 300,
      end: SATURDAY_NOON + 3900,
    }
    expect((await mount({ ...idle, capture })).text()).toContain('Keskpäevatund, L 24.10 11:55')
  })

  it('should say how many hours of radio still fit', async () => {
    expect((await mount(idle)).text()).toContain('≈ 3159 tundi')
  })

  it('should show no free space without a recordings directory', async () => {
    expect((await mount({ ...idle, freeBytes: null, hours: null })).text()).toContain('Vaba ruumi—')
  })

  it('should say the programme on air is not recorded', async () => {
    const onAir = {
      name: 'Tarkade klubi',
      start: SATURDAY_NOON + 3600,
      end: SATURDAY_NOON + 7200,
      recorded: false,
    }
    expect((await mount({ ...idle, onAir })).text()).toContain('13:00–14:00 · ei salvestata')
  })
})
