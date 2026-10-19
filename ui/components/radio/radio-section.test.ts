import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioSection from './radio-section.vue'
import { recording, sections } from './radio-fixture'
import type { TranscriptLine } from '../../models/generated/domain-models'

const spoken = (start: number): TranscriptLine => ({
  start,
  end: start + 5,
  text: ` Ostke üks ${start}`,
  words: [],
  split: null,
  music: false,
})

describe('RadioSection', () => {
  const render = (count: number) =>
    mount(RadioSection, {
      props: {
        section: sections([2130, 2140, 2150, 2160, 2170].slice(0, count).map(spoken))[2],
        startedAt: recording().status.startedAt,
        raw: true,
        playing: false,
      },
      slots: { heading: '<div data-testid="radio-cut">Reklaamipaus</div>' },
    })

  const lines = (wrapper: ReturnType<typeof render>) =>
    wrapper.findAll('[data-testid="radio-text-line"]')

  it("should show a cut card above the cut's lines", () => {
    expect(render(1).find('[data-testid="radio-cut"] + ol').exists()).toBe(true)
  })

  it("should dim a cut's lines", () => {
    expect(lines(render(1))[0].classes()).toContain('opacity-60')
  })

  it("should fold a cut's lines after three", () => {
    const wrapper = render(5)
    expect([
      lines(wrapper).length,
      wrapper.find('[data-testid="radio-section-more"]').text(),
    ]).toEqual([3, 'Näita veel 2 rida'])
  })

  it("should show all of a cut's lines after Näita veel", async () => {
    const wrapper = render(5)
    await wrapper.find('[data-testid="radio-section-more"]').trigger('click')
    expect(lines(wrapper)).toHaveLength(5)
  })

  it('should not fold three lines of a cut', () => {
    expect(render(3).find('[data-testid="radio-section-more"]').exists()).toBe(false)
  })
})
