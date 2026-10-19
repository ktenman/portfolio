import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { radioService } from '../../services/api'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioText from './radio-text.vue'
import { recording, song, transcript } from './radio-fixture'
import { type RecordingSummary, TranscriptState } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  radioService: {
    transcript: vi.fn(),
    check: vi.fn(),
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
    vi.mocked(radioService.transcript).mockResolvedValue(transcript())
    vi.mocked(radioService.check).mockReset().mockResolvedValue()
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

  it('should list each topic with its length', async () => {
    const topics = (await render()).findAll('[data-testid="radio-topic"]').map(t => t.text())
    expect(topics[0]).toBe('12:07Rahvusooperi tüli23 min')
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
    vi.mocked(radioService.transcript).mockResolvedValue([first, song(), ...rest])
    expect(lines(await render())[1].text()).toBe('12:08:15Muusika')
  })

  it('should leave music out of the search', async () => {
    vi.mocked(radioService.transcript).mockResolvedValue([song()])
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

  it('should mark the transcript checked', async () => {
    const wrapper = await render()
    await wrapper
      .findAll('button')
      .find(b => b.text().includes('Märgi kontrollituks'))!
      .trigger('click')
    expect(radioService.check).toHaveBeenCalledWith('2026-10-17')
  })

  it('should say a checked transcript is checked', async () => {
    const wrapper = await render(
      recording({
        transcriptCheckedAt: '2026-10-18T09:00:00Z',
        transcriptState: TranscriptState.CHECKED,
      })
    )
    expect(wrapper.find('[data-testid="radio-text-state"]').text()).toBe('Kontrollitud')
  })

  it('should offer the transcript as subtitles and as text', async () => {
    const links = (await render()).findAll('a[download]').map(a => a.attributes('href'))
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
})
