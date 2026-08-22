import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AppButton from './app-button.vue'

describe('app-button', () => {
  it('renders the bootstrap class names for a ghost secondary small button', () => {
    const wrapper = mount(AppButton, {
      props: { variant: 'secondary', size: 'sm', ghost: true },
      slots: { default: 'Reset' },
    })
    expect(wrapper.classes()).toEqual(
      expect.arrayContaining(['btn', 'btn-secondary', 'btn-sm', 'btn-ghost'])
    )
    expect(wrapper.text()).toBe('Reset')
  })

  it('disables itself and shows a spinner while loading', () => {
    const wrapper = mount(AppButton, { props: { loading: true }, slots: { default: 'Save' } })
    expect(wrapper.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.btn-spinner').exists()).toBe(true)
  })

  it('disables itself when disabled is set without loading', () => {
    const wrapper = mount(AppButton, { props: { disabled: true }, slots: { default: 'Sulge' } })
    expect(wrapper.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.btn-spinner').exists()).toBe(false)
  })

  it('defaults to a non-submitting button', () => {
    const wrapper = mount(AppButton, { slots: { default: 'Cancel' } })
    expect(wrapper.attributes('type')).toBe('button')
    expect(wrapper.classes()).toContain('btn')
  })
})
