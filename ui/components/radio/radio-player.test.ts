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

  const parts = (wrapper: ReturnType<typeof render>) =>
    wrapper.findAll('[data-testid="radio-strip"] button')

  it('should hatch a cut as soon as it is kept or removed', async () => {
    const item = recording()
    const wrapper = render('raw', item)
    useRadioDecisions(() => item).choose(1, false)
    await wrapper.vm.$nextTick()
    expect(parts(wrapper)[2]!.attributes('aria-label')).toBe(
      'Reklaamipaus 12:30–12:32 · Jääb alles'
    )
  })

  it('should play the raw part clicked on Toorsalvestus', async () => {
    const wrapper = render()
    await parts(wrapper)[2]!.trigger('click')
    expect([player.version.value, player.recordingSeconds.value]).toEqual(['raw', 2120])
  })

  it('should play the episode part clicked on Puhastatud', async () => {
    const wrapper = render('episode')
    await parts(wrapper)[1]!.trigger('click')
    expect([player.version.value, audio.currentTime]).toEqual(['episode', 1358.5])
  })

  const timeline = (wrapper: ReturnType<typeof render>) =>
    wrapper.findAll('[data-testid="radio-timeline-part"]')

  const hatched = (part: { classes: () => string[] }) =>
    part.classes().join(' ').includes('repeating-linear-gradient')

  it('should hatch a removed part on the phone timeline', () => {
    const wrapper = render()
    expect(timeline(wrapper).map(hatched)).toEqual(parts(wrapper).map(hatched))
  })

  it('should name the playing chapter under the phone timeline', async () => {
    const item = recording()
    const chapters = [
      { start: 0, title: 'Algus', label: null, summary: null },
      { start: 500, title: 'Õhtune jätk', label: null, summary: null },
    ]
    const wrapper = render('episode', recording({ editList: { ...item.editList!, chapters } }))
    audio.currentTime = 600
    audio.dispatchEvent(new Event('timeupdate'))
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-testid="radio-timeline-caption"]').text()).toBe('Peatükk 2')
  })

  it('should keep a played removed part faded on the phone timeline', async () => {
    const wrapper = render()
    await parts(wrapper)[3]!.trigger('click')
    expect(timeline(wrapper).find(hatched)!.get('div').classes()).toContain('bg-ink/40')
  })

  it('should leave the caption empty before the first chapter', async () => {
    const item = recording()
    const chapters = [{ start: 30, title: 'Algus', label: null, summary: null }]
    const wrapper = render('episode', recording({ editList: { ...item.editList!, chapters } }))
    audio.currentTime = 10
    audio.dispatchEvent(new Event('timeupdate'))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="radio-timeline-caption"]').exists()).toBe(false)
  })

  it('should jump to the spot tapped on the timeline', async () => {
    const wrapper = render()
    const input = wrapper.get('input[aria-label="Asukoht salvestuses"]')
    vi.spyOn(input.element, 'getBoundingClientRect').mockReturnValue(new DOMRect(10, 0, 200, 28))
    await input.trigger('click', { clientX: 160 })
    expect(player.recordingSeconds.value).toBe(Math.round(player.total.value * 0.75))
  })

  it('should leave the caption empty on the raw recording', () => {
    expect(render().find('[data-testid="radio-timeline-caption"]').exists()).toBe(false)
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

  it('should offer the cleaned episode once it is rendered', () => {
    const options = render()
      .findAll('select[aria-label="Versioon"] option')
      .map(o => o.text())
    expect(options).toEqual(['Toorsalvestus · 1:10:00', 'Puhastatud · 41:52'])
  })

  it('should not offer the cleaned episode before it is rendered', () => {
    const wrapper = render('raw', recording({ editList: null, episodeBytes: null }))
    expect(wrapper.findAll('select[aria-label="Versioon"] option')).toHaveLength(1)
  })

  it('should count the card as in view while it first appears', () => {
    player.cardInView.value = false
    render()
    expect(player.cardInView.value).toBe(true)
  })

  it("should clear the card's place in view when it closes", () => {
    const wrapper = render()
    player.cardInView.value = true
    wrapper.unmount()
    expect(player.cardInView.value).toBe(false)
  })

  it('should offer to go back to the playing line while the text lost it', async () => {
    const wrapper = render()
    player.lost.value = true
    await wrapper.vm.$nextTick()
    await wrapper
      .findAll('button')
      .find(b => b.text() === 'Tagasi praegusele')!
      .trigger('click')
    expect(player.lost.value).toBe(false)
  })

  it('should not offer to go back while the text follows the playing line', () => {
    expect(
      render()
        .findAll('button')
        .some(b => b.text() === 'Tagasi praegusele')
    ).toBe(false)
  })
})
