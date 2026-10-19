import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { radioService } from '../../services/api'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioCuts from './radio-cuts.vue'
import { contexts, programme, recording } from './radio-fixture'
import {
  BoundaryMethod,
  CutKind,
  CutStep,
  ProgrammeCutState,
} from '../../models/generated/domain-models'
import type { ProgrammeCuts, RecordingSummary } from '../../models/generated/domain-models'

const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }))

vi.mock('../../services/api', () => ({
  radioService: {
    cuts: vi.fn(),
    review: vi.fn(),
    jingle: vi.fn(),
    addCut: vi.fn(),
    dropCut: vi.fn(),
    findCuts: vi.fn(),
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
  },
}))
vi.mock('../../composables/use-confirm', () => ({ useConfirm: () => ({ confirm }) }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

describe('RadioCuts', () => {
  const player = useRadioPlayer()
  let audio: HTMLAudioElement

  beforeEach(() => {
    vi.mocked(radioService.cuts).mockResolvedValue(contexts())
    vi.mocked(radioService.review).mockReset().mockResolvedValue(recording().editList!)
    vi.mocked(radioService.jingle).mockReset().mockResolvedValue()
    vi.mocked(radioService.addCut).mockReset().mockResolvedValue()
    vi.mocked(radioService.dropCut).mockReset().mockResolvedValue()
    vi.mocked(radioService.findCuts).mockReset().mockResolvedValue()
    confirm.mockReset()
    audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    player.attach(audio)
    player.load(recording())
  })

  afterEach(() => player.detach())

  const render = async (target: RecordingSummary = recording()) => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = mount(RadioCuts, {
      props: { recording: target },
      global: { plugins: [[VueQueryPlugin, { queryClient }]] },
    })
    await flushPromises()
    return wrapper
  }

  const card = async (index: number) => (await render()).findAll('[data-testid="radio-cut"]')[index]

  const button = (wrapper: { findAll: (s: string) => any[] }, text: string) =>
    wrapper.findAll('button').find(b => b.text().includes(text))

  const result = (wrapper: { find: (s: string) => any }) =>
    wrapper.find('[data-testid="radio-cut-result"]').text()

  it('should show a card for each suggested cut', async () => {
    expect((await render()).findAll('[data-testid="radio-cut"]')).toHaveLength(4)
  })

  it('should name each transition preview after its cut', async () => {
    expect(button(await card(1), 'Kuula üleminekut')?.attributes('aria-label')).toBe(
      'Kuula üleminekut: Reklaamipaus 12:30'
    )
  })

  it('should ask to check a cut the machine is unsure of', async () => {
    expect((await card(2)).text()).toContain('Kontrolli')
  })

  it('should quote what the hosts say on both sides of a cut', async () => {
    expect((await card(1)).text()).toContain(
      '12:30 „Ja siinkohal väike paus.“ … 12:32 „Ja Keskpäevatund jätkab.“'
    )
  })

  it('should name how the machine found the edges of a cut', async () => {
    expect((await card(1)).text()).toContain('helitaseme langus · jaama džingel')
  })

  it('should add up what the suggested cuts remove', async () => {
    expect(result(await render())).toContain('22:51 · 4 lõiget')
  })

  it('should start from the cuts the episode already leaves in', async () => {
    const cuts = recording().cuts.map((cut, i) => ({ ...cut, removed: i !== 1 }))
    expect(result(await render(recording({ cuts })))).toContain('20:36 · 3 lõiget')
  })

  it('should leave a kept break in the episode', async () => {
    const wrapper = await render()
    await button(wrapper.findAll('[data-testid="radio-cut"]')[1], 'Jäta alles').trigger('click')
    expect(result(wrapper)).toContain('Puhastatud49:23')
  })

  it('should restore the machine choice', async () => {
    const wrapper = await render()
    await button(wrapper.findAll('[data-testid="radio-cut"]')[1], 'Jäta alles').trigger('click')
    await button(wrapper, 'Lähtesta').trigger('click')
    expect(result(wrapper)).toContain('22:51 · 4 lõiget')
  })

  it('should send the cuts to remove once the user confirms', async () => {
    confirm.mockResolvedValue(true)
    const wrapper = await render()
    await button(wrapper.findAll('[data-testid="radio-cut"]')[1], 'Jäta alles').trigger('click')
    await button(wrapper, 'Rakenda lõiked').trigger('click')
    await flushPromises()
    expect(radioService.review).toHaveBeenCalledWith('2026-10-17', { removed: [0, 2, 3] })
  })

  it('should not touch the episode when the user cancels', async () => {
    confirm.mockResolvedValue(false)
    await button(await render(), 'Rakenda lõiked').trigger('click')
    await flushPromises()
    expect(radioService.review).not.toHaveBeenCalled()
  })

  it('should show the jingle that ends a break and how well it matched', async () => {
    expect((await card(1)).text()).toContain('Džingel r = 0,93 · kinnitamata')
  })

  it('should show no jingle for a cut without one', async () => {
    expect((await card(2)).text()).not.toContain('Džingel')
  })

  it('should save the jingle of a break once the user confirms it', async () => {
    await button(await card(1), 'Kinnita džingel').trigger('click')
    await flushPromises()
    expect(radioService.jingle).toHaveBeenCalledWith('2026-10-17', 1, { saved: true })
  })

  it('should forget a saved jingle', async () => {
    const cuts = recording().cuts.map(cut =>
      cut.jingle ? { ...cut, jingle: { ...cut.jingle, saved: true } } : cut
    )
    const wrapper = await render(recording({ cuts }))
    await button(wrapper.findAll('[data-testid="radio-cut"]')[1], 'Unusta džingel').trigger('click')
    await flushPromises()
    expect(radioService.jingle).toHaveBeenCalledWith('2026-10-17', 1, { saved: false })
  })

  it('should name the jingle button after its cut', async () => {
    expect(button(await card(1), 'Kinnita džingel')?.attributes('aria-label')).toBe(
      'Kinnita džingel: Reklaamipaus 12:30'
    )
  })

  const manual = {
    kind: CutKind.MANUAL,
    title: 'Käsitsi lõige',
    start: 2600,
    end: 2640,
    methods: [BoundaryMethod.MANUAL, BoundaryMethod.MANUAL],
    review: false,
    removed: false,
    jingle: null,
  }

  const typed = async (start: string, end: string) => {
    const wrapper = await render()
    await wrapper.find('#radio-manual-start').setValue(start)
    await wrapper.find('#radio-manual-end').setValue(end)
    return wrapper
  }

  it('should add a cut typed as clock times', async () => {
    await (await typed('12:31:05', '12:32:10')).find('form').trigger('submit')
    expect(radioService.addCut).toHaveBeenCalledWith('2026-10-17', { start: 2165, end: 2230 })
  })

  it('should not add a cut that ends before it starts', async () => {
    const wrapper = await typed('12:32:10', '12:31:05')
    expect(button(wrapper, 'Lisa lõige')?.attributes('disabled')).toBeDefined()
  })

  it('should take an end of the cut from where the player is', async () => {
    player.playFrom(2165)
    audio.dispatchEvent(new Event('timeupdate'))
    const wrapper = await render()
    await button(wrapper, 'Praegune koht').trigger('click')
    expect((wrapper.find('#radio-manual-start').element as HTMLInputElement).value).toBe('12:31:05')
  })

  it('should show a cut made by hand among the others in recording order', async () => {
    const cuts = [...recording().cuts, manual]
    const titles = (await render(recording({ cuts })))
      .findAll('[data-testid="radio-cut"] h4')
      .map(title => title.text())
    expect(titles[2]).toBe('Käsitsi lõige')
  })

  it('should choose to remove a cut once it is added by hand', async () => {
    const wrapper = await render()
    await wrapper.setProps({ recording: recording({ cuts: [...recording().cuts, manual] }) })
    expect(result(wrapper)).toContain('5 lõiget')
  })

  it('should delete a cut made by hand', async () => {
    const cuts = [...recording().cuts, manual]
    await button(await render(recording({ cuts })), 'Kustuta').trigger('click')
    expect(radioService.dropCut).toHaveBeenCalledWith('2026-10-17', 4)
  })

  const searching = (state: ProgrammeCutState, details: Partial<ProgrammeCuts> = {}) =>
    recording({
      editList: null,
      cuts: [],
      programmeCuts: [programme('Tarkade klubi', state, details)],
    })

  const finding = searching(ProgrammeCutState.FINDING, {
    progress: { step: CutStep.READ, read: 1, percent: 36, secondsLeft: 70 },
  })

  const waiting = searching(ProgrammeCutState.WAITING, {
    transcript: { writtenSeconds: 3180, totalSeconds: 4200, percent: 75, secondsLeft: 1920 },
  })

  const bar = (wrapper: VueWrapper) => {
    const progress = wrapper.find('[role="progressbar"]')
    return [progress.attributes('aria-valuenow'), progress.attributes('aria-valuetext')]
  }

  it('should show the step and time left of a running cut search', async () => {
    expect((await render(finding)).text()).toContain('AI loeb saadet · 1/3~1 min jäänud')
  })

  it('should give the percent and step of a running cut search to a screen reader', async () => {
    expect(bar(await render(finding))).toEqual(['36', '36 %, AI loeb saadet · 1/3, ~1 min jäänud'])
  })

  it('should say which programme a queued cut search waits for', async () => {
    const target = searching(ProgrammeCutState.QUEUED, { after: 'Keskpäevatund' })
    expect((await render(target)).text()).toContain('JärjekorrasAlgab pärast saadet Keskpäevatund')
  })

  it('should say a queued cut search starts at once when nothing runs before it', async () => {
    expect((await render(searching(ProgrammeCutState.QUEUED))).text()).toContain('Algab kohe')
  })

  it('should say how much of the transcript a waiting cut search has', async () => {
    expect((await render(waiting)).text()).toContain('Ootan teksti · 53 / 70 min kirjas')
  })

  it('should give the transcript written so far to a screen reader', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-19T09:30:00Z'))
    const read = bar(await render(waiting))
    vi.useRealTimers()
    expect(read).toEqual([
      '75',
      '75 %, Ootan teksti · 53 / 70 min kirjas, ~32 min jäänud · valmis ~13:02',
    ])
  })

  const failed = recording({
    editList: null,
    cuts: [],
    programmeCuts: [programme('Tarkade klubi', ProgrammeCutState.FAILED)],
    failure: { reason: 'No outline read to vote on', failedAt: '2026-10-19T10:20:00Z' },
  })

  it('should say when and why a cut search failed', async () => {
    expect((await render(failed)).text()).toContain(
      'Otsing katkes kell 13:20No outline read to vote on'
    )
  })

  it('should look for the cuts of a failed programme again', async () => {
    await button(await render(failed), 'Proovi uuesti').trigger('click')
    expect(radioService.findCuts).toHaveBeenCalledWith('2026-10-17', { programme: 'Tarkade klubi' })
  })

  it('should say there are no suggestions when no cut search is coming', async () => {
    expect((await render(searching(ProgrammeCutState.NONE))).text()).toContain(
      'Lõikesoovitusi pole.'
    )
  })

  it('should not show the cut search once every programme has its cuts', async () => {
    expect((await render()).text()).not.toContain('Leia lõiked')
  })

  it('should play the moment before a cut to hear the transition', async () => {
    await button(await card(1), 'Kuula üleminekut').trigger('click')
    expect(audio.currentTime).toBe(2115)
  })
})
