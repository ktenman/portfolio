import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import RangeChangeHeader from './range-change-header.vue'
import { TimeRange } from '../../models/generated/domain-models'

describe('RangeChangeHeader', () => {
  it('should leave a gain unsigned and mark it as a gain', () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: 25429, percent: 17.16, range: TimeRange.ONE_MONTH },
    })

    const change = wrapper.find('.range-change')
    expect(change.classes()).toContain('text-gain')
    expect(change.text()).toBe('€25,429.00 (17.16%)')
  })

  it('should prefix a loss with a minus and mark it as a loss', () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: -8431.2, percent: -8.46, range: TimeRange.ONE_MONTH },
    })

    const change = wrapper.find('.range-change')
    expect(change.classes()).toContain('text-loss')
    expect(change.text()).toBe('−€8,431.20 (−8.46%)')
  })

  it('should print the cents of the amount on their own so they can be set smaller', () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: -8431.2, percent: -8.46, range: TimeRange.ONE_MONTH },
    })

    expect(wrapper.find('.range-cents').text()).toBe('.20')
  })

  it('should dont colour a flat range', () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: 0, percent: 0, range: TimeRange.ONE_MONTH },
    })

    const change = wrapper.find('.range-change')
    expect(change.classes()).toEqual(['range-change'])
  })

  it('should leave a flat range unsigned', () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: 0, percent: 0, range: TimeRange.ONE_MONTH },
    })

    expect(wrapper.find('.range-change').text()).toBe('€0.00 (0.00%)')
  })

  it('should flash a gain when the amount rises', async () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: 100, percent: 1, range: TimeRange.ONE_MONTH },
    })

    await wrapper.setProps({ amount: 200, percent: 2 })
    await nextTick()

    expect(wrapper.find('.range-change').classes()).toContain('value-increase')
  })

  it('should flash a loss when the amount falls', async () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: 100, percent: 1, range: TimeRange.ONE_MONTH },
    })

    await wrapper.setProps({ amount: 50, percent: 0.5 })
    await nextTick()

    expect(wrapper.find('.range-change').classes()).toContain('value-decrease')
  })

  it('should name the period the change covers', () => {
    const wrapper = mount(RangeChangeHeader, {
      props: { amount: 13726.45, percent: 8.4, range: TimeRange.SIX_YEARS },
    })

    expect(wrapper.find('.range-period').text()).toBe('in the past 6 years')
  })
})
