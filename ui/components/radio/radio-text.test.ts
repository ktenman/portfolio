import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { radioService } from '../../services/api'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioCutCard from './radio-cut-card.vue'
import RadioResult from './radio-result.vue'
import RadioText from './radio-text.vue'
import { recording, sections, song, transcript } from './radio-fixture'
import type { RecordingSummary } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  radioService: {
    position: () => Promise.resolve({ seconds: 0 }),
    savePosition: () => Promise.resolve(),
    transcript: vi.fn(),
    cuts: () => Promise.resolve([]),
    dropCut: () => Promise.resolve(),
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
  },
}))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

describe('RadioText', () => {
  const player = useRadioPlayer()
  let audio: HTMLAudioElement

  beforeEach(() => {
    localStorage.clear()
    Element.prototype.scrollIntoView = vi.fn()
    vi.mocked(radioService.transcript).mockReset().mockResolvedValue(sections())
    audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    player.attach(audio)
    player.load(recording())
  })

  afterEach(() => player.close())

  const render = async (
    target: RecordingSummary = recording(),
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  ) => {
    const wrapper = mount(RadioText, {
      props: { recording: target },
      global: { plugins: [[VueQueryPlugin, { queryClient }]] },
    })
    await flushPromises()
    return wrapper
  }

  const lines = (wrapper: Awaited<ReturnType<typeof render>>) =>
    wrapper.findAll('[data-testid="radio-text-line"]')

  const scrolled = () => vi.mocked(Element.prototype.scrollIntoView).mock.contexts

  const play = () => audio.dispatchEvent(new Event('play'))

  const hear = async (wrapper: Awaited<ReturnType<typeof render>>, seconds: number) => {
    audio.currentTime = seconds
    audio.dispatchEvent(new Event('timeupdate'))
    await wrapper.vm.$nextTick()
  }

  const headings = (wrapper: Awaited<ReturnType<typeof render>>) =>
    wrapper.findAll('[data-testid="radio-section-heading"]').map(h => h.text())

  it('should leave the text to screens wider than a phone', async () => {
    expect((await render()).get('[data-testid="radio-transcript"]').classes()).toContain(
      'max-md:hidden'
    )
  })

  it('should show a heading for each topic with its time and length', async () => {
    expect(headings(await render())[0]).toBe('12:07Rahvusooperi tüli23 min')
  })

  it('should hide cut sections in the cleaned version', async () => {
    player.load(recording(), 'episode')
    expect(headings(await render())).toHaveLength(5)
  })

  it('should show the episode time on headings in the cleaned version', async () => {
    player.load(recording(), 'episode')
    expect(headings(await render())[1]).toBe('22:38Rahvusooperi tüli7 min')
  })

  it('should play the episode from a line in the cleaned version', async () => {
    player.load(recording(), 'episode')
    const wrapper = await render()
    await lines(wrapper)[2].find('button').trigger('click')
    expect(player.seconds.value).toBe(1359.5)
  })

  it('should play a topic from its heading', async () => {
    const wrapper = await render()
    await wrapper.findAll('[data-testid="radio-section-heading"]')[1].trigger('click')
    expect(audio.currentTime).toBe(2255)
  })

  it('should mark the playing topic without a badge', async () => {
    const wrapper = await render()
    await hear(wrapper, 2257)
    expect(wrapper.get('[data-testid="radio-section"][aria-current="true"]').text()).not.toContain(
      'Now'
    )
  })

  it('should regroup the text once the episode is cut again', async () => {
    const wrapper = await render()
    await wrapper.setProps({ recording: recording({ audible: [{ start: 761.5, end: 3837 }] }) })
    await flushPromises()
    expect(radioService.transcript).toHaveBeenCalledTimes(2)
  })

  it('should keep the text on screen while it is regrouped', async () => {
    const wrapper = await render()
    vi.mocked(radioService.transcript).mockReturnValue(new Promise(() => {}))
    await wrapper.setProps({ recording: recording({ timeline: recording().timeline.slice(1) }) })
    expect(lines(wrapper)).toHaveLength(3)
  })

  it('should show one card for the detection and the processing', async () => {
    expect((await render()).text()).not.toContain('Detection')
  })

  it('should show each line of the transcript', async () => {
    expect(lines(await render())).toHaveLength(3)
  })

  it('should show a run of music as one Music row with its time', async () => {
    const [first, ...rest] = transcript()
    vi.mocked(radioService.transcript).mockResolvedValue(sections([first, song(), ...rest]))
    expect(lines(await render())[1].text()).toBe('12:08:15Music')
  })

  it('should mark only the spoken lines as Estonian', async () => {
    const [first, ...rest] = transcript()
    vi.mocked(radioService.transcript).mockResolvedValue(sections([first, song(), ...rest]))
    const marked = lines(await render()).map(line => line.find('p').attributes('lang'))
    expect(marked.slice(0, 2)).toEqual(['et', undefined])
  })

  it('should play the recording from the time of a line', async () => {
    await lines(await render())[2]
      .find('button')
      .trigger('click')
    expect(audio.currentTime).toBe(2256)
  })

  it('should highlight the word being played', async () => {
    const wrapper = await render()
    audio.currentTime = 763.5
    audio.dispatchEvent(new Event('timeupdate'))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-word-now"]').text()).toBe('päevast,')
  })

  it('should say where a cut splits a sentence', async () => {
    expect(lines(await render())[1].text()).toContain('Cut splits a sentenceAd break 12:30–12:32')
  })

  it('should not offer to mark the transcript checked', async () => {
    expect((await render()).text()).not.toContain('Mark as checked')
  })

  it('should not label a transcript that is there', async () => {
    expect((await render()).find('[data-testid="radio-transcript"]').text()).not.toContain('Ready')
  })

  it('should offer no text search', async () => {
    expect((await render()).find('input[type="search"]').exists()).toBe(false)
  })

  it('should list the topics in the rail', async () => {
    player.load(recording(), 'episode')
    expect((await render()).findAll('[data-testid="radio-topics-row"]')).toHaveLength(3)
  })

  it('should play a topic from the rail', async () => {
    player.load(recording(), 'episode')
    const wrapper = await render()
    await wrapper.findAll('[data-testid="radio-topics-row"]')[1]!.trigger('click')
    expect(player.seconds.value).toBe(1803.5)
  })

  it('should leave out the cut summary on the cleaned version', async () => {
    player.load(recording(), 'episode')
    expect((await render()).findComponent(RadioResult).exists()).toBe(false)
  })

  it('should mark the search results stale once a cut is deleted', async () => {
    const client = new QueryClient()
    client.setQueryData(['radio', 'search', 'valimised'], { pages: [], pageParams: [] })
    const wrapper = await render(recording(), client)
    wrapper.findComponent(RadioCutCard).vm.$emit('drop')
    await flushPromises()
    expect(client.getQueryState(['radio', 'search', 'valimised'])?.isInvalidated).toBe(true)
  })

  it('should keep the cut summary on the raw recording', async () => {
    expect((await render()).findComponent(RadioResult).exists()).toBe(true)
  })

  it('should leave out the ad breaks', async () => {
    expect((await render()).find('[data-testid="radio-ads"]').exists()).toBe(false)
  })

  it('should offer no transcript downloads', async () => {
    const links = (await render()).findAll('a[download]').map(a => a.attributes('href'))
    expect(links.filter(href => href?.includes('transcript'))).toEqual([])
  })

  it('should follow the playing line while playing', async () => {
    const wrapper = await render()
    play()
    await hear(wrapper, 762.5)
    await hear(wrapper, 2257)
    expect(scrolled()).toEqual([lines(wrapper)[0].element, lines(wrapper)[2].element])
  })

  it('should center the playing line', async () => {
    const wrapper = await render()
    play()
    await hear(wrapper, 762.5)
    expect(vi.mocked(Element.prototype.scrollIntoView)).toHaveBeenCalledWith({
      block: 'center',
      behavior: 'smooth',
    })
  })

  it('should not follow while paused after the first jump', async () => {
    const wrapper = await render()
    await hear(wrapper, 762.5)
    await hear(wrapper, 2257)
    expect(scrolled()).toEqual([lines(wrapper)[0].element])
  })

  it('should jump once to the current line when opened while paused', async () => {
    audio.currentTime = 2257
    audio.dispatchEvent(new Event('timeupdate'))
    const wrapper = await render()
    expect(scrolled()).toEqual([lines(wrapper)[2].element])
  })

  it('should stop following after a wheel scroll', async () => {
    const wrapper = await render()
    play()
    await hear(wrapper, 762.5)
    window.dispatchEvent(new WheelEvent('wheel'))
    await hear(wrapper, 2257)
    expect(scrolled()).toEqual([lines(wrapper)[0].element])
  })

  it('should stop following after a page key', async () => {
    const wrapper = await render()
    await hear(wrapper, 762.5)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'PageDown' }))
    expect(player.lost.value).toBe(true)
  })

  it('should not stop following on a space pressed on a heading', async () => {
    const wrapper = mount(RadioText, {
      props: { recording: recording() },
      attachTo: document.body,
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }]] },
    })
    await flushPromises()
    await hear(wrapper, 762.5)
    await wrapper.get('[data-testid="radio-section-heading"]').trigger('keydown', { key: ' ' })
    expect(player.lost.value).toBe(false)
  })

  it('should not lose a line that is not playing', async () => {
    await render()
    window.dispatchEvent(new WheelEvent('wheel'))
    expect(player.lost.value).toBe(false)
  })

  const attached = async () => {
    const wrapper = mount(RadioText, {
      props: { recording: recording() },
      attachTo: document.body,
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }]] },
    })
    await flushPromises()
    return wrapper
  }

  const scrollable = (wrapper: Awaited<ReturnType<typeof render>>) =>
    Object.defineProperty(wrapper.get('[data-testid="radio-rail"]').element, 'scrollHeight', {
      value: 900,
    })

  it('should keep following when the rail is scrolled', async () => {
    const wrapper = await attached()
    scrollable(wrapper)
    await hear(wrapper, 762.5)
    wrapper
      .get('[data-testid="radio-details"]')
      .element.dispatchEvent(new WheelEvent('wheel', { bubbles: true }))
    expect(player.lost.value).toBe(false)
  })

  it('should keep following when a key scrolls the rail', async () => {
    const wrapper = await attached()
    scrollable(wrapper)
    await hear(wrapper, 762.5)
    await wrapper.get('[data-testid="radio-details"]').trigger('keydown', { key: 'ArrowDown' })
    expect(player.lost.value).toBe(false)
  })

  it('should stop following after a wheel on a rail that cannot scroll', async () => {
    const wrapper = await attached()
    await hear(wrapper, 762.5)
    wrapper
      .get('[data-testid="radio-details"]')
      .element.dispatchEvent(new WheelEvent('wheel', { bubbles: true }))
    expect(player.lost.value).toBe(true)
  })

  it('should follow again after going back to the playing line', async () => {
    const wrapper = await render()
    await hear(wrapper, 762.5)
    window.dispatchEvent(new WheelEvent('wheel'))
    await wrapper.vm.$nextTick()
    player.rejoin()
    await wrapper.vm.$nextTick()
    expect(scrolled()).toEqual([lines(wrapper)[0].element, lines(wrapper)[0].element])
  })

  it('should forget a lost line when the tab closes', async () => {
    const wrapper = await render()
    await hear(wrapper, 762.5)
    window.dispatchEvent(new WheelEvent('wheel'))
    wrapper.unmount()
    expect(player.lost.value).toBe(false)
  })

  it('should mark the spoken word without changing its weight', async () => {
    const wrapper = await render()
    await hear(wrapper, 763.5)
    const word = wrapper.find('[data-testid="radio-word-now"]')
    expect(word.classes().filter(name => name.startsWith('font-'))).toEqual([])
  })

  const touch = (type: string, y: number) =>
    window.dispatchEvent(Object.assign(new Event(type), { touches: [{ clientY: y }] }))

  it('should stop following after a scroll in a pause between lines', async () => {
    const wrapper = await render()
    play()
    await hear(wrapper, 1000)
    window.dispatchEvent(new WheelEvent('wheel'))
    expect(player.lost.value).toBe(true)
  })

  it('should stop following after an arrow key on a focused time', async () => {
    const wrapper = mount(RadioText, {
      props: { recording: recording() },
      attachTo: document.body,
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }]] },
    })
    await flushPromises()
    await hear(wrapper, 762.5)
    await lines(wrapper)[0].find('button').trigger('keydown', { key: 'ArrowDown' })
    expect(player.lost.value).toBe(true)
  })

  it('should keep following after a finger jitters on a tap', async () => {
    const wrapper = await render()
    await hear(wrapper, 762.5)
    touch('touchstart', 300)
    touch('touchmove', 304)
    expect(player.lost.value).toBe(false)
  })

  it('should stop following after a finger drags the text', async () => {
    const wrapper = await render()
    await hear(wrapper, 762.5)
    touch('touchstart', 300)
    touch('touchmove', 240)
    expect(player.lost.value).toBe(true)
  })
})
