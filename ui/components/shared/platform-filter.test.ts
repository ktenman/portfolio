import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import PlatformFilter from './platform-filter.vue'

enableAutoUnmount(afterEach)

const AVAILABLE = ['LIGHTYEAR', 'SWEDBANK', 'TRADING212']

const createWrapper = (selected: string[] = AVAILABLE) =>
  mount(PlatformFilter, {
    props: { available: AVAILABLE, selected },
  })

describe('PlatformFilter', () => {
  it('shows one chip per available platform alongside the select-all chip', () => {
    const wrapper = createWrapper()

    expect(wrapper.findAll('button')).toHaveLength(AVAILABLE.length + 1)
  })

  it('offers to clear the selection while every platform is selected', () => {
    const wrapper = createWrapper()

    expect(wrapper.find('.platform-btn-ghost').text()).toBe('Clear All')
  })

  it('offers to select everything once a platform is deselected', () => {
    const wrapper = createWrapper(['LIGHTYEAR'])

    expect(wrapper.find('.platform-btn-ghost').text()).toBe('Select All')
  })

  it('emits the toggled platform when a chip is clicked', async () => {
    const wrapper = createWrapper()

    await wrapper.find('.platform-btn').trigger('click')

    expect(wrapper.emitted('toggle')).toEqual([['LIGHTYEAR']])
  })
})
