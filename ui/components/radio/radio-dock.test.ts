import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { reactive, ref } from 'vue'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioDock from './radio-dock.vue'
import { recording } from './radio-fixture'

const route = reactive({ params: {} as Record<string, string> })

vi.mock('vue-router', () => ({ useRoute: () => route }))
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

  beforeEach(() => {
    route.params = {}
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
  })

  afterEach(() => player.detach())

  it('should hide the dock on a recording page', async () => {
    route.params = { name: '2026-10-17' }
    const wrapper = render()
    player.load(recording())
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-dock"]').exists()).toBe(false)
  })

  it('should show the dock on other pages while a recording plays', async () => {
    const wrapper = render()
    player.load(recording())
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-dock"]').exists()).toBe(true)
  })

  it('should show the dock for live radio on a recording page', async () => {
    route.params = { name: '2026-10-17' }
    const wrapper = render()
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
    player.listen()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-dock"]').exists()).toBe(true)
  })
})
