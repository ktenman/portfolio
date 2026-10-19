import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioDock from './radio-dock.vue'
import RadioScrubber from './radio-scrubber.vue'
import { editList, recording } from './radio-fixture'

vi.mock('../../composables/use-radio-recordings', () => ({
  useRadioStation: () => ({ data: ref(undefined) }),
}))
vi.mock('../../services/api', () => ({
  radioService: {
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
    liveUrl: '/api/radio/live',
  },
}))

enableAutoUnmount(afterEach)

describe('RadioDock', () => {
  const player = useRadioPlayer()

  const render = () => mount(RadioDock, { global: { stubs: { RouterLink: true } } })

  const loaded = async (item = recording()) => {
    const wrapper = render()
    player.load(item)
    await nextTick()
    return wrapper
  }

  const audio = (wrapper: ReturnType<typeof render>) =>
    wrapper.find('audio').element as HTMLAudioElement

  const back = (wrapper: ReturnType<typeof render>) =>
    wrapper.findAll('button').find(button => button.text() === 'Tagasi praegusele')

  const mute = (wrapper: ReturnType<typeof render>) => wrapper.find('button[aria-label="Vaigista"]')

  beforeEach(() => {
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {})
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
    player.rate.value = 1
    player.volume.value = 1
    player.muted.value = false
  })

  it('should offer to go back to the playing line while the text lost it', async () => {
    const wrapper = await loaded()
    player.lost.value = true
    await nextTick()
    await back(wrapper)?.trigger('click')
    expect(player.lost.value).toBe(false)
  })

  it('should not offer to go back while the text follows the playing line', async () => {
    expect(back(await loaded())).toBeUndefined()
  })

  it('should show the player on Kuula too', async () => {
    expect((await loaded()).find('[data-testid="radio-dock"]').exists()).toBe(true)
  })

  it('should shade the cuts of the raw version on the timeline', async () => {
    const wrapper = await loaded()
    expect(wrapper.findComponent(RadioScrubber).props('parts')).toEqual(recording().timeline)
  })

  it('should mark the chapters of the cleaned episode on the timeline', async () => {
    const wrapper = await loaded()
    const chapters = [
      { start: 0, title: 'Enne saadet' },
      { start: 600, title: 'Rahvusooperi tüli' },
    ]
    player.load(recording({ editList: editList({ chapters }) }), 'episode')
    await nextTick()
    expect(wrapper.findComponent(RadioScrubber).props('parts')).toEqual([
      { start: 0, end: 600, cut: false },
      { start: 600, end: 2512.5, cut: false },
    ])
  })

  it('should switch to the cleaned version', async () => {
    const wrapper = await loaded()
    await wrapper.find('select[aria-label="Versioon"]').setValue('episode')
    expect(player.version.value).toBe('episode')
  })

  it('should not offer a version before the cleaned episode is rendered', async () => {
    const wrapper = await loaded(recording({ editList: null, episodeBytes: null }))
    expect(wrapper.find('select[aria-label="Versioon"]').exists()).toBe(false)
  })

  it('should change the speed', async () => {
    const wrapper = await loaded()
    await wrapper.find('select[aria-label="Kiirus"]').setValue('1.5')
    expect(player.rate.value).toBe(1.5)
  })

  it('should give the position on the station clock', async () => {
    const wrapper = await loaded()
    audio(wrapper).currentTime = 761.5
    audio(wrapper).dispatchEvent(new Event('timeupdate'))
    await nextTick()
    expect(wrapper.text()).toContain('eetris 12:07')
  })

  it('should speak the position in the recording as a clock', async () => {
    const wrapper = await loaded()
    expect(
      wrapper.find('input[aria-label="Asukoht salvestuses"]').attributes('aria-valuetext')
    ).toBe('0:00 / 1:10:00')
  })

  it('should mute the player when the speaker is pressed', async () => {
    const wrapper = await loaded()
    await mute(wrapper).trigger('click')
    expect(audio(wrapper).muted).toBe(true)
  })

  it('should tell that the speaker is pressed', async () => {
    const wrapper = await loaded()
    await mute(wrapper).trigger('click')
    expect(mute(wrapper).attributes('aria-pressed')).toBe('true')
  })

  it('should keep the volume level through a mute', async () => {
    const wrapper = await loaded()
    player.volume.value = 0.4
    await mute(wrapper).trigger('click')
    await mute(wrapper).trigger('click')
    expect(audio(wrapper).volume).toBe(0.4)
  })

  it('should unmute when the volume is moved', async () => {
    const wrapper = await loaded()
    await mute(wrapper).trigger('click')
    await wrapper.find('input[aria-label="Helitugevus"]').setValue('0.6')
    expect(audio(wrapper).muted).toBe(false)
  })

  it('should keep the live station without a timeline', async () => {
    const wrapper = render()
    player.listen()
    await nextTick()
    expect(wrapper.findComponent(RadioScrubber).exists()).toBe(false)
  })

  it('should keep the live station at its own speed', async () => {
    const wrapper = render()
    player.listen()
    await nextTick()
    expect(wrapper.find('select[aria-label="Kiirus"]').exists()).toBe(false)
  })
})
