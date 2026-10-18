import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { radioService } from '../../services/api'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioCuts from './radio-cuts.vue'
import { contexts, recording } from './radio-fixture'
import type { RecordingSummary } from '../../models/generated/domain-models'

const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }))

vi.mock('../../services/api', () => ({
  radioService: {
    cuts: vi.fn(),
    review: vi.fn(),
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

  it('should play the moment before a cut to hear the transition', async () => {
    await button(await card(1), 'Kuula üleminekut').trigger('click')
    expect(audio.currentTime).toBe(2115)
  })
})
