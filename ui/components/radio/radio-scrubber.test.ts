import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioScrubber from './radio-scrubber.vue'
import type { StripPart } from '../../utils/radio'

const part = (
  start: number,
  end: number,
  label: string,
  name: string | null,
  summary: string | null = null
): StripPart => ({
  start,
  end,
  removed: name === null,
  label,
  title: label,
  name,
  summary,
  time: `${start} s`,
})

const PARTS: StripPart[] = [
  part(0, 10, 'Before the show', null),
  part(10, 30, 'Ülle Madise', 'Madise', 'Õiguskantsler räägib kandidatuurist.'),
  part(30, 40, 'Pärast', 'Pärast'),
]

const RESUMED: StripPart[] = [
  part(0, 10, 'Ülle Madise', 'Madise'),
  part(10, 20, 'Ad break', null),
  { ...part(20, 30, 'Ülle Madise', null), removed: false },
  part(30, 40, 'Pärast', 'Pärast'),
]

describe('RadioScrubber', () => {
  const render = (value = 0, parts = PARTS) =>
    mount(RadioScrubber, { props: { value, total: 40, label: 'Position', parts } })

  const labels = (value = 0) => render(value).findAll('[data-testid="radio-timeline-label"]')

  const name = (value: number) => labels(value)[0]!.findAll('span')[1]!

  const opening = (value = 0) =>
    mount(RadioScrubber, {
      props: { value, total: 40, label: 'Position', parts: PARTS, programme: 'Sihik' },
    }).findAll('[data-testid="radio-timeline-label"]')

  it('should name the show on the opening label', () => {
    expect(
      opening()[0]!
        .findAll('span')
        .map(span => span.text())
    ).toEqual(['10 s', 'Madise', 'Sihik'])
  })

  it("should show the show's name only in an opening too narrow for its topic", () => {
    expect(opening()[0]!.findAll('span')[2]!.classes()).toEqual(
      expect.arrayContaining(['hidden', '@max-[6rem]:block'])
    )
  })

  it('should name the show on the opening label only', () => {
    expect(
      opening()[1]!
        .findAll('span')
        .map(span => span.text())
    ).toEqual(['30 s', 'Pärast'])
  })

  it("should set the show's name in brass while the opening plays", () => {
    expect(opening(15)[0]!.findAll('span')[2]!.classes()).toContain('text-brass-deep')
  })

  it('should play a topic from its label', async () => {
    const wrapper = render()
    await wrapper.findAll('[data-testid="radio-timeline-label"]')[1]!.trigger('click')
    expect(wrapper.emitted('play')).toEqual([[30]])
  })

  it("should mark the playing topic's label", () => {
    expect(labels(15)[0]!.attributes('aria-current')).toBe('true')
  })

  it("should set the playing topic's name in brass", () => {
    expect(name(15).classes()).toEqual(expect.arrayContaining(['font-semibold', 'text-brass-deep']))
  })

  it('should mute the label of a topic already heard', () => {
    expect(name(35).classes()).toContain('text-ink-soft')
  })

  it('should label only named parts with their time and name', () => {
    expect(labels().map(label => label.findAll('span').map(span => span.text()))).toEqual([
      ['10 s', 'Madise'],
      ['30 s', 'Pärast'],
    ])
  })

  it("should give a topic's summary as its label's tooltip", () => {
    expect(labels()[0]!.attributes('title')).toBe('Õiguskantsler räägib kandidatuurist.')
  })

  it('should name each label for screen readers by its visible time and name', () => {
    expect(labels()[1]!.attributes('aria-label')).toBe('30 s Pärast')
  })

  it("should keep a resumed topic's label playing while its second stretch plays", () => {
    const label = render(25, RESUMED).findAll('[data-testid="radio-timeline-label"]')[0]!
    expect(label.attributes('aria-current')).toBe('true')
  })

  it('should not mark a topic heard during a break inside it', () => {
    const label = render(15, RESUMED).findAll('[data-testid="radio-timeline-label"]')[0]!
    expect(label.findAll('span')[1]!.classes()).toContain('text-ink')
  })

  it('should mark a topic heard once its last stretch ends', () => {
    const label = render(35, RESUMED).findAll('[data-testid="radio-timeline-label"]')[0]!
    expect(label.findAll('span')[1]!.classes()).toContain('text-ink-soft')
  })

  it('should leave out the label row when no part is named', () => {
    const wrapper = render(0, PARTS.slice(0, 1))
    expect(wrapper.find('[data-testid="radio-timeline-labels"]').exists()).toBe(false)
  })

  it('should leave out the labels when bare', () => {
    const wrapper = mount(RadioScrubber, {
      props: { value: 0, total: 40, label: 'Position', parts: PARTS, bare: true },
    })
    expect(wrapper.find('[data-testid="radio-timeline-labels"]').exists()).toBe(false)
  })

  it('should draw a slim track when bare', () => {
    const wrapper = mount(RadioScrubber, {
      props: { value: 0, total: 40, label: 'Position', parts: PARTS, bare: true },
    })
    expect(wrapper.get('input').element.parentElement?.classList.contains('h-5')).toBe(true)
  })

  it('should show how much of each part has played', () => {
    const played = render(20).findAll('[data-testid="radio-timeline-part"] > div')
    expect(played.map(fill => fill.attributes('style'))).toEqual([
      'width: 100%;',
      'width: 50%;',
      'width: 0%;',
    ])
  })
})
