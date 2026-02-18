import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import ReturnPredictions from './return-predictions.vue'

const mockReturnPredictions = {
  predictions: ref([] as any[]),
  hasSufficientData: ref(false),
  dataPointCount: ref(0),
  currentValue: ref(0),
  monthlyInvestment: ref(0),
  isLoading: ref(false),
  error: ref(null as string | null),
}

vi.mock('../../composables/use-return-predictions', () => ({
  useReturnPredictions: vi.fn(() => mockReturnPredictions),
}))

vi.mock('../../utils/formatters', () => ({
  formatCurrencyWithSymbol: vi.fn((v: number) => `€${v?.toFixed(2) ?? '0.00'}`),
}))

vi.mock('vue-chartjs', () => ({
  Line: {
    name: 'Line',
    template: '<canvas data-testid="prediction-chart"></canvas>',
    props: ['data', 'options'],
  },
}))

const fourPredictions = [
  {
    horizon: '1M',
    horizonDays: 30,
    targetDate: '2026-03-19',
    expectedValue: 50400,
    optimisticValue: 52800,
    pessimisticValue: 48100,
    contributions: 500,
  },
  {
    horizon: '3M',
    horizonDays: 91,
    targetDate: '2026-05-19',
    expectedValue: 51200,
    optimisticValue: 55000,
    pessimisticValue: 47500,
    contributions: 1500,
  },
  {
    horizon: '6M',
    horizonDays: 183,
    targetDate: '2026-08-18',
    expectedValue: 52500,
    optimisticValue: 58000,
    pessimisticValue: 47000,
    contributions: 3000,
  },
  {
    horizon: '1Y',
    horizonDays: 365,
    targetDate: '2027-02-17',
    expectedValue: 55000,
    optimisticValue: 65000,
    pessimisticValue: 46000,
    contributions: 6000,
  },
]

describe('return-predictions', () => {
  beforeEach(() => {
    mockReturnPredictions.isLoading.value = false
    mockReturnPredictions.hasSufficientData.value = false
    mockReturnPredictions.dataPointCount.value = 0
    mockReturnPredictions.currentValue.value = 0
    mockReturnPredictions.monthlyInvestment.value = 0
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
    mockReturnPredictions.currentValue.value = 50000
    mockReturnPredictions.predictions.value = fourPredictions
    const wrapper = mount(ReturnPredictions)
    const cards = wrapper.findAll('.col-6.col-lg-3')
    expect(cards).toHaveLength(4)
  })

  it('should display correct horizon labels', () => {
    mockReturnPredictions.hasSufficientData.value = true
    mockReturnPredictions.currentValue.value = 50000
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
    mockReturnPredictions.currentValue.value = 50000
    mockReturnPredictions.predictions.value = [fourPredictions[0]]
    const wrapper = mount(ReturnPredictions)
    expect(wrapper.find('.card-header').text()).toContain('250 days')
  })

  it('should render prediction chart when data is available', () => {
    mockReturnPredictions.hasSufficientData.value = true
    mockReturnPredictions.currentValue.value = 50000
    mockReturnPredictions.predictions.value = fourPredictions
    const wrapper = mount(ReturnPredictions)
    expect(wrapper.findComponent({ name: 'Line' }).exists()).toBe(true)
  })

  it('should not render chart when insufficient data', () => {
    mockReturnPredictions.dataPointCount.value = 15
    const wrapper = mount(ReturnPredictions)
    expect(wrapper.findComponent({ name: 'Line' }).exists()).toBe(false)
  })

  it('should show percentage change on cards', () => {
    mockReturnPredictions.hasSufficientData.value = true
    mockReturnPredictions.currentValue.value = 50000
    mockReturnPredictions.predictions.value = fourPredictions
    const wrapper = mount(ReturnPredictions)
    const changes = wrapper.findAll('.text-success')
    expect(changes.length).toBeGreaterThanOrEqual(4)
  })

  it('should show negative percentage for declining predictions', () => {
    mockReturnPredictions.hasSufficientData.value = true
    mockReturnPredictions.currentValue.value = 55000
    mockReturnPredictions.predictions.value = [fourPredictions[0]]
    const wrapper = mount(ReturnPredictions)
    expect(wrapper.find('.text-danger').exists()).toBe(true)
  })

  it('should show monthly investment in header when available', () => {
    mockReturnPredictions.hasSufficientData.value = true
    mockReturnPredictions.dataPointCount.value = 120
    mockReturnPredictions.currentValue.value = 50000
    mockReturnPredictions.monthlyInvestment.value = 500
    mockReturnPredictions.predictions.value = [fourPredictions[0]]
    const wrapper = mount(ReturnPredictions)
    expect(wrapper.find('.card-header').text()).toContain('€500.00')
    expect(wrapper.find('.card-header').text()).toContain('/mo invested')
  })

  it('should show contributions on cards when present', () => {
    mockReturnPredictions.hasSufficientData.value = true
    mockReturnPredictions.currentValue.value = 50000
    mockReturnPredictions.predictions.value = fourPredictions
    const wrapper = mount(ReturnPredictions)
    expect(wrapper.text()).toContain('incl.')
    expect(wrapper.text()).toContain('invested')
  })
})
