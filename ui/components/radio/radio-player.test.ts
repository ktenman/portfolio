import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import RadioPlayer from './radio-player.vue'
import { recording } from './radio-fixture'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer, type Version } from '../../composables/use-radio-player'

vi.mock('../../services/api', () => ({
  radioService: {
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
    review: () => Promise.resolve(),
  },
}))
vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries: () => Promise.resolve() }),
}))

enableAutoUnmount(afterEach)

describe('RadioPlayer', () => {
  const player = useRadioPlayer()
  let audio: HTMLAudioElement

  beforeEach(() => {
    audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    vi.spyOn(audio, 'load').mockImplementation(() => {})
    player.attach(audio)
  })

  afterEach(() => player.detach())

  const render = (version: Version = 'raw', item = recording()) => {
    player.load(item, version)
    return mount(RadioPlayer, { props: { recording: item } })
  }

  const labels = (wrapper: ReturnType<typeof render>) =>
    wrapper.findAll('[data-testid="radio-timeline-label"]')

  const timeline = (wrapper: ReturnType<typeof render>) =>
    wrapper.findAll('[data-testid="radio-timeline-part"]')

  const hatched = (part: { classes: () => string[] }) =>
    part.classes().join(' ').includes('repeating-linear-gradient')

  it('should hatch a cut as soon as it is kept', async () => {
    const item = recording()
    const wrapper = render('raw', item)
    useRadioDecisions(() => item).choose(1, false)
    await wrapper.vm.$nextTick()
    expect(hatched(timeline(wrapper)[2]!)).toBe(false)
  })

  it('should play the raw topic clicked on Toorsalvestus', async () => {
    const wrapper = render()
    await labels(wrapper)[1]!.trigger('click')
    expect([player.version.value, player.recordingSeconds.value]).toEqual(['raw', 2700])
  })

  it('should play the episode topic clicked on Puhastatud', async () => {
    const wrapper = render('episode')
    await labels(wrapper)[1]!.trigger('click')
    expect([player.version.value, audio.currentTime]).toEqual(['episode', 1803.5])
  })

  it('should label a resumed topic once', () => {
    const [first, second, third] = recording().topics
    const pause = { ...first!, start: 900, end: 1000, title: 'Reklaamipaus', cut: true }
    const topics = [{ ...first!, end: 900 }, pause, { ...first!, start: 1000 }, second!, third!]
    const wrapper = render('episode', recording({ topics }))
    expect(labels(wrapper).map(label => label.findAll('span').map(span => span.text()))).toEqual([
      ['0:00', 'Ooperitüli'],
      ['30:03', 'Poliitika'],
      ['39:51', 'Ülle Madise'],
    ])
  })

  it('should keep a played removed part faded on the timeline', async () => {
    const wrapper = render()
    await labels(wrapper)[1]!.trigger('click')
    expect(timeline(wrapper).find(hatched)!.get('div').classes()).toContain('bg-ink/40')
  })

  it('should jump to the spot tapped on the timeline', async () => {
    const wrapper = render()
    const input = wrapper.get('input[aria-label="Asukoht salvestuses"]')
    vi.spyOn(input.element, 'getBoundingClientRect').mockReturnValue(new DOMRect(10, 0, 200, 28))
    await input.trigger('click', { clientX: 160 })
    expect(player.recordingSeconds.value).toBe(Math.round(player.total.value * 0.75))
  })

  it('should play the next topic from the playing topic', async () => {
    const wrapper = render('episode')
    await wrapper.get('button[aria-label="Järgmine teema"]').trigger('click')
    expect(audio.currentTime).toBe(1803.5)
  })

  it('should not show the playing topic on the raw recording', () => {
    expect(render().find('[data-testid="radio-now"]').exists()).toBe(false)
  })

  it('should leave out the labels while the recording has no parts yet', () => {
    const wrapper = render('raw', recording({ timeline: [] }))
    expect(wrapper.find('[data-testid="radio-timeline-labels"]').exists()).toBe(false)
  })

  it('should leave out the parts row and its ticks', () => {
    expect(render().text()).not.toContain('Klõpsa osal')
  })

  it('should offer the start of the show only on the raw recording', () => {
    expect(render('episode').find('[data-testid="radio-show-start"]').exists()).toBe(false)
  })

  it('should jump to the start of the show', async () => {
    const wrapper = render()
    await wrapper.find('[data-testid="radio-show-start"]').trigger('click')
    expect(audio.currentTime).toBe(761.5)
  })

  it('should mute from the speaker', async () => {
    const wrapper = render()
    await wrapper.find('button[aria-label="Vaigista"]').trigger('click')
    expect([
      wrapper.find('button[aria-label="Vaigista"]').attributes('aria-pressed'),
      player.muted.value,
    ]).toEqual(['true', true])
  })

  it('should show the speaker as a sunken button', () => {
    expect(render().get('button[aria-label="Vaigista"]').classes()).toContain('bg-surface-sunken')
  })

  it('should unmute when the volume slider moves', async () => {
    const wrapper = render()
    player.muted.value = true
    await wrapper.get('input[aria-label="Helitugevus"]').setValue(0.4)
    expect([player.muted.value, player.volume.value]).toEqual([false, 0.4])
  })

  it('should read the volume as a percentage', async () => {
    const wrapper = render()
    player.volume.value = 0.4
    await wrapper.vm.$nextTick()
    expect(wrapper.get('input[aria-label="Helitugevus"]').attributes('aria-valuetext')).toBe('40%')
  })

  it('should fill the volume slider up to its level', async () => {
    const wrapper = render()
    player.volume.value = 0.4
    await wrapper.vm.$nextTick()
    expect(wrapper.get('input[aria-label="Helitugevus"]').attributes('style')).toContain(
      '--level: 40%'
    )
  })

  it('should grey the volume slider while muted', async () => {
    const wrapper = render()
    player.muted.value = true
    await wrapper.vm.$nextTick()
    expect(wrapper.get('input[aria-label="Helitugevus"]').classes()).toContain('muted')
  })

  it('should step the speed from the chip', async () => {
    const wrapper = render()
    player.rate.value = 1
    const rates = []
    for (let tap = 0; tap < 4; tap++) {
      await wrapper.get('[data-testid="radio-speed"]').trigger('click')
      rates.push(player.rate.value)
    }
    expect(rates).toEqual([1.25, 1.5, 2, 1])
  })

  it('should step an unlisted speed back to normal', async () => {
    const wrapper = render()
    player.rate.value = 1.75
    await wrapper.get('[data-testid="radio-speed"]').trigger('click')
    expect(player.rate.value).toBe(1)
  })

  it('should leave speed to the chip alone', () => {
    const selects = render().findAll('select')
    expect(selects.map(select => select.attributes('aria-label'))).toEqual(['Versioon'])
  })

  it('should offer the speed chip at every width', () => {
    expect(render().get('[data-testid="radio-speed"]').classes()).not.toContain('md:hidden')
  })

  it('should put the start of the show under the controls', () => {
    const wrapper = render()
    const pad = wrapper.get('[data-testid="radio-play"]').element.parentElement!.parentElement!
    expect(pad.contains(wrapper.get('[data-testid="radio-show-start"]').element)).toBe(false)
  })

  it('should keep the phone pad in equal columns on a live stream', async () => {
    const wrapper = render()
    player.live.value = true
    await wrapper.vm.$nextTick()
    const pad = wrapper.get('[data-testid="radio-play"]').element.parentElement!.parentElement!
    expect([pad.classList.contains('max-md:auto-cols-fr'), wrapper.text()]).toEqual([
      true,
      expect.not.stringContaining('15'),
    ])
  })

  it('should speak the position in the recording as a clock', () => {
    expect(render().find('input[type="range"]').attributes('aria-valuetext')).toBe('0:00 / 1:10:00')
  })

  it('should give the position on the station clock', async () => {
    const wrapper = render()
    audio.currentTime = 761.5
    audio.dispatchEvent(new Event('timeupdate'))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Eetris 12:07:41')
  })

  it('should name the versions without their lengths', () => {
    const options = render()
      .findAll('select[aria-label="Versioon"] option')
      .map(o => o.text())
    expect(options).toEqual(['Toorsalvestus', 'Puhastatud'])
  })

  it('should not offer the cleaned episode before it is rendered', () => {
    const wrapper = render('raw', recording({ editList: null, episodeBytes: null }))
    expect(wrapper.findAll('select[aria-label="Versioon"] option')).toHaveLength(1)
  })

  it('should name each version without its length from tablets up', () => {
    const segments = render()
      .findAll('[role="group"][aria-label="Versioon"] button')
      .map(b => b.text())
    expect(segments).toEqual(['Toorsalvestus', 'Puhastatud'])
  })

  it('should press the segment of the version that plays', () => {
    const pressed = render('episode')
      .findAll('[role="group"][aria-label="Versioon"] button')
      .map(b => b.attributes('aria-pressed'))
    expect(pressed).toEqual(['false', 'true'])
  })

  it('should switch to the version of the segment clicked', async () => {
    const wrapper = render()
    await wrapper.findAll('[role="group"][aria-label="Versioon"] button')[1]!.trigger('click')
    expect(player.version.value).toBe('episode')
  })

  it('should disable the cleaned segment before the episode is rendered', () => {
    const wrapper = render('raw', recording({ editList: null, episodeBytes: null }))
    const segment = wrapper.findAll('[role="group"][aria-label="Versioon"] button')[1]!
    expect([segment.attributes('disabled'), segment.text()]).toEqual(['', 'Puhastatud'])
  })

  it('should keep the select for phones only', () => {
    const wrapper = render()
    expect([
      wrapper.get('select[aria-label="Versioon"]').classes(),
      wrapper.get('[role="group"][aria-label="Versioon"]').classes(),
    ]).toEqual([expect.arrayContaining(['md:hidden']), expect.arrayContaining(['max-md:hidden'])])
  })

  it('should pause the recording when the card closes', () => {
    const wrapper = render()
    const pause = vi.spyOn(audio, 'pause').mockImplementation(() => {})
    wrapper.unmount()
    expect(pause).toHaveBeenCalledOnce()
  })

  it('should not offer to go back to the playing line', async () => {
    const wrapper = render()
    player.lost.value = true
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('button').some(b => b.text() === 'Tagasi praegusele')).toBe(false)
  })
})
