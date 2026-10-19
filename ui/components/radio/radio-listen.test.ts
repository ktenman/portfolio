import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import RadioListen from './radio-listen.vue'
import { recording } from './radio-fixture'
import { useRadioPlayer, type Version } from '../../composables/use-radio-player'
import type { RecordingSummary } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  radioService: {
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
    cuts: () => Promise.resolve([]),
  },
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
    vi.spyOn(audio, 'load').mockImplementation(() => {})
    player.attach(audio)
  })

  afterEach(() => player.detach())

  const render = (item: RecordingSummary = recording(), version: Version = 'raw') => {
    player.load(item, version)
    const queryClient = new QueryClient()
    return mount(RadioListen, {
      props: { recording: item },
      global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
    })
  }

  const chaptered = () =>
    recording({
      editList: {
        ...recording().editList!,
        chapters: [
          { start: 0, title: 'Rahvusooperi tüli' },
          { start: 1358.5, title: 'Mis toimub poliitikas?' },
          { start: 2128.5, title: 'President Ülle Madise' },
        ],
      },
    })

  const chapters = (wrapper: ReturnType<typeof render>) =>
    wrapper.findAll('[data-testid="radio-chapter"]')

  it('should show the cut rows and Tulemus on the raw recording', () => {
    const wrapper = render()
    expect([
      wrapper.findAll('[data-testid="radio-cut"]').length,
      wrapper.text().includes('Tulemus'),
    ]).toEqual([4, true])
  })

  it('should show the chapters and no cut rows on the cleaned episode', () => {
    const wrapper = render(chaptered(), 'episode')
    expect([chapters(wrapper).length, wrapper.find('[data-testid="radio-cut"]').exists()]).toEqual([
      3,
      false,
    ])
  })

  it('should play a chapter in the cleaned episode', async () => {
    const wrapper = render(chaptered(), 'episode')
    await chapters(wrapper)[1]!.trigger('click')
    expect([player.version.value, audio.currentTime]).toEqual(['episode', 1358.5])
  })

  it('should mark the chapter that plays', async () => {
    const wrapper = render(chaptered(), 'episode')
    audio.currentTime = 1500
    audio.dispatchEvent(new Event('timeupdate'))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-chapter"][aria-current="true"]').text()).toContain(
      'Mis toimub poliitikas?'
    )
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
})
