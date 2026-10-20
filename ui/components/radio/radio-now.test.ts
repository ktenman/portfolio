import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioNow from './radio-now.vue'
import type { StripPart } from '../../utils/radio'

const part = (
  start: number,
  end: number,
  title: string,
  name: string | null,
  summary: string | null = null
): StripPart => ({ start, end, removed: false, label: title, title, name, summary, time: '' })

const PARTS: StripPart[] = [
  part(
    0,
    541,
    'Küberturvalisus ja nutikülmikud',
    'Küberturvalisus',
    'Häkkerid murdsid külmikutesse.'
  ),
  part(541, 786, 'Reklaamipaus', null),
  part(
    786,
    901,
    'Põhja-Korea fantoomtöölised Ameerikas',
    'Põhja-Korea töölised',
    'Võltsnimedega IT-töötajad said tööd USA firmades.'
  ),
  part(901, 1200, 'Skeletonist sai ükssarvik', 'Skeleton'),
]

describe('RadioNow', () => {
  const render = (at: number, parts = PARTS) => mount(RadioNow, { props: { parts, at } })

  const button = (wrapper: ReturnType<typeof render>, name: string) =>
    wrapper.findAll('button').find(it => it.text() === name || it.attributes('aria-label') === name)

  it("should show the playing topic's number, title and summary", () => {
    const text = render(800).text()
    expect(
      ['2/3', 'Põhja-Korea fantoomtöölised Ameerikas', 'Võltsnimedega IT-töötajad'].every(part =>
        text.includes(part)
      )
    ).toBe(true)
  })

  it("should show the playing topic's span and length", () => {
    expect(render(800).text()).toContain('13:06–15:01 · 2 min')
  })

  it('should play the next topic', async () => {
    const wrapper = render(800)
    await button(wrapper, 'Järgmine teema →')!.trigger('click')
    expect(wrapper.emitted('play')).toEqual([[901]])
  })

  it('should play the previous topic', async () => {
    const wrapper = render(800)
    await button(wrapper, 'Eelmine teema')!.trigger('click')
    expect(wrapper.emitted('play')).toEqual([[0]])
  })

  it('should offer no next topic on the last one', () => {
    const wrapper = render(1000)
    expect([button(wrapper, 'Järgmine teema →'), button(wrapper, 'Järgmine teema')]).toEqual([
      undefined,
      undefined,
    ])
  })

  it('should offer no previous topic on the first one', () => {
    expect(button(render(100), 'Eelmine teema')).toBeUndefined()
  })

  it('should hide itself with fewer than two topics', () => {
    expect(render(100, PARTS.slice(0, 2)).find('[data-testid="radio-now"]').exists()).toBe(false)
  })

  it('should not count a cut as a topic', () => {
    expect(render(600).text()).toContain('1/3 Küberturvalisus ja nutikülmikud')
  })

  it('should leave out a missing summary', () => {
    expect(render(1000).find('[data-testid="radio-now-summary"]').exists()).toBe(false)
  })
})
