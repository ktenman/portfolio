import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioDock from './radio-dock.vue'
import { recording } from './radio-fixture'

vi.mock('vue-router', () => ({ useRoute: () => ({ name: 'RadioText' }) }))
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
      global: { stubs: { RadioControls: true, RadioScrubber: true, RouterLink: true } },
    })

  const back = (wrapper: ReturnType<typeof render>) =>
    wrapper.findAll('button').find(button => button.text() === 'Tagasi praegusele')

  beforeEach(() => vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {}))

  it('should offer to go back to the playing line while the text lost it', async () => {
    const wrapper = render()
    player.load(recording())
    player.lost.value = true
    await wrapper.vm.$nextTick()
    await back(wrapper)?.trigger('click')
    expect(player.lost.value).toBe(false)
  })

  it('should not offer to go back while the text follows the playing line', async () => {
    const wrapper = render()
    player.load(recording())
    await wrapper.vm.$nextTick()
    expect(back(wrapper)).toBeUndefined()
  })
})
