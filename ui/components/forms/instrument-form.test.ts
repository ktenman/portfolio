import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import InstrumentForm from './instrument-form.vue'
import { ProviderName } from '../../constants/provider-name'
import { nextTick } from 'vue'

describe('InstrumentForm', () => {
  const defaultProps = {
    instrument: {
      symbol: '',
      name: '',
      providerName: 'ALPHA_VANTAGE' as const,
      category: '',
      baseCurrency: '',
    },
  }

  it('renders all form fields', () => {
    const wrapper = mount(InstrumentForm, {
      props: defaultProps,
    })

    expect(wrapper.find('#symbol').exists()).toBe(true)
    expect(wrapper.find('#name').exists()).toBe(true)
    expect(wrapper.find('#providerName').exists()).toBe(true)
    expect(wrapper.find('#category').exists()).toBe(true)
    expect(wrapper.find('#currency').exists()).toBe(true)
  })

  it('displays initial values from props', () => {
    const instrument = {
      symbol: 'AAPL',
      name: 'Apple Inc.',
      providerName: 'ALPHA_VANTAGE' as const,
      category: 'STOCK',
      baseCurrency: 'USD',
    }

    const wrapper = mount(InstrumentForm, {
      props: { instrument },
    })

    expect((wrapper.find('#symbol').element as HTMLInputElement).value).toBe('AAPL')
    expect((wrapper.find('#name').element as HTMLInputElement).value).toBe('Apple Inc.')
    expect((wrapper.find('#providerName').element as HTMLSelectElement).value).toBe('ALPHA_VANTAGE')
    expect((wrapper.find('#category').element as HTMLSelectElement).value).toBe('STOCK')
    expect((wrapper.find('#currency').element as HTMLSelectElement).value).toBe('USD')
  })

  it('emits update:instrument when form values change', async () => {
    const wrapper = mount(InstrumentForm, {
      props: defaultProps,
    })

    await wrapper.find('#symbol').setValue('MSFT')
    await wrapper.find('#name').setValue('Microsoft')

    const emitted = wrapper.emitted('update:instrument')
    expect(emitted).toBeTruthy()
    expect(emitted?.[emitted.length - 1]?.[0]).toMatchObject({
      symbol: 'MSFT',
      name: 'Microsoft',
    })
  })

  it('emits submit event when form is submitted', async () => {
    const wrapper = mount(InstrumentForm, {
      props: defaultProps,
    })

    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.emitted('submit')).toBeTruthy()
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('updates local values when prop changes', async () => {
    const wrapper = mount(InstrumentForm, {
      props: defaultProps,
    })

    const newInstrument = {
      symbol: 'GOOGL',
      name: 'Google',
      providerName: 'ALPHA_VANTAGE' as const,
      category: 'STOCK',
      baseCurrency: 'USD',
    }

    await wrapper.setProps({ instrument: newInstrument })
    await nextTick()

    expect((wrapper.find('#symbol').element as HTMLInputElement).value).toBe('GOOGL')
    expect((wrapper.find('#name').element as HTMLInputElement).value).toBe('Google')
  })

  it('renders all provider options', () => {
    const wrapper = mount(InstrumentForm, {
      props: defaultProps,
    })

    const providerOptions = wrapper.find('#providerName').findAll('option')
    const providerValues = Object.values(ProviderName)

    expect(providerOptions.length).toBe(providerValues.length + 1)
  })

  it('renders all category options', () => {
    const wrapper = mount(InstrumentForm, {
      props: defaultProps,
    })

    const categoryOptions = wrapper.find('#category').findAll('option')
    const expectedCategories = ['STOCK', 'ETF', 'MUTUAL_FUND', 'BOND', 'CRYPTO']

    expect(categoryOptions.length).toBe(expectedCategories.length + 1)

    categoryOptions.slice(1).forEach((option, index) => {
      expect((option.element as HTMLOptionElement).value).toBe(expectedCategories[index])
    })
  })

  it('renders all currency options', () => {
    const wrapper = mount(InstrumentForm, {
      props: defaultProps,
    })

    const currencyOptions = wrapper.find('#currency').findAll('option')
    const expectedCurrencies = ['USD', 'EUR', 'GBP']

    expect(currencyOptions.length).toBe(expectedCurrencies.length + 1)

    currencyOptions.slice(1).forEach((option, index) => {
      expect((option.element as HTMLOptionElement).value).toBe(expectedCurrencies[index])
    })
  })

  it('marks all fields as required', () => {
    const wrapper = mount(InstrumentForm, {
      props: defaultProps,
    })

    expect(wrapper.find('#symbol').attributes('required')).toBeDefined()
    expect(wrapper.find('#name').attributes('required')).toBeDefined()
    expect(wrapper.find('#providerName').attributes('required')).toBeDefined()
    expect(wrapper.find('#category').attributes('required')).toBeDefined()
    expect(wrapper.find('#currency').attributes('required')).toBeDefined()
  })
})
