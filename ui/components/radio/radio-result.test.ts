import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import RadioResult from './radio-result.vue'
import { recording } from './radio-fixture'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer, type Version } from '../../composables/use-radio-player'
import type { RecordingSummary } from '../../models/generated/domain-models'

const { review } = vi.hoisted(() => ({ review: vi.fn() }))

vi.mock('../../services/api', () => ({
  radioService: {
    review,
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
    position: () => Promise.resolve({ seconds: 0 }),
    savePosition: () => Promise.resolve(),
  },
}))
vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries: () => Promise.resolve() }),
}))

enableAutoUnmount(afterEach)

describe('RadioResult', () => {
  const player = useRadioPlayer()

  beforeEach(() => {
    review.mockReset().mockResolvedValue(undefined)
    const audio = document.createElement('audio')
    vi.spyOn(audio, 'load').mockImplementation(() => {})
    player.attach(audio)
  })

  afterEach(async () => {
    await useRadioDecisions(() => recording()).flush()
    player.detach()
  })

  const render = (item: RecordingSummary = recording(), version: Version = 'raw') => {
    player.load(item, version)
    return mount(RadioResult, { props: { recording: item } })
  }

  const facts = (wrapper: VueWrapper) => wrapper.find('[data-testid="radio-cut-result"]').text()

  const button = (wrapper: VueWrapper, text: string) =>
    wrapper.findAll('button').find(b => b.text().includes(text))

  const meter = (wrapper: VueWrapper) =>
    wrapper.find('[aria-label="Clean version"]').attributes('aria-valuenow')

  const rendering = recording({ render: { percent: 57, secondsLeft: 40 } })

  it('should add up what the suggested cuts remove', () => {
    expect(facts(render())).toContain('22:51 · 4 cuts')
  })

  it('should start from the cuts the episode already leaves in', () => {
    const cuts = recording().cuts.map((cut, i) => ({ ...cut, removed: i !== 1 }))
    expect(facts(render(recording({ cuts })))).toContain('20:36 · 3 cuts')
  })

  it('should count one removed cut in the singular', () => {
    const cuts = recording().cuts.map((cut, i) => ({ ...cut, removed: i === 1 }))
    const removed = render(recording({ cuts })).findAll('[data-testid="radio-cut-result"] dd')[1]!
    expect(removed.text()).toBe('2:15 · 1 cut')
  })

  it('should leave a kept break in the episode', async () => {
    const item = recording()
    const wrapper = render(item)
    useRadioDecisions(() => item).choose(1, false)
    await wrapper.vm.$nextTick()
    expect(facts(wrapper)).toContain('Clean49:23')
  })

  it('should restore the machine choice', async () => {
    const item = recording()
    const wrapper = render(item)
    useRadioDecisions(() => item).choose(1, false)
    await wrapper.vm.$nextTick()
    await button(wrapper, 'Reset')!.trigger('click')
    expect(facts(wrapper)).toContain('22:51 · 4 cuts')
  })

  it('should show the render percent while the episode updates', () => {
    const wrapper = render(rendering)
    expect([meter(wrapper), wrapper.text().includes('Updating the clean version · 57%')]).toEqual([
      '57',
      true,
    ])
  })

  it('should not pass the next tenth before the render reports it', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    const wrapper = render(recording({ render: { percent: 57, secondsLeft: 10 } }))
    vi.advanceTimersByTime(600_000)
    await flushPromises()
    vi.useRealTimers()
    expect(meter(wrapper)).toBe('59')
  })

  it('should say the episode is updating while a decision waits', async () => {
    const item = recording()
    const wrapper = render(item)
    useRadioDecisions(() => item).choose(1, false)
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Updating the clean version')
  })

  it('should say the episode is up to date once nothing waits or renders', () => {
    expect(render().text()).toContain('The clean version is up to date')
  })

  it('should show why the render failed', () => {
    const failed = recording({
      renderFailure: { reason: 'FFmpeg exited with 1 – õ', failedAt: '2026-10-17T12:00:00Z' },
    })
    expect(render(failed).text()).toContain(
      'Could not make the clean version: FFmpeg exited with 1 – õ'
    )
  })
})
