import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { radioService } from '../../services/api'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioText from './radio-text.vue'
import { recording, sections, song, transcript } from './radio-fixture'
import type { RecordingSummary } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  radioService: {
    transcript: vi.fn(),
    cuts: () => Promise.resolve([]),
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
    Element.prototype.scrollIntoView = vi.fn()
    vi.mocked(radioService.transcript).mockResolvedValue(sections())
    audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    player.attach(audio)
    player.load(recording())
  })

  afterEach(() => player.detach())

  const render = async (target: RecordingSummary = recording()) => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
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

  const rail = (wrapper: Awaited<ReturnType<typeof render>>) =>
    wrapper.find('[data-testid="radio-topics"]').findAll('[data-testid="radio-topic"]')

  it('should show a heading for each topic with its time and length', async () => {
    expect(headings(await render())[1]).toBe('12:07Rahvusooperi tüli23 min')
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
    await wrapper.findAll('[data-testid="radio-section-heading"]')[3].trigger('click')
    expect(audio.currentTime).toBe(2255)
  })

  it('should drop headings without matching lines when searching', async () => {
    const wrapper = await render()
    await wrapper.find('input[type="search"]').setValue('jätkab')
    expect(headings(wrapper)).toEqual(['12:32Rahvusooperi tüli7 min'])
  })

  it('should mark the playing topic Praegu', async () => {
    const wrapper = await render()
    await hear(wrapper, 2257)
    expect(wrapper.find('[data-testid="radio-section"][aria-current="true"]').text()).toContain(
      'Praegu'
    )
  })

  it('should list each topic once in Teemad with its length', async () => {
    expect(rail(await render())[1].text()).toBe('12:07Rahvusooperi tüli 23 min')
  })

  it('should mark a cut lõige in Teemad on the raw recording', async () => {
    expect(rail(await render())[0].text()).toContain('lõige')
  })

  it('should play and scroll to a topic picked from Teemad', async () => {
    const wrapper = await render()
    await rail(wrapper)[3].trigger('click')
    await wrapper.vm.$nextTick()
    expect([audio.currentTime, scrolled().length > 0]).toEqual([2255, true])
  })

  it('should offer Teemad as chips above the search', async () => {
    const chips = (await render()).find('[data-testid="radio-topic-chips"]')
    expect(chips.findAll('[data-testid="radio-topic"]')).toHaveLength(9)
  })

  it('should list the ad breaks the machine found', async () => {
    expect((await render()).find('[data-testid="radio-ads"]').text()).toContain(
      '12:30–12:322 min 15 s'
    )
  })

  it('should say no ad break was found when the machine found none', async () => {
    const wrapper = await render(recording({ cuts: [] }))
    expect(wrapper.find('[data-testid="radio-ads"]').text()).toContain('Ei leitud')
  })

  it('should name the model that found the topics and breaks', async () => {
    expect((await render()).find('[data-testid="radio-detection"]').text()).toContain(
      'deepseek/deepseek-v4-flash'
    )
  })

  it('should show each line of the transcript', async () => {
    expect(lines(await render())).toHaveLength(3)
  })

  it('should show a run of music as one Muusika row with its time', async () => {
    const [first, ...rest] = transcript()
    vi.mocked(radioService.transcript).mockResolvedValue(sections([first, song(), ...rest]))
    expect(lines(await render())[1].text()).toBe('12:08:15Muusika')
  })

  it('should leave music out of the search', async () => {
    vi.mocked(radioService.transcript).mockResolvedValue(sections([song()]))
    const wrapper = await render()
    await wrapper.find('input[type="search"]').setValue('Muusika')
    expect(lines(wrapper)).toHaveLength(0)
  })

  it('should keep only the lines that contain the search', async () => {
    const wrapper = await render()
    await wrapper.find('input[type="search"]').setValue('KESKPÄEVATUND')
    expect(lines(wrapper).map(l => l.text())).toEqual(['12:32:36Ja Keskpäevatund jätkab.'])
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
    expect(lines(await render())[1].text()).toContain(
      'Lõige poolitab lauseReklaamipaus 12:30–12:32'
    )
  })

  it('should not offer to mark the transcript checked', async () => {
    expect((await render()).text()).not.toContain('Märgi kontrollituks')
  })

  it('should not label a transcript that is there', async () => {
    expect((await render()).find('[data-testid="radio-transcript"]').text()).not.toContain('Olemas')
  })

  it('should offer the transcript as subtitles and as text', async () => {
    const links = (await render())
      .findAll('a[download]')
      .map(a => a.attributes('href'))
      .filter(href => href?.includes('transcript'))
    expect(links).toEqual([
      '/api/radio/2026-10-17/transcript.srt',
      '/api/radio/2026-10-17/transcript.txt',
    ])
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

  it('should not follow while the search filters the text', async () => {
    const wrapper = await render()
    play()
    await wrapper.find('input[type="search"]').setValue('Tere')
    await hear(wrapper, 762.5)
    expect(scrolled()).toEqual([])
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

  it('should not stop following on keys typed into the search box', async () => {
    const wrapper = mount(RadioText, {
      props: { recording: recording() },
      attachTo: document.body,
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }]] },
    })
    await flushPromises()
    await hear(wrapper, 762.5)
    await wrapper.find('input[type="search"]').trigger('keydown', { key: ' ' })
    expect(player.lost.value).toBe(false)
  })

  it('should not lose a line that is not playing', async () => {
    await render()
    window.dispatchEvent(new WheelEvent('wheel'))
    expect(player.lost.value).toBe(false)
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
