import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { MONTH_STEPS } from '../../services/monthly-profit'
import DailyProfitLegend from './daily-profit-legend.vue'

const ticksOf = (steps?: number[]) =>
  mount(DailyProfitLegend, { props: { missing: false, steps } })
    .findAll('.legend-tick')
    .map(tick => tick.text())

const mountLegend = (props: { missing: boolean; missingLabel?: string }) =>
  mount(DailyProfitLegend, { props })

describe('DailyProfitLegend steps', () => {
  it('labels the monthly thresholds', () =>
    expect(ticksOf(MONTH_STEPS)).toEqual(['5', '2.5', '1', '±0.3%', '1', '2.5', '5']))

  it('keeps the daily thresholds by default', () =>
    expect(ticksOf()).toEqual(['1.5', '0.75', '0.3', '±0.05%', '0.3', '0.75', '1.5']))

  it('names the missing key after the given label', () =>
    expect(
      mountLegend({ missing: true, missingLabel: 'No 1W change' }).find('.legend-missing').text()
    ).toBe('No 1W change'))
})
