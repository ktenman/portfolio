import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FormInput from './form-input.vue'

describe('FormInput wheel handling', () => {
  it('blurs a focused number input on wheel so scrolling cannot change the value', async () => {
    const wrapper = mount(FormInput, {
      props: { type: 'number', label: 'Kogus' },
      attachTo: document.body,
    })
    const input = wrapper.find('input').element as HTMLInputElement
    input.focus()

    await wrapper.find('input').trigger('wheel')

    expect(document.activeElement).not.toBe(input)
    wrapper.unmount()
  })

  it('keeps focus on a text input during wheel', async () => {
    const wrapper = mount(FormInput, {
      props: { type: 'text', label: 'Nimi' },
      attachTo: document.body,
    })
    const input = wrapper.find('input').element as HTMLInputElement
    input.focus()

    await wrapper.find('input').trigger('wheel')

    expect(document.activeElement).toBe(input)
    wrapper.unmount()
  })
})
