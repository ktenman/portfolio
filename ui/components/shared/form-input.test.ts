import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FormInput from './form-input.vue'

describe('FormInput placeholders', () => {
  it('shows the placeholder text inside an empty text input', () => {
    const wrapper = mount(FormInput, {
      props: { type: 'text', label: 'Nimi', placeholder: 'Sisesta väärtpaberi nimi' },
    })

    expect(wrapper.find('input').attributes('placeholder')).toBe('Sisesta väärtpaberi nimi')
  })

  it('displays the placeholder option when the select has no value yet', () => {
    const wrapper = mount(FormInput, {
      props: {
        type: 'select',
        label: 'Pakkuja',
        placeholder: 'Vali pakkuja',
        options: [{ value: 'FT', text: 'Financial Times' }],
      },
    })

    expect((wrapper.find('select').element as HTMLSelectElement).selectedIndex).toBe(0)
  })

  it('emits the chosen option when a select value is picked', async () => {
    const wrapper = mount(FormInput, {
      props: {
        type: 'select',
        label: 'Pakkuja',
        placeholder: 'Vali pakkuja',
        options: [{ value: 'FT', text: 'Financial Times' }],
      },
    })

    await wrapper.find('select').setValue('FT')

    expect(wrapper.emitted('update:modelValue')).toEqual([['FT']])
  })
})

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
