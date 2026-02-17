import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import ReturnPredictions from './return-predictions.vue'

const mockReturnPredictions = {
  predictions: ref([] as any[]),
  hasSufficientData: ref(false),
  dataPointCount: ref(0),
  isLoading: ref(false),
  error: ref(null as string | null),
}

vi.mock('../../composables/use-return-predictions', () => ({
  useReturnPredictions: vi.fn(() => mockReturnPredictions),
}))

vi.mock('../../utils/formatters', () => ({
  formatCurrencyWithSymbol: vi.fn((v: number) => `€${v?.toFixed(2) ?? '0.00'}`),
}))

const fourPredictions = [
  {
    horizon: '1M',
    horizonDays: 30,
    targetDate: '2026-03-19',
    xirrProjectedValue: 50493,
    expectedValue: 50400,
    optimisticValue: 52800,
    pessimisticValue: 48100,
  },
  {
    horizon: '3M',
    horizonDays: 91,
    targetDate: '2026-05-19',
    xirrProjectedValue: 51500,
    expectedValue: 51200,
    optimisticValue: 55000,
    pessimisticValue: 47500,
  },
  {
    horizon: '6M',
    horizonDays: 183,
    targetDate: '2026-08-18',
    xirrProjectedValue: 53000,
    expectedValue: 52500,
    optimisticValue: 58000,
    pessimisticValue: 47000,
  },
  {
    horizon: '1Y',
    horizonDays: 365,
    targetDate: '2027-02-17',
    xirrProjectedValue: 56000,
    expectedValue: 55000,
    optimisticValue: 65000,
    pessimisticValue: 46000,
  },
]

describe('return-predictions', () => {
  beforeEach(() => {
    mockReturnPredictions.isLoading.value = false
    mockReturnPredictions.hasSufficientData.value = false
    mockReturnPredictions.dataPointCount.value = 0
    mockReturnPredictions.predictions.value = []
    mockReturnPredictions.error.value = null
  })

  it('should show skeleton loader when loading', () => {
    mockReturnPredictions.isLoading.value = true
    const wrapper = mount(ReturnPredictions)
    expect(wrapper.findComponent({ name: 'SkeletonLoader' }).exists()).toBe(true)
    expect(wrapper.find('.alert-info').exists()).toBe(false)
    expect(wrapper.find('.row').exists()).toBe(false)
  })

  it('should show error message when request fails', () => {
    mockReturnPredictions.error.value = 'Network error'
    const wrapper = mount(ReturnPredictions)
    expect(wrapper.find('.alert-danger').text()).toContain('Failed to load predictions')
  })

  it('should show insufficient data message when not enough history', () => {
    mockReturnPredictions.dataPointCount.value = 15
    const wrapper = mount(ReturnPredictions)
    expect(wrapper.find('.alert-info').text()).toContain('Insufficient data')
    expect(wrapper.find('.alert-info').text()).toContain('15 days available')
  })

  it('should render four prediction cards when data is available', () => {
    mockReturnPredictions.hasSufficientData.value = true
    mockReturnPredictions.dataPointCount.value = 120
    mockReturnPredictions.predictions.value = fourPredictions
    const wrapper = mount(ReturnPredictions)
    const cards = wrapper.findAll('.col-6.col-lg-3')
    expect(cards).toHaveLength(4)
  })

  it('should display correct horizon labels', () => {
    mockReturnPredictions.hasSufficientData.value = true
    mockReturnPredictions.predictions.value = fourPredictions
    const wrapper = mount(ReturnPredictions)
    const titles = wrapper.findAll('.card-title')
    expect(titles[0].text()).toBe('1 Month')
    expect(titles[1].text()).toBe('3 Months')
    expect(titles[2].text()).toBe('6 Months')
    expect(titles[3].text()).toBe('1 Year')
  })

  it('should show data point count in header', () => {
    mockReturnPredictions.hasSufficientData.value = true
    mockReturnPredictions.dataPointCount.value = 250
    mockReturnPredictions.predictions.value = [fourPredictions[0]]
    const wrapper = mount(ReturnPredictions)
    expect(wrapper.find('.card-header').text()).toContain('250 days')
  })
})
