import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioStrip from './radio-strip.vue'
import type { StripPart } from '../../utils/radio'

const part = (start: number, end: number, label: string, name: string | null): StripPart => ({
  start,
  end,
  removed: name === null,
  label,
  title: label,
  name,
  summary: null,
  time: `${start} s`,
})

const PARTS: StripPart[] = [
  part(0, 10, 'Enne saadet', null),
  part(10, 30, 'Ülle Madise', 'Madise'),
  part(30, 40, 'Pärast', 'Pärast'),
]

describe('RadioStrip', () => {
  const render = (at = 0) => mount(RadioStrip, { props: { parts: PARTS, at } })

  it('should play a part from its start', async () => {
    const wrapper = render()
    await wrapper.findAll('button')[2]!.trigger('click')
    expect(wrapper.emitted('play')).toEqual([[30]])
  })

  it('should mark the part under the playhead', () => {
    expect(
      render(15)
        .findAll('button[aria-current="true"]')
        .map(button => button.attributes('aria-label'))
    ).toEqual(['Ülle Madise'])
  })

  const labels = (at: number) => render(at).findAll('[data-testid="radio-strip-label"]')

  it('should label each named part at its start', () => {
    expect(labels(0).map(label => [label.text(), label.attributes('style')])).toEqual([
      ['Madise', 'left: calc(25% + 0.625rem);'],
      ['Pärast', 'left: calc(75% + 0.625rem);'],
    ])
  })

  it("should mark the playing topic's label", () => {
    expect(labels(15)[0]!.classes()).toEqual(
      expect.arrayContaining(['font-semibold', 'text-brass-deep'])
    )
  })

  it('should leave out the label band when no part is named', () => {
    const parts = [part(0, 10, 'Enne saadet', null)]
    const wrapper = mount(RadioStrip, { props: { parts, at: 0 } })
    expect(wrapper.find('[data-testid="radio-strip-labels"]').exists()).toBe(false)
  })

  it('should mute the label of a topic already heard', () => {
    expect(labels(35)[0]!.classes()).toContain('text-ink-soft')
  })

  it('should show how much of each part has played', () => {
    const played = render(20).findAll('[data-testid="radio-strip-played"]')
    expect(played.map(fill => fill.attributes('style'))).toEqual([
      'width: 100%;',
      'width: 50%;',
      'width: 0%;',
    ])
  })

  it("should print each part's start inside it", () => {
    expect(render().findAll('button')[1]!.text()).toBe('10 s')
  })
})
