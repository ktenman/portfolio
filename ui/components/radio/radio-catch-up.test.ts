import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioCatchUp from './radio-catch-up.vue'
import { airing, radioDay, recording } from './radio-fixture'
import { AiringState, RecorderState } from '../../models/generated/domain-models'

const player = vi.hoisted(() => ({ load: vi.fn(), playFrom: vi.fn(), listen: vi.fn() }))

vi.mock('../../services/api', () => ({
  radioService: {
    day: vi.fn(),
    record: vi.fn(),
    recordings: vi.fn(),
    station: vi.fn(),
    continuous: vi.fn(),
  },
}))
vi.mock('../../composables/use-radio-player', () => ({ useRadioPlayer: () => player }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const mount = async () => {
  const wrapper = renderWithProviders(RadioCatchUp)
  await flushPromises()
  return wrapper
}

const row = async (name: string) => {
  const wrapper = await mount()
  return wrapper.findAll('[data-testid="radio-airing"]').find(r => r.text().includes(name))!
}

const button = async (label: string) =>
  (await mount()).findAll('button').find(b => b.attributes('aria-label')?.startsWith(label))!

describe('RadioCatchUp', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(radioService.day).mockResolvedValue(radioDay())
    vi.mocked(radioService.recordings).mockResolvedValue([recording()])
    vi.mocked(radioService.record).mockResolvedValue()
    vi.mocked(radioService.station).mockResolvedValue({
      onAir: null,
      next: null,
      capture: null,
      freeBytes: 364_000_000_000,
      hours: 3159,
      recorder: RecorderState.WAITING,
      last: null,
      format: null,
      continuous: { on: false, dayBytes: 2_764_800_000, weekBytes: 19_353_600_000 },
    })
  })

  it('should fold the morning that was not recorded into one row', async () => {
    expect((await mount()).text()).toContain('05:00–10:00 · 3 saadet, salvestamata')
  })

  it('should hide the airings of a folded morning', async () => {
    expect((await mount()).text()).not.toContain('Laupäevane hommik')
  })

  it('should show the airings of a folded morning once it is opened', async () => {
    const wrapper = await mount()
    await wrapper.find('button[aria-expanded="false"]').trigger('click')
    expect(wrapper.text()).toContain('Laupäevane hommik')
  })

  it('should set the night after midnight apart from the day', async () => {
    expect((await mount()).text()).toContain('Öö vastu pühapäeva, 18.10')
  })

  it('should mark today among the days to choose from', async () => {
    expect((await mount()).text()).toContain('L 17.10 · täna')
  })

  it('should ask for the day that is chosen', async () => {
    const wrapper = await mount()
    await wrapper
      .findAll('button')
      .find(b => b.text() === 'R 16.10')!
      .trigger('click')
    await flushPromises()
    expect(radioService.day).toHaveBeenLastCalledWith('2026-10-16')
  })

  it('should say when the recording of an airing was made', async () => {
    expect((await row('12:00')).text()).toContain('Salvestus 11:55–13:05')
  })

  it('should say how much of a partly recorded airing is kept and where', async () => {
    expect((await row('10:00')).text()).toContain('Viimased 5 min on salvestuses „Keskpäevatund“')
  })

  it('should say a repeat can be recorded in full when its original is partial', async () => {
    expect((await row('00:00')).text()).toContain('Kordus · saab salvestada täispikalt')
  })

  it('should say when the original of a repeat was missed', async () => {
    expect((await row('21:00Meretund')).text()).toContain('Kordus · L 17.10 09:00 jäi salvestamata')
  })

  it('should show a repeat whose original is archived', async () => {
    expect((await row('18:00')).text()).toContain('Originaal arhiivis')
  })

  it('should play a partly recorded airing from where it is covered', async () => {
    await (await button('Kuula lõppu')).trigger('click')
    expect(player.playFrom).toHaveBeenCalledWith(0)
  })

  it('should play the original of an archived repeat from its start', async () => {
    await (await button('Kuula: Keskpäevatund 18:00')).trigger('click')
    expect(player.playFrom).toHaveBeenCalledWith(300)
  })

  it('should listen live to an airing that is on air', async () => {
    await (await button('Kuula otse')).trigger('click')
    expect(player.listen).toHaveBeenCalled()
  })

  it('should ask to record a later airing once', async () => {
    await (await button('Salvesta: Ilmaparandaja')).trigger('click')
    expect(radioService.record).toHaveBeenCalledWith(7, { record: true })
  })

  it('should ask to cancel an airing chosen once', async () => {
    await (await button('Tühista')).trigger('click')
    expect(radioService.record).toHaveBeenCalledWith(17, { record: false })
  })

  it('should not offer to play an airing whose recording is not listed', async () => {
    vi.mocked(radioService.recordings).mockResolvedValue([])
    expect((await button('Kuula: Keskpäevatund 12:00')).attributes('disabled')).toBeDefined()
  })

  it('should say the schedule is not loaded for a day without airings', async () => {
    vi.mocked(radioService.day).mockResolvedValue(radioDay({ day: [], night: [] }))
    expect((await mount()).text()).toContain('Selle päeva kava pole veel laaditud.')
  })

  it('should size a day and a week of continuous recording', async () => {
    expect((await mount()).text()).toContain('Päevas≈ 2,8 GB7 päeva≈ 19 GBVaba ruumi364 GB')
  })

  it('should switch continuous recording on', async () => {
    vi.mocked(radioService.continuous).mockResolvedValue()
    const wrapper = await mount()
    await wrapper
      .findAll('button')
      .find(b => b.text() === 'Lülita sisse')!
      .trigger('click')
    expect(radioService.continuous).toHaveBeenCalledWith({ on: true })
  })

  it('should give the length of an airing in whole hours', async () => {
    vi.mocked(radioService.day).mockResolvedValue(
      radioDay({ day: [{ missed: false, airings: [airing({ state: AiringState.OPEN })] }] })
    )
    expect((await row('14:00')).text()).toContain('1 h')
  })
})
