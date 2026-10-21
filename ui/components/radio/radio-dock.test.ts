import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount, RouterLinkStub } from '@vue/test-utils'
import { ref } from 'vue'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioDock from './radio-dock.vue'
import { recording } from './radio-fixture'

vi.mock('../../composables/use-radio-recordings', () => ({
  useRadioStation: () => ({ data: ref(undefined) }),
}))
vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries: () => Promise.resolve() }),
}))
vi.mock('../../services/api', () => ({
  radioService: {
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
    review: () => Promise.resolve(),
  },
}))

enableAutoUnmount(afterEach)

describe('RadioDock', () => {
  const player = useRadioPlayer()

  const render = (stubs: Record<string, unknown> = { RadioControls: true }) =>
    mount(RadioDock, { global: { stubs: { RouterLink: RouterLinkStub, ...stubs } } })

  const hatched = (wrapper: ReturnType<typeof render>) =>
    wrapper
      .findAll('[data-testid="radio-timeline-part"]')
      .filter(part => part.classes().join(' ').includes('repeating-linear-gradient')).length

  beforeEach(() => {
    player.cardInView.value = false
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
  })

  afterEach(() => player.detach())

  it('should hide the dock while the player card is in view', async () => {
    const wrapper = render()
    player.load(recording())
    player.cardInView.value = true
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-dock"]').exists()).toBe(false)
  })

  it('should show the dock once the player card scrolls away', async () => {
    const wrapper = render()
    player.load(recording())
    player.cardInView.value = true
    await wrapper.vm.$nextTick()
    player.cardInView.value = false
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-dock"]').exists()).toBe(true)
  })

  it('should show the dock on other pages while a recording plays', async () => {
    const wrapper = render()
    player.load(recording())
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-dock"]').exists()).toBe(true)
  })

  it('should show the dock for live radio while the player card is in view', async () => {
    const wrapper = render()
    player.cardInView.value = true
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
    player.listen()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-dock"]').exists()).toBe(true)
  })

  it('should seek from the dock timeline', async () => {
    const wrapper = render()
    player.load(recording())
    await wrapper.vm.$nextTick()
    await wrapper.get('input[aria-label="Asukoht salvestuses"]').setValue('120')
    expect(player.seconds.value).toBe(120)
  })

  it("should show the recording's timeline in the dock", async () => {
    const wrapper = render()
    player.load(recording(), 'episode')
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('[data-testid="radio-timeline-part"]')).toHaveLength(3)
  })

  it('should leave the topic labels out of the dock', async () => {
    const wrapper = render()
    player.load(recording(), 'episode')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-timeline-labels"]').exists()).toBe(false)
  })

  it('should name the playing topic in the dock', async () => {
    const wrapper = render()
    player.load(recording(), 'episode')
    player.seek(1900)
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Mis toimub poliitikas?')
  })

  it('should name the break while an ad plays in the raw recording', async () => {
    const wrapper = render()
    player.load(recording())
    player.seek(2150)
    await wrapper.vm.$nextTick()
    expect([wrapper.text().includes('Reklaamipaus'), wrapper.text().includes('Rahvusooperi')]).toEqual([
      true,
      false,
    ])
  })

  it('should name the programme while a recording has no topics', async () => {
    const wrapper = render()
    player.load(recording({ timeline: [] }))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Keskpäevatund')
  })

  it('should hatch a cut decided in the text before it saves', async () => {
    const item = recording()
    const wrapper = render()
    player.load(item)
    await wrapper.vm.$nextTick()
    const before = hatched(wrapper)
    useRadioDecisions(() => item).choose(1, false)
    await wrapper.vm.$nextTick()
    expect(hatched(wrapper)).toBe(before - 1)
  })

  it('should leave the timeline out of live radio', async () => {
    const wrapper = render()
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
    player.listen()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('input[aria-label="Asukoht salvestuses"]').exists()).toBe(false)
  })

  it('should not offer to go back to the playing line', async () => {
    const wrapper = render()
    player.load(recording())
    player.lost.value = true
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('button').some(b => b.text() === 'Tagasi praegusele')).toBe(false)
  })

  it('should offer the speed chip in the dock', async () => {
    const wrapper = render({})
    player.load(recording())
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-speed"]').exists()).toBe(true)
  })

  it('should leave the speed chip out of live radio', async () => {
    const wrapper = render({})
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
    player.listen()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-speed"]').exists()).toBe(false)
  })
})
