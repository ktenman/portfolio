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
          'IBKR',
          'LHV',
          'LIGHTYEAR',
          'SWEDBANK',
          'TRADING212',
          'UNKNOWN',
        ],
        providers: ['BINANCE', 'FT', 'LIGHTYEAR', 'TRADING212'],
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
      expect(formInputs).toHaveLength(6)

      expect(formInputs[0].props('label')).toBe('Symbol')
      expect(formInputs[1].props('label')).toBe('Name')
      expect(formInputs[2].props('label')).toBe('Data Provider')
      expect(formInputs[3].props('label')).toBe('Category')
      expect(formInputs[4].props('label')).toBe('Currency')
      expect(formInputs[5].props('label')).toBe('Current Price')
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
      expect(formInputs[4].props('modelValue')).toBe('EUR')
      expect(formInputs[5].props('modelValue')).toBeUndefined()
    })
  })

  describe('with initial data', () => {
    it('should populate form fields with initial data', () => {
      const initialData = {
        id: 1,
        symbol: 'AAPL',
        name: 'Apple Inc.',
        providerName: 'FT',
        category: 'STOCK',
        baseCurrency: 'USD',
      }

      const wrapper = createWrapper({ initialData })

      const formInputs = wrapper.findAllComponents(FormInput)
      expect(formInputs[0].props('modelValue')).toBe('AAPL')
      expect(formInputs[1].props('modelValue')).toBe('Apple Inc.')
      expect(formInputs[2].props('modelValue')).toBe('FT')
      expect(formInputs[3].props('modelValue')).toBe('STOCK')
      expect(formInputs[4].props('modelValue')).toBe('USD')
    })

    it('should disable symbol, name, provider, and category fields when editing existing instrument', () => {
      const initialData = {
        id: 1,
        symbol: 'AAPL',
        name: 'Apple Inc.',
        providerName: 'FT',
        category: 'STOCK',
        baseCurrency: 'USD',
      }

      const wrapper = createWrapper({ initialData })

      const formInputs = wrapper.findAllComponents(FormInput)
      expect(formInputs[0].attributes('disabled')).toBeDefined()
      expect(formInputs[1].attributes('disabled')).toBeDefined()
      expect(formInputs[2].attributes('disabled')).toBeDefined()
      expect(formInputs[3].attributes('disabled')).toBeDefined()
      expect(formInputs[4].attributes('disabled')).toBeDefined()
      expect(formInputs[5].attributes('disabled')).toBeUndefined()
    })

    it('should populate currentPrice when provided in initial data', () => {
      const initialData = {
        symbol: 'AAPL',
        name: 'Apple Inc.',
        providerName: 'FT',
        category: 'STOCK',
        baseCurrency: 'USD',
        currentPrice: 150.25,
      }

      const wrapper = createWrapper({ initialData })

      const formInputs = wrapper.findAllComponents(FormInput)
      expect(formInputs[5].props('modelValue')).toBe(150.25)
    })

    it('should update form when initial data changes', async () => {
      const wrapper = createWrapper({
        initialData: { id: 1, symbol: 'AAPL', name: 'Apple Inc.' },
      })

      await wrapper.setProps({
        initialData: { id: 1, symbol: 'GOOGL', name: 'Alphabet Inc.' },
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
      await formInputs[5].vm.$emit('update:modelValue', '650.50')

      await nextTick()

      expect(formInputs[0].props('modelValue')).toBe('TSLA')
      expect(formInputs[1].props('modelValue')).toBe('Tesla Inc.')
      expect(formInputs[2].props('modelValue')).toBe('FINANCIAL_TIMES')
      expect(formInputs[3].props('modelValue')).toBe('STOCK')
      expect(formInputs[4].props('modelValue')).toBe('EUR')
      expect(formInputs[5].props('modelValue')).toBe('650.50')
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
      expect(wrapper.emitted('submit')?.[0]).toEqual([{ ...initialData, baseCurrency: 'EUR' }])
    })

    it('should emit submit event with currentPrice when provided', async () => {
      const initialData = {
        symbol: 'BTC',
        name: 'Bitcoin',
        providerName: 'BINANCE',
        category: 'CRYPTO',
        baseCurrency: 'USD',
        currentPrice: 45000.5,
      }

      const wrapper = createWrapper({ initialData })

      const form = wrapper.find('form')
      await form.trigger('submit')

      expect(wrapper.emitted('submit')).toBeTruthy()
      expect(wrapper.emitted('submit')?.[0]).toEqual([{ ...initialData, baseCurrency: 'EUR' }])
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
        { value: 'BINANCE', text: 'Binance' },
        { value: 'FT', text: 'FT' },
        { value: 'LIGHTYEAR', text: 'Lightyear' },
        { value: 'TRADING212', text: 'Trading212' },
      ] as any
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
      expect(formInputs[4].props('modelValue')).toBe('EUR')
      expect(formInputs[5].props('modelValue')).toBeUndefined()
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

  describe('field validation', () => {
    describe('symbol validation', () => {
      it('should show error when symbol is less than 2 characters', async () => {
        const wrapper = createWrapper()
        const symbolInput = wrapper.findAllComponents(FormInput)[0]

        await symbolInput.vm.$emit('update:modelValue', 'A')
        await symbolInput.vm.$emit('blur')
        await nextTick()

        expect(symbolInput.props('error')).toBe('Symbol must be at least 2 characters')
      })

      it('should not show error for symbol with 2 or more characters', async () => {
        const wrapper = createWrapper()
        const symbolInput = wrapper.findAllComponents(FormInput)[0]

        await symbolInput.vm.$emit('update:modelValue', 'AA')
        await symbolInput.vm.$emit('blur')
        await nextTick()

        expect(symbolInput.props('error')).toBeUndefined()
      })

      it('should allow long symbols without maximum limit', async () => {
        const wrapper = createWrapper()
        const symbolInput = wrapper.findAllComponents(FormInput)[0]

        await symbolInput.vm.$emit('update:modelValue', 'VERYLONGSYMBOLNAME')
        await symbolInput.vm.$emit('blur')
        await nextTick()

        expect(symbolInput.props('error')).toBeUndefined()
      })
    })

    describe('currentPrice validation', () => {
      it('should accept valid positive price', async () => {
        const wrapper = createWrapper()
        const priceInput = wrapper.findAllComponents(FormInput)[5]

        await priceInput.vm.$emit('update:modelValue', '100.50')
        await priceInput.vm.$emit('blur')
        await nextTick()

        expect(priceInput.props('error')).toBeUndefined()
      })

      it('should show error for negative price', async () => {
        const wrapper = createWrapper()
        const priceInput = wrapper.findAllComponents(FormInput)[5]

        await priceInput.vm.$emit('update:modelValue', '-10')
        await priceInput.vm.$emit('blur')
        await nextTick()

        expect(priceInput.props('error')).toBe('Price must be a positive number')
      })

      it('should accept empty price as optional field', async () => {
        const wrapper = createWrapper()
        const priceInput = wrapper.findAllComponents(FormInput)[5]

        await priceInput.vm.$emit('update:modelValue', '')
        await priceInput.vm.$emit('blur')
        await nextTick()

        expect(priceInput.props('error')).toBeUndefined()
      })

      it('should accept price as string value', async () => {
        const wrapper = createWrapper()
        const formInputs = wrapper.findAllComponents(FormInput)

        await formInputs[0].vm.$emit('update:modelValue', 'BTC')
        await formInputs[1].vm.$emit('update:modelValue', 'Bitcoin')
        await formInputs[2].vm.$emit('update:modelValue', 'BINANCE')
        await formInputs[3].vm.$emit('update:modelValue', 'CRYPTO')
        await formInputs[4].vm.$emit('update:modelValue', 'USD')
        await formInputs[5].vm.$emit('update:modelValue', '50000.75')

        const form = wrapper.find('form')
        await form.trigger('submit')

        expect(wrapper.emitted('submit')).toBeTruthy()
        const emittedData = wrapper.emitted('submit')?.[0][0] as any
        expect(emittedData.currentPrice).toBe('50000.75')
      })
    })
  })
})
