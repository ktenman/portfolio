import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CurrencyFlag from './currency-flag.vue'

describe('currency-flag', () => {
  it('renders an img for a known currency', () => {
    const wrapper = mount(CurrencyFlag, { props: { currency: 'USD' } })
    const img = wrapper.find('img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://hatscripts.github.io/circle-flags/flags/us.svg')
    expect(img.attributes('title')).toBe('USD')
  })

  it('renders nothing when currency is null', () => {
    const wrapper = mount(CurrencyFlag, { props: { currency: null } })
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('renders nothing when currency is unknown', () => {
    const wrapper = mount(CurrencyFlag, { props: { currency: 'XYZ' } })
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('respects size prop', () => {
    const wrapper = mount(CurrencyFlag, { props: { currency: 'EUR', size: 24 } })
    const img = wrapper.find('img')
    expect(img.attributes('width')).toBe('24')
    expect(img.attributes('height')).toBe('24')
  })

  it('hides self on load error', async () => {
    const wrapper = mount(CurrencyFlag, { props: { currency: 'USD' } })
    const img = wrapper.find('img').element as HTMLImageElement
    await wrapper.find('img').trigger('error')
    expect(img.style.display).toBe('none')
  })
})
