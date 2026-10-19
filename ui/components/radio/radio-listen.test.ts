import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import RadioListen from './radio-listen.vue'
import { recording } from './radio-fixture'
import { useRadioPlayer } from '../../composables/use-radio-player'

vi.mock('../../services/api', () => ({
  radioService: { fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}` },
}))

enableAutoUnmount(afterEach)

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
})

describe('RadioListen', () => {
  const player = useRadioPlayer()
  let audio: HTMLAudioElement

  beforeEach(() => {
    audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    player.attach(audio)
    player.load(recording())
  })

  afterEach(() => player.detach())

  const render = (item = recording()) =>
    mount(RadioListen, { props: { recording: item }, global: { plugins: [router] } })

  it('should list the cut suggestions and topics on the timeline', () => {
    expect(render().findAll('[data-testid="radio-timeline-row"]')).toHaveLength(9)
  })

  it('should mark the part being played now', () => {
    const wrapper = render()
    audio.currentTime = 800
    audio.dispatchEvent(new Event('timeupdate'))
    return wrapper.vm.$nextTick().then(() => {
      const now = wrapper.find('[data-testid="radio-timeline-row"][aria-current="true"]')
      expect(now.text()).toContain('Rahvusooperi tüli')
    })
  })

  it('should note how often the stream dropped', () => {
    expect(render(recording({ drops: 2 })).text()).toContain(
      'Voog katkes 2 korda, midagi ei kadunud'
    )
  })

  it('should not note drops of a stream that never dropped', () => {
    expect(render().text()).not.toContain('Voog katkes')
  })

  it('should show the checksum of each recorded part', () => {
    expect(render().text()).toContain('SHA-256ab12')
  })

  it('should not draw a second player', () => {
    expect(render().find('[data-testid="radio-play"]').exists()).toBe(false)
  })

  it('should jump to the start of the show', async () => {
    const wrapper = render()
    await wrapper.find('[data-testid="radio-show-start"]').trigger('click')
    expect(audio.currentTime).toBe(761.5)
  })
})
