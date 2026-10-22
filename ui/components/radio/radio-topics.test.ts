import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioTopics from './radio-topics.vue'
import { recording } from './radio-fixture'
import { episodeParts, rawParts, type StripPart } from '../../utils/radio'

describe('RadioTopics', () => {
  beforeEach(() => localStorage.clear())

  const render = (at = 0, parts: StripPart[] = episodeParts(recording())) =>
    mount(RadioTopics, { props: { parts, at } })

  const rows = (wrapper: ReturnType<typeof render>) =>
    wrapper.findAll('[data-testid="radio-topics-row"]')

  const playing = (wrapper: ReturnType<typeof render>) =>
    rows(wrapper).map(row => row.attributes('aria-current') === 'true')

  it('should list each topic once with its length under its start', () => {
    const wrapper = render()
    const [column, title] = rows(wrapper)[0]!.findAll(':scope > span')
    expect([
      rows(wrapper).length,
      column!.findAll('span').map(s => s.text()),
      title!.text(),
    ]).toEqual([3, ['0:00', '30 min'], 'Rahvusooperi tüli'])
  })

  it('should hide the card before the recording has topics', () => {
    const untitled = { start: 0, end: 4200, title: null, cut: false, label: null, summary: null }
    const wrapper = render(0, rawParts(recording({ timeline: [untitled] }), []))
    expect(wrapper.find('[data-testid="radio-topics"]').exists()).toBe(false)
  })

  it('should give a resumed topic its length without the break', () => {
    const wrapper = render(0, rawParts(recording(), [0, 1, 2, 3]))
    expect(rows(wrapper)[0]!.findAll('span')[2]!.text()).toBe('30 min')
  })

  it('should mark the playing topic', () => {
    expect(playing(render(1900))).toEqual([false, true, false])
  })

  it('should mark no topic during an ad break', () => {
    expect(playing(render(2150, rawParts(recording(), [0, 1, 2, 3])))).toEqual([
      false,
      false,
      false,
    ])
  })

  it('should play a topic from its start', async () => {
    const wrapper = render()
    await rows(wrapper)[1]!.trigger('click')
    expect(wrapper.emitted('play')).toEqual([[1803.5]])
  })

  it('should leave the title alone while open', () => {
    expect(render(1900).get('h3').element.parentElement!.textContent!.trim()).toBe('Teemad')
  })

  it("should show the playing topic's start while folded", () => {
    localStorage.setItem('radio-fold:Teemad', 'false')
    expect(render(1900).get('h3').element.parentElement!.textContent).toContain('30:03')
  })

  it('should hide the card without topics', () => {
    expect(render(0, []).find('section').exists()).toBe(false)
  })
})
