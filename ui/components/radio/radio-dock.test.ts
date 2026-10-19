import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioDock from './radio-dock.vue'
import { recording } from './radio-fixture'

vi.mock('../../composables/use-radio-recordings', () => ({
  useRadioStation: () => ({ data: ref(undefined) }),
}))
vi.mock('../../services/api', () => ({
  radioService: { fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}` },
}))

enableAutoUnmount(afterEach)

describe('RadioDock', () => {
  const player = useRadioPlayer()

  const render = () =>
    mount(RadioDock, {
      global: { stubs: { RadioControls: true, RouterLink: true } },
    })

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

  it("should seek from the line along the dock's top edge", async () => {
    const wrapper = render()
    player.load(recording())
    await wrapper.vm.$nextTick()
    await wrapper.find('[data-testid="radio-dock-line"]').setValue('120')
    expect(player.seconds.value).toBe(120)
  })

  it('should offer to go back to the playing line from the dock', async () => {
    const wrapper = render()
    player.load(recording())
    player.lost.value = true
    await wrapper.vm.$nextTick()
    await wrapper
      .findAll('button')
      .find(b => b.text() === 'Tagasi praegusele')!
      .trigger('click')
    expect(player.lost.value).toBe(false)
  })
})
