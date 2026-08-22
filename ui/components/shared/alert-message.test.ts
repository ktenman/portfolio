import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AlertMessage from './alert-message.vue'

describe('alert-message', () => {
  it('renders the bootstrap alert class names for its variant', () => {
    const wrapper = mount(AlertMessage, {
      props: { variant: 'danger' },
      slots: { default: 'Ei õnnestunud laadida' },
    })
    expect(wrapper.classes()).toEqual(expect.arrayContaining(['alert', 'alert-danger']))
    expect(wrapper.attributes('role')).toBe('alert')
    expect(wrapper.text()).toBe('Ei õnnestunud laadida')
  })

  it('emits dismiss when the close button is clicked', async () => {
    const wrapper = mount(AlertMessage, {
      props: { variant: 'info', dismissible: true },
      slots: { default: 'Teade' },
    })
    expect(wrapper.classes()).toContain('alert-dismissible')
    await wrapper.find('.btn-close').trigger('click')
    expect(wrapper.emitted('dismiss')).toHaveLength(1)
  })

  it('omits the close button when it cannot be dismissed', () => {
    const wrapper = mount(AlertMessage, {
      props: { variant: 'warning' },
      slots: { default: 'Hoiatus' },
    })
    expect(wrapper.find('.btn-close').exists()).toBe(false)
    expect(wrapper.classes()).not.toContain('alert-dismissible')
  })
})
