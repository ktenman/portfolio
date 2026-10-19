import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import RadioResult from './radio-result.vue'
import { recording } from './radio-fixture'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer, type Version } from '../../composables/use-radio-player'
import type { RecordingSummary } from '../../models/generated/domain-models'

const { review } = vi.hoisted(() => ({ review: vi.fn() }))

vi.mock('../../services/api', () => ({
  radioService: { review, fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}` },
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
    wrapper.find('[aria-label="Puhastatud versioon"]').attributes('aria-valuenow')

  const rendering = recording({ render: { percent: 57, secondsLeft: 40 } })

  it('should add up what the suggested cuts remove', () => {
    expect(facts(render())).toContain('22:51 · 4 lõiget')
  })

  it('should start from the cuts the episode already leaves in', () => {
    const cuts = recording().cuts.map((cut, i) => ({ ...cut, removed: i !== 1 }))
    expect(facts(render(recording({ cuts })))).toContain('20:36 · 3 lõiget')
  })

  it('should leave a kept break in the episode', async () => {
    const item = recording()
    const wrapper = render(item)
    useRadioDecisions(() => item).choose(1, false)
    await wrapper.vm.$nextTick()
    expect(facts(wrapper)).toContain('Puhastatud49:23')
  })

  it('should restore the machine choice', async () => {
    const item = recording()
    const wrapper = render(item)
    useRadioDecisions(() => item).choose(1, false)
    await wrapper.vm.$nextTick()
    await button(wrapper, 'Lähtesta')!.trigger('click')
    expect(facts(wrapper)).toContain('22:51 · 4 lõiget')
  })

  it('should show the render percent while the episode updates', () => {
    const wrapper = render(rendering)
    expect([
      meter(wrapper),
      wrapper.text().includes('Uuendan puhastatud versiooni · 57 %'),
    ]).toEqual(['57', true])
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
    expect(wrapper.text()).toContain('Uuendan puhastatud versiooni')
  })

  it('should say the episode is up to date once nothing waits or renders', () => {
    expect(render().text()).toContain('Puhastatud versioon on ajakohane')
  })

  it('should show why the render failed', () => {
    const failed = recording({
      renderFailure: { reason: 'FFmpeg exited with 1 – õ', failedAt: '2026-10-17T12:00:00Z' },
    })
    expect(render(failed).text()).toContain(
      'Puhastatud versiooni ei saanud teha: FFmpeg exited with 1 – õ'
    )
  })

  it('should sum up the applied cuts on the cleaned episode', () => {
    expect(render(recording(), 'episode').text()).toContain(
      '4 lõiget rakendatud, kokku 22:51 eemaldatud. Puhastatud saade on 41:52.'
    )
  })

  it('should switch to the raw recording from Muuda lõikeid', async () => {
    await button(render(recording(), 'episode'), 'Muuda lõikeid')!.trigger('click')
    expect(player.version.value).toBe('raw')
  })

  it('should show the render percent on the cleaned episode too', () => {
    expect(meter(render(rendering, 'episode'))).toBe('57')
  })
})
