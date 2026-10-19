import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioStrip from './radio-strip.vue'
import type { StripPart } from '../../utils/radio'

const PARTS: StripPart[] = [
  { start: 0, end: 10, removed: true, label: 'Enne saadet' },
  { start: 10, end: 30, removed: false, label: 'Ülle Madise' },
  { start: 30, end: 40, removed: false, label: 'Pärast' },
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
})
