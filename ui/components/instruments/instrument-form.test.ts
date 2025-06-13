import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import InstrumentForm from './instrument-form.vue'
import FormInput from '../shared/form-input.vue'

vi.mock('../../services/enum-service', () => ({
  enumService: {
    getAll: vi.fn(() =>
      Promise.resolve({
        platforms: [
          'AVIVA',
          'BINANCE',
          'COINBASE',
          'LHV',
          'LIGHTYEAR',
          'SWEDBANK',
          'TRADING212',
          'UNKNOWN',
        ],
        providers: ['ALPHA_VANTAGE', 'BINANCE', 'FT'],
        transactionTypes: ['BUY', 'SELL'],
        categories: ['CRYPTOCURRENCY', 'ETF', 'STOCK'],
        currencies: ['USD', 'EUR', 'GBP'],
      })
    ),
  },
}))

describe('InstrumentForm', () => {
  const createWrapper = (props = {}) => {
    return mount(InstrumentForm, {
      props,
      global: {
        components: { FormInput },
      },
    })
  }

  describe('initial rendering', () => {
    it('should render all form fields', () => {
      const wrapper = createWrapper()

      const formInputs = wrapper.findAllComponents(FormInput)
      expect(formInputs).toHaveLength(5)

      expect(formInputs[0].props('label')).toBe('Symbol')
      expect(formInputs[1].props('label')).toBe('Name')
      expect(formInputs[2].props('label')).toBe('Data Provider')
      expect(formInputs[3].props('label')).toBe('Category')
      expect(formInputs[4].props('label')).toBe('Currency')
    })

    it('should have form with novalidate attribute', () => {
      const wrapper = createWrapper()
      const form = wrapper.find('form')
      expect(form.attributes('novalidate')).toBe('')
    })

    it('should render empty form without initial data', () => {
      const wrapper = createWrapper()

      const formInputs = wrapper.findAllComponents(FormInput)
      expect(formInputs[0].props('modelValue')).toBeUndefined()
      expect(formInputs[1].props('modelValue')).toBeUndefined()
      expect(formInputs[2].props('modelValue')).toBeUndefined()
      expect(formInputs[3].props('modelValue')).toBeUndefined()
      expect(formInputs[4].props('modelValue')).toBeUndefined()
    })
  })

  describe('with initial data', () => {
    it('should populate form fields with initial data', () => {
      const initialData = {
        symbol: 'AAPL',
        name: 'Apple Inc.',
        providerName: 'ALPHA_VANTAGE',
        category: 'STOCK',
        baseCurrency: 'USD',
      }

      const wrapper = createWrapper({ initialData })

      const formInputs = wrapper.findAllComponents(FormInput)
      expect(formInputs[0].props('modelValue')).toBe('AAPL')
      expect(formInputs[1].props('modelValue')).toBe('Apple Inc.')
      expect(formInputs[2].props('modelValue')).toBe('ALPHA_VANTAGE')
      expect(formInputs[3].props('modelValue')).toBe('STOCK')
      expect(formInputs[4].props('modelValue')).toBe('USD')
    })

    it('should update form when initial data changes', async () => {
      const wrapper = createWrapper({
        initialData: { symbol: 'AAPL', name: 'Apple Inc.' },
      })

      await wrapper.setProps({
        initialData: { symbol: 'GOOGL', name: 'Alphabet Inc.' },
      })
      await nextTick()

      const formInputs = wrapper.findAllComponents(FormInput)
      expect(formInputs[0].props('modelValue')).toBe('GOOGL')
      expect(formInputs[1].props('modelValue')).toBe('Alphabet Inc.')
    })
  })

  describe('form interactions', () => {
    it('should update form data when input values change', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[0].vm.$emit('update:modelValue', 'TSLA')
      await formInputs[1].vm.$emit('update:modelValue', 'Tesla Inc.')
      await formInputs[2].vm.$emit('update:modelValue', 'FINANCIAL_TIMES')
      await formInputs[3].vm.$emit('update:modelValue', 'STOCK')
      await formInputs[4].vm.$emit('update:modelValue', 'EUR')

      await nextTick()

      expect(formInputs[0].props('modelValue')).toBe('TSLA')
      expect(formInputs[1].props('modelValue')).toBe('Tesla Inc.')
      expect(formInputs[2].props('modelValue')).toBe('FINANCIAL_TIMES')
      expect(formInputs[3].props('modelValue')).toBe('STOCK')
      expect(formInputs[4].props('modelValue')).toBe('EUR')
    })
  })

  describe('form submission', () => {
    it('should emit submit event with form data when form is valid', async () => {
      const initialData = {
        symbol: 'BTC',
        name: 'Bitcoin',
        providerName: 'BINANCE',
        category: 'CRYPTO',
        baseCurrency: 'USD',
      }

      const wrapper = createWrapper({ initialData })

      const form = wrapper.find('form')
      await form.trigger('submit')

      expect(wrapper.emitted('submit')).toBeTruthy()
      expect(wrapper.emitted('submit')?.[0]).toEqual([initialData])
    })

    it('should not emit submit event when required fields are missing', async () => {
      const wrapper = createWrapper()

      const form = wrapper.find('form')
      await form.trigger('submit')

      expect(wrapper.emitted('submit')).toBeFalsy()
    })

    it('should not emit submit when form validation fails', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[0].vm.$emit('update:modelValue', 'PARTIAL')

      const form = wrapper.find('form')
      await form.trigger('submit')

      expect(wrapper.emitted('submit')).toBeFalsy()
    })
  })

  describe('provider options', () => {
    it('should generate provider options from ProviderName enum', async () => {
      const wrapper = createWrapper()
      await nextTick()
      await nextTick()

      const providerInput = wrapper.findAllComponents(FormInput)[2]
      const expectedOptions = [
        { value: 'ALPHA_VANTAGE', text: 'Alpha vantage' },
        { value: 'BINANCE', text: 'Binance' },
        { value: 'FT', text: 'Ft' },
      ]

      expect(providerInput.props('options')).toEqual(expectedOptions)
    })
  })

  describe('partial data handling', () => {
    it('should handle partial initial data', () => {
      const partialData = {
        symbol: 'ETH',
        name: 'Ethereum',
      }

      const wrapper = createWrapper({ initialData: partialData })

      const formInputs = wrapper.findAllComponents(FormInput)
      expect(formInputs[0].props('modelValue')).toBe('ETH')
      expect(formInputs[1].props('modelValue')).toBe('Ethereum')
      expect(formInputs[2].props('modelValue')).toBeUndefined()
      expect(formInputs[3].props('modelValue')).toBeUndefined()
      expect(formInputs[4].props('modelValue')).toBeUndefined()
    })

    it('should not submit partial form data when required fields missing', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[0].vm.$emit('update:modelValue', 'PARTIAL')
      await formInputs[1].vm.$emit('update:modelValue', 'Partial Test')

      const form = wrapper.find('form')
      await form.trigger('submit')

      expect(wrapper.emitted('submit')).toBeFalsy()
    })
  })
})
