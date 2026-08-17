import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import FilterToggle from './filter-toggle.vue'

enableAutoUnmount(afterEach)

const createWrapper = (
  props: { modelValue?: boolean; selected?: number; available?: number } = {}
) =>
  mount(FilterToggle, {
    props: { modelValue: true, selected: 7, available: 7, ...props },
  })

describe('FilterToggle', () => {
  it('reports how many of the available filters are selected', () => {
    const wrapper = createWrapper({ selected: 2, available: 7 })

    expect(wrapper.text()).toBe('Filters (2/7)')
  })

  it('marks itself active when some filters are deselected', () => {
    const wrapper = createWrapper({ selected: 2, available: 7 })

    expect(wrapper.classes()).toContain('active')
  })

  it('stays inactive when every filter is selected', () => {
    const wrapper = createWrapper()

    expect(wrapper.classes()).not.toContain('active')
  })

  it('exposes the open state to assistive technology', () => {
    const wrapper = createWrapper({ modelValue: false })

    expect(wrapper.attributes('aria-expanded')).toBe('false')
  })

  it('flips the model when pressed', async () => {
    const wrapper = createWrapper({ modelValue: false })

    await wrapper.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })
})
