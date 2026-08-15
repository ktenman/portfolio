import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ChartRangeFilter from './chart-range-filter.vue'
import { TimeRange } from '../../models/generated/domain-models'

const createWrapper = (selected: TimeRange = TimeRange.SIX_MONTHS) =>
  mount(ChartRangeFilter, { props: { selected } })

describe('ChartRangeFilter', () => {
  it('should render one chip per range in declaration order', () => {
    const labels = createWrapper()
      .findAll('.platform-btn')
      .map(button => button.text())

    expect(labels).toEqual([
      '1D',
      '2D',
      '3D',
      '1W',
      '1M',
      '3M',
      '6M',
      'YTD',
      '1Y',
      '2Y',
      '3Y',
      '4Y',
      '5Y',
      'MAX',
    ])
  })

  it('should mark the selected range as active', () => {
    const active = createWrapper(TimeRange.ONE_YEAR)
      .findAll('.platform-btn')
      .filter(button => button.classes('active'))
      .map(button => button.text())

    expect(active).toEqual(['1Y'])
  })

  it('should emit the clicked range', async () => {
    const wrapper = createWrapper()
    const chips = wrapper.findAll('.platform-btn')

    await chips[chips.length - 1].trigger('click')

    expect(wrapper.emitted('select')).toEqual([['MAX']])
  })
})
