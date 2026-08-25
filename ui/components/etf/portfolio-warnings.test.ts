import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PortfolioWarnings from './portfolio-warnings.vue'
import {
  PortfolioWarningRule,
  type PortfolioWarningDto,
} from '../../models/generated/domain-models'

const warning = (overrides: Partial<PortfolioWarningDto> = {}): PortfolioWarningDto => ({
  rule: PortfolioWarningRule.LARGEST_HOLDING,
  label: 'Largest holding',
  detail: 'Nestlé S.A.',
  measuredPercentage: 4.2,
  thresholdPercentage: 10,
  breached: false,
  ...overrides,
})

describe('PortfolioWarnings', () => {
  it('should render nothing when there are no warnings', () => {
    const wrapper = mount(PortfolioWarnings, { props: { warnings: [] } })

    expect(wrapper.find('.warnings-card').exists()).toBe(false)
  })

  it('should render one row per warning', () => {
    const wrapper = mount(PortfolioWarnings, {
      props: {
        warnings: [
          warning(),
          warning({ rule: PortfolioWarningRule.SECTOR_CONCENTRATION, label: 'Largest sector' }),
        ],
      },
    })

    expect(wrapper.findAll('.warning-row')).toHaveLength(2)
  })

  it('should show the measured value against its threshold', () => {
    const wrapper = mount(PortfolioWarnings, { props: { warnings: [warning()] } })

    expect(wrapper.find('.warning-measured').text()).toBe('4.20%')
    expect(wrapper.find('.warning-threshold').text()).toBe('/ 10.00%')
  })

  it('should show fees to three decimals because they are fractions of a percent', () => {
    const wrapper = mount(PortfolioWarnings, {
      props: {
        warnings: [
          warning({
            rule: PortfolioWarningRule.AVERAGE_TER,
            label: 'Weighted TER',
            detail: null,
            measuredPercentage: 0.425,
            thresholdPercentage: 0.4,
          }),
        ],
      },
    })

    expect(wrapper.find('.warning-measured').text()).toBe('0.425%')
    expect(wrapper.find('.warning-threshold').text()).toBe('/ 0.400%')
  })

  it('should mark only the breached rows', () => {
    const wrapper = mount(PortfolioWarnings, {
      props: {
        warnings: [
          warning(),
          warning({ rule: PortfolioWarningRule.SECTOR_CONCENTRATION, breached: true }),
        ],
      },
    })

    expect(wrapper.findAll('.warning-row').map(row => row.classes('breached'))).toEqual([
      false,
      true,
    ])
  })

  it('should announce the breached state to screen readers', () => {
    const wrapper = mount(PortfolioWarnings, { props: { warnings: [warning({ breached: true })] } })

    expect(wrapper.find('.sr-only').text()).toBe('Warning:')
  })

  it('should name what the measurement refers to', () => {
    const wrapper = mount(PortfolioWarnings, { props: { warnings: [warning()] } })

    expect(wrapper.find('.warning-detail').text()).toBe('Nestlé S.A.')
  })

  it('should omit the detail when a rule measures the whole portfolio', () => {
    const wrapper = mount(PortfolioWarnings, { props: { warnings: [warning({ detail: null })] } })

    expect(wrapper.find('.warning-detail').exists()).toBe(false)
  })
})
