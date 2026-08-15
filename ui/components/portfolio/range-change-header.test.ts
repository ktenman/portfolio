import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RangeChangeHeader from './range-change-header.vue'

describe('RangeChangeHeader', () => {
  it('should prefix a gain with a plus and mark it as a gain', () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: 25429, percent: 17.16 },
    })

    const change = wrapper.find('.range-change')
    expect(change.classes()).toContain('text-gain')
    expect(change.text()).toBe('+€25,429.00 (+17.16%)')
  })

  it('should prefix a loss with a minus and mark it as a loss', () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: -8431.2, percent: -8.46 },
    })

    const change = wrapper.find('.range-change')
    expect(change.classes()).toContain('text-loss')
    expect(change.text()).toBe('−€8,431.20 (−8.46%)')
  })

  it('should dont colour a flat range', () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: 0, percent: 0 },
    })

    const change = wrapper.find('.range-change')
    expect(change.classes()).toEqual(['range-change'])
  })
})
