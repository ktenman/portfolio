import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { radioService } from '../../services/api'
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioText from './radio-text.vue'
import { recording, transcript } from './radio-fixture'
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
})
