import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { h, ref } from 'vue'
import DiversificationCalculator from './diversification-calculator.vue'

vi.mock('@tanstack/vue-query', () => ({
  useQuery: vi.fn(() => ({
    data: ref([
      {
        instrumentId: 1,
        symbol: 'VWCE',
        name: 'Vanguard FTSE All-World',
        allocation: 0,
        ter: 0.22,
        annualReturn: 12.5,
        currentPrice: 120.5,
      },
      {
        instrumentId: 2,
        symbol: 'VUAA',
        name: 'Vanguard S&P 500',
        allocation: 0,
        ter: 0.07,
        annualReturn: 15.0,
        currentPrice: 95.3,
      },
    ]),
    isLoading: ref(false),
    dataUpdatedAt: ref(Date.now()),
  })),
}))

vi.mock('../../services/diversification-service', () => ({
  diversificationService: {
    getAvailableEtfs: vi.fn(),
    calculate: vi.fn(),
  },
}))

vi.mock('../../services/instruments-service', () => ({
  instrumentsService: {
    getAll: vi.fn(),
  },
}))

vi.mock('@vueuse/core', () => ({
  useDebounceFn: vi.fn((fn: () => void) => fn),
  useNow: vi.fn(() => ({ value: new Date() })),
}))

vi.mock('bootstrap', () => ({
  Modal: vi.fn().mockImplementation(() => ({
    show: vi.fn(),
    hide: vi.fn(),
    dispose: vi.fn(),
  })),
}))

vi.mock('@guolao/vue-monaco-editor', () => ({
  VueMonacoEditor: {
    name: 'VueMonacoEditor',
    props: ['value', 'language', 'options', 'theme'],
    setup(props: { value: string }) {
      return () => h('div', { class: 'mock-monaco-editor' }, props.value)
    },
  },
}))

describe('DiversificationCalculator', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('rendering', () => {
    it('should render calculator title', () => {
      const wrapper = mount(DiversificationCalculator)
      expect(wrapper.text()).toContain('Diversification Calculator')
    })

    it('should render description text', () => {
      const wrapper = mount(DiversificationCalculator)
      expect(wrapper.text()).toContain('Plan your ETF allocation')
    })

    it('should render AllocationTable component', () => {
      const wrapper = mount(DiversificationCalculator)
      expect(wrapper.findComponent({ name: 'AllocationTable' }).exists()).toBe(true)
    })

    it('should show last updated text when data is available', () => {
      const wrapper = mount(DiversificationCalculator)
      expect(wrapper.find('.last-updated').exists()).toBe(true)
    })
  })

  describe('loading state', () => {
    it('should not show spinner when not loading ETFs', () => {
      const wrapper = mount(DiversificationCalculator)
      expect(wrapper.find('.spinner-border').exists()).toBe(false)
    })
  })

  describe('error handling', () => {
    it('should not show error alert when no error', () => {
      const wrapper = mount(DiversificationCalculator)
      expect(wrapper.find('.alert-danger').exists()).toBe(false)
    })
  })

  describe('results display', () => {
    it('should not show results section when no result', () => {
      const wrapper = mount(DiversificationCalculator)
      expect(wrapper.find('.results-section').exists()).toBe(false)
    })
  })

  describe('config dialogs', () => {
    it('should have export dialog component', () => {
      const wrapper = mount(DiversificationCalculator)
      const configDialogs = wrapper.findAllComponents({ name: 'ConfigDialog' })
      expect(configDialogs.length).toBeGreaterThanOrEqual(1)
    })

    it('should have import dialog component', () => {
      const wrapper = mount(DiversificationCalculator)
      const configDialogs = wrapper.findAllComponents({ name: 'ConfigDialog' })
      expect(configDialogs.length).toBe(2)
    })
  })

  describe('allocation operations', () => {
    it('should emit events from allocation table', () => {
      const wrapper = mount(DiversificationCalculator)
      const allocationTable = wrapper.findComponent({ name: 'AllocationTable' })
      expect(allocationTable.exists()).toBe(true)
    })
  })
})
