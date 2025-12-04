import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import TransactionForm from './transaction-form.vue'
import FormInput from '../shared/form-input.vue'
import { ProviderName } from '../../models/generated/domain-models'
import { createInstrumentDto } from '../../tests/fixtures'

vi.mock('../../utils/formatters', async () => {
  const actual = await vi.importActual('../../utils/formatters')
  return {
    ...actual,
    formatCurrency: (value: number) => `$${value.toFixed(2)}`,
  }
})

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

describe('TransactionForm', () => {
  const mockInstruments = [
    createInstrumentDto({
      id: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
      currentPrice: 150.5,
      providerName: ProviderName.FT,
      category: 'STOCK',
    }),
    createInstrumentDto({
      id: 2,
      symbol: 'BTC',
      name: 'Bitcoin',
      currentPrice: 45000,
      providerName: ProviderName.BINANCE,
      category: 'CRYPTO',
    }),
    createInstrumentDto({
      id: 3,
      symbol: 'ETH',
      name: 'Ethereum',
      currentPrice: 0,
      providerName: ProviderName.BINANCE,
      category: 'CRYPTO',
    }),
  ]
  const createWrapper = (props = {}) => {
    return mount(TransactionForm, {
      props: {
        instruments: mockInstruments,
        ...props,
      },
      global: {
        components: { FormInput },
      },
    })
  }

  const getTodayDate = () => new Date().toISOString().split('T')[0]

  describe('initial rendering', () => {
    it('should render all form fields', () => {
      const wrapper = createWrapper()

      const formInputs = wrapper.findAllComponents(FormInput)
      expect(formInputs).toHaveLength(8)

      expect(formInputs[0].props('label')).toBe('InstrumentDto')
      expect(formInputs[1].props('label')).toBe('Platform')
      expect(formInputs[2].props('label')).toBe('Transaction Type')
      expect(formInputs[3].props('label')).toBe('Quantity')
      expect(formInputs[4].props('label')).toBe('Price')
      expect(formInputs[5].props('label')).toBe('Fee')
      expect(formInputs[6].props('label')).toBe('Currency')
      expect(formInputs[7].props('label')).toBe('Transaction Date')
    })

    it('should set today as default transaction date', () => {
      const wrapper = createWrapper()

      const dateInput = wrapper.findAllComponents(FormInput)[7]
      expect(dateInput.props('modelValue')).toBe(getTodayDate())
    })

    it('should not show total value initially', () => {
      const wrapper = createWrapper()

      expect(wrapper.find('.alert-info').exists()).toBe(false)
    })
  })

  describe('instrument options', () => {
    it('should format instrument options correctly', () => {
      const wrapper = createWrapper()

      const instrumentInput = wrapper.findAllComponents(FormInput)[0]
      expect(instrumentInput.props('options')).toEqual([
        { value: 1, text: 'AAPL - Apple Inc.' },
        { value: 2, text: 'BTC - Bitcoin' },
        { value: 3, text: 'ETH - Ethereum' },
      ])
    })

    it('should handle empty instruments list', () => {
      const wrapper = createWrapper({ instruments: [] })

      const instrumentInput = wrapper.findAllComponents(FormInput)[0]
      expect(instrumentInput.props('options')).toEqual([])
    })
  })

  describe('price auto-population', () => {
    it('should auto-populate price when instrument is selected', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[0].vm.$emit('update:modelValue', 1)
      await nextTick()

      expect(formInputs[4].props('modelValue')).toBe('150.5')
    })

    it('should not update price for instrument with zero price', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[0].vm.$emit('update:modelValue', 3)
      await nextTick()

      expect(formInputs[4].props('modelValue')).toBe('')
    })

    it('should not update price for non-existent instrument', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[0].vm.$emit('update:modelValue', 999)
      await nextTick()

      expect(formInputs[4].props('modelValue')).toBe('')
    })

    it('should update price when changing between instruments', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[0].vm.$emit('update:modelValue', 1)
      await nextTick()
      expect(formInputs[4].props('modelValue')).toBe('150.5')

      await formInputs[0].vm.$emit('update:modelValue', 2)
      await nextTick()
      expect(formInputs[4].props('modelValue')).toBe('45000')
    })

    it('should clear price when switching from valid to invalid price instrument', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[0].vm.$emit('update:modelValue', 1)
      await nextTick()
      expect(formInputs[4].props('modelValue')).toBe('150.5')

      await formInputs[0].vm.$emit('update:modelValue', 3)
      await nextTick()
      expect(formInputs[4].props('modelValue')).toBe('')
    })
  })

  describe('total value calculation', () => {
    it('should calculate and display total value', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[3].vm.$emit('update:modelValue', 10)
      await formInputs[4].vm.$emit('update:modelValue', 150)
      await nextTick()

      const totalValueAlert = wrapper.find('.alert-info')
      expect(totalValueAlert.exists()).toBe(true)
      expect(totalValueAlert.text()).toBe('Total Value: €1,500.00')
    })

    it('should update total value when quantity changes', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[3].vm.$emit('update:modelValue', 5)
      await formInputs[4].vm.$emit('update:modelValue', 100)
      await nextTick()

      expect(wrapper.find('.alert-info').text()).toBe('Total Value: €500.00')

      await formInputs[3].vm.$emit('update:modelValue', 15)
      await nextTick()

      expect(wrapper.find('.alert-info').text()).toBe('Total Value: €1,500.00')
    })

    it('should handle decimal values in calculation', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[3].vm.$emit('update:modelValue', 0.5)
      await formInputs[4].vm.$emit('update:modelValue', 45000)
      await nextTick()

      expect(wrapper.find('.alert-info').text()).toBe('Total Value: €22,500.00')
    })

    it('should not show total value with zero quantity or price', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[3].vm.$emit('update:modelValue', 0)
      await formInputs[4].vm.$emit('update:modelValue', 100)
      await nextTick()

      expect(wrapper.find('.alert-info').exists()).toBe(false)
    })
  })

  describe('form submission', () => {
    it('should emit submit event with complete form data', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[0].vm.$emit('update:modelValue', 1)
      await formInputs[1].vm.$emit('update:modelValue', 'BINANCE')
      await formInputs[2].vm.$emit('update:modelValue', 'BUY')
      await formInputs[3].vm.$emit('update:modelValue', 10)
      await formInputs[4].vm.$emit('update:modelValue', 150)
      await formInputs[5].vm.$emit('update:modelValue', 5)
      await formInputs[6].vm.$emit('update:modelValue', 'USD')
      await formInputs[7].vm.$emit('update:modelValue', '2023-12-31')

      const form = wrapper.find('form')
      await form.trigger('submit')

      expect(wrapper.emitted('submit')).toBeTruthy()
      expect(wrapper.emitted('submit')?.[0]).toEqual([
        {
          instrumentId: 1,
          platform: 'BINANCE',
          transactionType: 'BUY',
          quantity: 10,
          price: 150,
          commission: 5,
          currency: 'USD',
          transactionDate: '2023-12-31',
        },
      ])
    })

    it('should not emit submit when required fields are missing', async () => {
      const wrapper = createWrapper()

      const form = wrapper.find('form')
      await form.trigger('submit')

      expect(wrapper.emitted('submit')).toBeFalsy()
    })
  })

  describe('with initial data', () => {
    it('should populate form with initial data', () => {
      const initialData = {
        instrumentId: 2,
        platform: 'COINBASE',
        transactionType: 'SELL',
        quantity: 0.5,
        price: 50000,
        transactionDate: '2023-01-15',
      }

      const wrapper = createWrapper({ initialData })
      const formInputs = wrapper.findAllComponents(FormInput)

      expect(formInputs[0].props('modelValue')).toBe(2)
      expect(formInputs[1].props('modelValue')).toBe('COINBASE')
      expect(formInputs[2].props('modelValue')).toBe('SELL')
      expect(formInputs[3].props('modelValue')).toBe('0.5')
      expect(formInputs[4].props('modelValue')).toBe('50000')
      expect(formInputs[7].props('modelValue')).toBe('2023-01-15')
    })

    it('should not override initial price with instrument price', async () => {
      const initialData = {
        instrumentId: 1,
        price: 200,
      }

      const wrapper = createWrapper({ initialData })
      await nextTick()

      const priceInput = wrapper.findAllComponents(FormInput)[4]
      expect(priceInput.props('modelValue')).toBe('200')
    })

    it('should update form when initial data changes', async () => {
      const wrapper = createWrapper({
        initialData: { quantity: 5, price: 100 },
      })

      await wrapper.setProps({
        initialData: { quantity: 10, price: 200 },
      })
      await nextTick()

      const formInputs = wrapper.findAllComponents(FormInput)
      expect(formInputs[3].props('modelValue')).toBe('10')
      expect(formInputs[4].props('modelValue')).toBe('200')
    })
  })

  describe('field constraints', () => {
    it('should set correct number field constraints', () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      const quantityInput = formInputs[3]
      expect(quantityInput.props('type')).toBe('number')
      expect(quantityInput.props('step')).toBe('0.00000001')

      const priceInput = formInputs[4]
      expect(priceInput.props('type')).toBe('number')
      expect(priceInput.props('step')).toBe('0.001')
    })

    it('should show all form fields as rendered', () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      expect(formInputs).toHaveLength(8)
      expect(formInputs[0].props('label')).toBe('InstrumentDto')
      expect(formInputs[1].props('label')).toBe('Platform')
      expect(formInputs[2].props('label')).toBe('Transaction Type')
      expect(formInputs[3].props('label')).toBe('Quantity')
      expect(formInputs[4].props('label')).toBe('Price')
      expect(formInputs[5].props('label')).toBe('Fee')
      expect(formInputs[6].props('label')).toBe('Currency')
      expect(formInputs[7].props('label')).toBe('Transaction Date')
    })
  })

  describe('form validation', () => {
    it('should validate quantity is positive', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)
      const quantityInput = formInputs[3]

      await quantityInput.vm.$emit('update:modelValue', -5)
      await quantityInput.vm.$emit('blur')
      await nextTick()

      expect(quantityInput.props('error')).toBeTruthy()
    })

    it('should validate price is positive', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)
      const priceInput = formInputs[4]

      await priceInput.vm.$emit('update:modelValue', -100)
      await priceInput.vm.$emit('blur')
      await nextTick()

      expect(priceInput.props('error')).toBeTruthy()
    })

    it('should show user-friendly error for empty quantity', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)
      const quantityInput = formInputs[3]

      await quantityInput.vm.$emit('update:modelValue', '')
      await quantityInput.vm.$emit('blur')
      await nextTick()

      expect(quantityInput.props('error')).toBe('Quantity is required')
    })

    it('should show user-friendly error for empty price', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)
      const priceInput = formInputs[4]

      await priceInput.vm.$emit('update:modelValue', '')
      await priceInput.vm.$emit('blur')
      await nextTick()

      expect(priceInput.props('error')).toBe('Price is required')
    })

    it('should show user-friendly error for small quantity', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)
      const quantityInput = formInputs[3]

      await quantityInput.vm.$emit('update:modelValue', 0.000000001)
      await quantityInput.vm.$emit('blur')
      await nextTick()

      expect(quantityInput.props('error')).toBe('Quantity is too small')
    })

    it('should show user-friendly error for small price', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)
      const priceInput = formInputs[4]

      await priceInput.vm.$emit('update:modelValue', 0.001)
      await priceInput.vm.$emit('blur')
      await nextTick()

      expect(priceInput.props('error')).toBe('Price is too small')
    })

    it('should handle form submission', async () => {
      const wrapper = createWrapper()
      const form = wrapper.find('form')
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[0].vm.$emit('update:modelValue', 1)
      await formInputs[1].vm.$emit('update:modelValue', 'BINANCE')
      await formInputs[2].vm.$emit('update:modelValue', 'BUY')
      await formInputs[3].vm.$emit('update:modelValue', 10)
      await formInputs[4].vm.$emit('update:modelValue', 150)
      await formInputs[5].vm.$emit('update:modelValue', '2023-12-31')

      await form.trigger('submit')

      expect(wrapper.emitted('submit')).toBeTruthy()
    })

    it('should handle edge case decimal values', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[3].vm.$emit('update:modelValue', 0.00000001)
      await formInputs[4].vm.$emit('update:modelValue', 0.01)
      await nextTick()

      const totalValueAlert = wrapper.find('.alert-info')
      expect(totalValueAlert.exists()).toBe(true)
      expect(totalValueAlert.text()).toBe('Total Value: €0.00')
    })

    it('should handle very large values correctly', async () => {
      const wrapper = createWrapper()
      const formInputs = wrapper.findAllComponents(FormInput)

      await formInputs[3].vm.$emit('update:modelValue', 1000000)
      await formInputs[4].vm.$emit('update:modelValue', 999999.99)
      await nextTick()

      const totalValueAlert = wrapper.find('.alert-info')
      expect(totalValueAlert.text()).toBe('Total Value: €999,999,990,000.00')
    })
  })

  describe('date validation', () => {
    it('should accept valid date formats', async () => {
      const wrapper = createWrapper()
      const dateInput = wrapper.findAllComponents(FormInput)[7]

      await dateInput.vm.$emit('update:modelValue', '2024-01-01')
      expect(dateInput.props('modelValue')).toBe('2024-01-01')
    })

    it('should handle date input type properly', () => {
      const wrapper = createWrapper()
      const dateInput = wrapper.findAllComponents(FormInput)[7]

      expect(dateInput.props('type')).toBe('date')
    })
  })

  describe('platform options', () => {
    it('should provide correct platform options', async () => {
      const wrapper = createWrapper()
      await nextTick()
      await nextTick()

      const platformInput = wrapper.findAllComponents(FormInput)[1]

      const options = platformInput.props('options') as Array<{ value: string; text: string }>
      const values = options.map(opt => opt.value)

      expect(values).toContain('AVIVA')
      expect(values).toContain('BINANCE')
      expect(values).toContain('COINBASE')
      expect(values).toContain('LHV')
      expect(values).toContain('LIGHTYEAR')
      expect(values).toContain('SWEDBANK')
      expect(values).toContain('TRADING212')
      expect(values).toContain('UNKNOWN')
    })
  })

  describe('transaction type options', () => {
    it('should provide correct transaction type options', async () => {
      const wrapper = createWrapper()
      await nextTick()
      await nextTick()

      const typeInput = wrapper.findAllComponents(FormInput)[2]

      expect(typeInput.props('options')).toEqual([
        { value: 'BUY', text: 'Buy' },
        { value: 'SELL', text: 'Sell' },
      ])
    })
  })
})
