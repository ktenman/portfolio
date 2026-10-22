import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { radioService } from '../../services/api'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioText from './radio-text.vue'
import { contexts, programme, recording, sections, transcript } from './radio-fixture'
import {
  BoundaryMethod,
  CutKind,
  CutStep,
  ProgrammeCutState,
} from '../../models/generated/domain-models'
import type { ProgrammeCuts, RecordingSummary } from '../../models/generated/domain-models'

const { success } = vi.hoisted(() => ({ success: vi.fn() }))

vi.mock('../../services/api', () => ({
  radioService: {
    transcript: vi.fn(),
    cuts: vi.fn(),
    review: vi.fn(),
    jingle: vi.fn(),
    addCut: vi.fn(),
    dropCut: vi.fn(),
    findCuts: vi.fn(),
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
  },
}))
vi.mock('../../composables/use-toast', () => ({ useToast: () => ({ success, error: vi.fn() }) }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

describe('Radio cuts', () => {
  const player = useRadioPlayer()
  let audio: HTMLAudioElement

  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
    vi.mocked(radioService.cuts).mockResolvedValue(contexts())
    vi.mocked(radioService.review).mockReset().mockResolvedValue()
    vi.mocked(radioService.jingle).mockReset().mockResolvedValue()
    vi.mocked(radioService.addCut).mockReset().mockResolvedValue()
    vi.mocked(radioService.dropCut).mockReset().mockResolvedValue()
    vi.mocked(radioService.findCuts).mockReset().mockResolvedValue()
    success.mockReset()
    audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    player.attach(audio)
    player.load(recording())
  })

  afterEach(() => player.detach())

  const render = async (target: RecordingSummary = recording()) => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    vi.mocked(radioService.transcript).mockResolvedValue(sections(transcript(), target))
    const wrapper = mount(RadioText, {
      props: { recording: target },
      global: { plugins: [[VueQueryPlugin, { queryClient }]] },
    })
    await flushPromises()
    return wrapper
  }

  const card = async (index: number) => (await render()).findAll('[data-testid="radio-cut"]')[index]

  const button = (wrapper: { findAll: (s: string) => any[] }, text: string) =>
    wrapper
      .findAll('button')
      .find(b => b.text().includes(text) || b.attributes('aria-label')?.startsWith(text))

  it('should show a card for each suggested cut', async () => {
    expect((await render()).findAll('[data-testid="radio-cut"]')).toHaveLength(4)
  })

  it('should name each transition preview after its cut', async () => {
    expect(button(await card(1), 'Listen to the transition')?.attributes('aria-label')).toBe(
      'Listen to the transition: Ad break 12:30'
    )
  })

  it('should ask to check a cut the machine is unsure of', async () => {
    expect((await card(2)).text()).toContain('Check')
  })

  it('should quote what the hosts say on both sides of a cut', async () => {
    expect((await card(1)).text()).toContain(
      '12:30 “Ja siinkohal väike paus.” … 12:32 “Ja Keskpäevatund jätkab.”'
    )
  })

  it('should name how the machine found the edges of a cut', async () => {
    expect((await card(1)).text()).toContain('level dip · station jingle')
  })

  it('should save a decision without a button', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const wrapper = await render()
    await button(wrapper.findAll('[data-testid="radio-cut"]')[1], 'Keep').trigger('click')
    vi.advanceTimersByTime(3000)
    vi.useRealTimers()
    expect(vi.mocked(radioService.review).mock.calls).toEqual([
      ['2026-10-17', { removed: [0, 2, 3] }],
    ])
  })

  it('should offer no button to apply the cuts', async () => {
    expect(button(await render(), 'Apply')).toBeUndefined()
  })

  it('should show the jingle that ends a break and how well it matched', async () => {
    expect((await card(1)).text()).toContain('Jingle r = 0.93 · unconfirmed')
  })

  it('should show no jingle for a cut without one', async () => {
    expect((await card(2)).text()).not.toContain('Jingle')
  })

  it('should save the jingle of a break once the user confirms it', async () => {
    await button(await card(1), 'Confirm jingle').trigger('click')
    await flushPromises()
    expect(radioService.jingle).toHaveBeenCalledWith('2026-10-17', 1, { saved: true })
  })

  it('should forget a saved jingle', async () => {
    const cuts = recording().cuts.map(cut =>
      cut.jingle ? { ...cut, jingle: { ...cut.jingle, saved: true } } : cut
    )
    const wrapper = await render(recording({ cuts }))
    await button(wrapper.findAll('[data-testid="radio-cut"]')[1], 'Forget jingle').trigger('click')
    await flushPromises()
    expect(radioService.jingle).toHaveBeenCalledWith('2026-10-17', 1, { saved: false })
  })

  it('should name the jingle button after its cut', async () => {
    expect(button(await card(1), 'Confirm jingle')?.attributes('aria-label')).toBe(
      'Confirm jingle: Ad break 12:30'
    )
  })

  const handmade = (...added: (typeof manual)[]) =>
    recording({
      cuts: [...recording().cuts, ...added],
      timeline: [
        ...recording().timeline,
        ...added.map(cut => ({
          start: cut.start,
          end: cut.end,
          title: cut.title,
          cut: true,
          label: null,
          summary: null,
        })),
      ].sort((a, b) => a.start - b.start),
    })

  const manual = {
    kind: CutKind.MANUAL,
    title: 'Manual cut',
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
    expect(button(wrapper, 'Add cut')?.attributes('disabled')).toBeDefined()
  })

  it('should take an end of the cut from where the player is', async () => {
    player.playFrom(2165)
    audio.dispatchEvent(new Event('timeupdate'))
    const wrapper = await render()
    await button(wrapper, 'Current position').trigger('click')
    expect((wrapper.find('#radio-manual-start').element as HTMLInputElement).value).toBe('12:31:05')
  })

  it('should show a cut made by hand among the others in recording order', async () => {
    const titles = (await render(handmade(manual)))
      .findAll('[data-testid="radio-cut"] h4')
      .map(title => title.text())
    expect(titles[2]).toBe('Manual cut')
  })

  it('should remove a cut as soon as it is added by hand', async () => {
    const wrapper = await typed('12:31:05', '12:32:10')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await wrapper.unmount()
    expect(vi.mocked(radioService.review).mock.calls).toEqual([
      ['2026-10-17', { removed: [0, 1, 2, 3, 4] }],
    ])
  })

  it('should not decide on a cut the machine adds', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const wrapper = await render()
    await wrapper.setProps({ recording: recording({ cuts: [...recording().cuts, manual] }) })
    vi.advanceTimersByTime(3000)
    vi.useRealTimers()
    expect(radioService.review).not.toHaveBeenCalled()
  })

  it('should not offer to delete a cut made by hand once it is chosen for removal', async () => {
    const wrapper = await render(handmade(manual))
    await button(wrapper.findAll('[data-testid="radio-cut"]')[2], 'Remove').trigger('click')
    expect(button(wrapper, 'Delete')).toBeUndefined()
  })

  it('should save the decisions before a cut made by hand is deleted', async () => {
    const calls: string[] = []
    vi.mocked(radioService.review).mockImplementation(async () => void calls.push('review'))
    vi.mocked(radioService.dropCut).mockImplementation(async () => void calls.push('drop'))
    const wrapper = await render(handmade(manual, manual))
    await button(wrapper.findAll('[data-testid="radio-cut"]')[1], 'Keep').trigger('click')
    await button(wrapper, 'Delete').trigger('click')
    await flushPromises()
    expect(calls).toEqual(['review', 'drop'])
  })

  it('should delete a cut made by hand', async () => {
    await button(await render(handmade(manual)), 'Delete').trigger('click')
    await flushPromises()
    expect(radioService.dropCut).toHaveBeenCalledWith('2026-10-17', 4)
  })

  const searching = (state: ProgrammeCutState, details: Partial<ProgrammeCuts> = {}) =>
    recording({
      editList: null,
      cuts: [],
      programmeCuts: [programme('Tarkade klubi', state, details)],
    })

  const finding = searching(ProgrammeCutState.FINDING, {
    progress: { step: CutStep.READ, read: 1, percent: 36, secondsLeft: 70, stepSecondsLeft: 50 },
  })

  const waiting = searching(ProgrammeCutState.WAITING, {
    transcript: { writtenSeconds: 3180, totalSeconds: 4200, percent: 75, secondsLeft: 1920 },
  })

  const bar = (wrapper: VueWrapper) => {
    const progress = wrapper.find('[role="progressbar"]')
    return [progress.attributes('aria-valuenow'), progress.attributes('aria-valuetext')]
  }

  it('should show the step and time left of a running cut search', async () => {
    expect((await render(finding)).text()).toContain('Reading the show with AI · 1/3~1 min left')
  })

  it('should show the percent of a running cut search without a space before the sign', async () => {
    expect((await render(finding)).text()).toContain('36%')
  })

  it('should give the percent and step of a running cut search to a screen reader', async () => {
    expect(bar(await render(finding))).toEqual([
      '36',
      '36%, Reading the show with AI · 1/3, ~1 min left',
    ])
  })

  it("should move a running cut search's percent on between updates", async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    const wrapper = await render(finding)
    vi.advanceTimersByTime(35_000)
    await flushPromises()
    vi.useRealTimers()
    expect(bar(wrapper)[0]).toBe('68')
  })

  it('should not pass the end of the current step however long it waits', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    const wrapper = await render(
      searching(ProgrammeCutState.FINDING, {
        progress: {
          step: CutStep.READ,
          read: 1,
          percent: 36,
          secondsLeft: 70,
          stepSecondsLeft: 10,
        },
      })
    )
    vi.advanceTimersByTime(600_000)
    await flushPromises()
    vi.useRealTimers()
    expect(bar(wrapper)[0]).toBe('45')
  })

  it("should start again from a new update's percent", async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    const wrapper = await render(finding)
    vi.advanceTimersByTime(35_000)
    await wrapper.setProps({
      recording: searching(ProgrammeCutState.FINDING, {
        progress: {
          step: CutStep.RENDER,
          read: null,
          percent: 50,
          secondsLeft: 60,
          stepSecondsLeft: 50,
        },
      }),
    })
    await flushPromises()
    vi.useRealTimers()
    expect(bar(wrapper)[0]).toBe('50')
  })

  it('should say which programme a queued cut search waits for', async () => {
    const target = searching(ProgrammeCutState.QUEUED, { after: 'Keskpäevatund' })
    expect((await render(target)).text()).toContain('QueuedStarts after Keskpäevatund')
  })

  it('should say a queued cut search starts at once when nothing runs before it', async () => {
    expect((await render(searching(ProgrammeCutState.QUEUED))).text()).toContain('Starts now')
  })

  it('should say how much of the transcript a waiting cut search has', async () => {
    expect((await render(waiting)).text()).toContain(
      'Waiting for the transcript · 53 / 70 min written'
    )
  })

  it('should give the transcript written so far to a screen reader', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-19T09:30:00Z'))
    const read = bar(await render(waiting))
    vi.useRealTimers()
    expect(read).toEqual([
      '75',
      '75%, Waiting for the transcript · 53 / 70 min written, ~32 min left · ready at ~13:02',
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
      'The search stopped at 13:20No outline read to vote on'
    )
  })

  it('should look for the cuts of a failed programme again', async () => {
    await button(await render(failed), 'Try again').trigger('click')
    expect(radioService.findCuts).toHaveBeenCalledWith('2026-10-17', { programme: 'Tarkade klubi' })
  })

  it('should say there are no suggestions when no cut search is coming', async () => {
    expect((await render(searching(ProgrammeCutState.NONE))).text()).toContain('No suggested cuts.')
  })

  it('should not show the cut search once every programme has its cuts', async () => {
    expect((await render()).text()).not.toContain('Find cuts')
  })

  const section = async (index: number) =>
    (await render()).findAll('[data-testid="radio-section"]')[index]

  it('should show each cut above its own lines in the text', async () => {
    expect((await section(2)).find('[data-testid="radio-cut"] h4').text()).toBe('Ad break')
  })

  it('should save a decision from a cut in the text', async () => {
    const wrapper = await render()
    await button(wrapper.findAll('[data-testid="radio-section"]')[2], 'Keep').trigger('click')
    wrapper.unmount()
    await flushPromises()
    expect(vi.mocked(radioService.review).mock.calls).toEqual([
      ['2026-10-17', { removed: [0, 2, 3] }],
    ])
  })

  it('should show no cut cards in the cleaned version', async () => {
    player.load(recording(), 'episode')
    expect((await render()).find('[data-testid="radio-cut"]').exists()).toBe(false)
  })

  it('should play the moment before a cut to hear the transition', async () => {
    await button(await card(1), 'Listen to the transition').trigger('click')
    expect(audio.currentTime).toBe(2115)
  })
})
