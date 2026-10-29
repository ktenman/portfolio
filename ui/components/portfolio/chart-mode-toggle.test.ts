import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ChartModeToggle from './chart-mode-toggle.vue'
import type { BenchmarkKey } from '../../composables/use-portfolio-chart'

const createWrapper = (selected: BenchmarkKey[] = []) =>
  mount(ChartModeToggle, { props: { selected } })

const pressedLabels = (wrapper: ReturnType<typeof createWrapper>) =>
  wrapper.findAll('button[aria-pressed="true"]').map(button => button.text())

describe('ChartModeToggle', () => {
  it('should name the chip group Compare', () => {
    const wrapper = createWrapper()
    const label = wrapper.find('[role="group"]').attributes('aria-labelledby')

    expect(wrapper.find(`#${label}`).text()).toBe('Compare')
  })

  it('should render one chip per benchmark', () => {
    const labels = createWrapper()
      .findAll('button')
      .map(button => button.text())

    expect(labels).toEqual(['S&P 500', 'VWCE'])
  })

  it('should press no chip when the chart shows euros', () => {
    expect(pressedLabels(createWrapper())).toEqual([])
  })

  it('should press a selected benchmark', () => {
    expect(pressedLabels(createWrapper(['vwce']))).toEqual(['VWCE'])
  })

  it('should press both benchmarks when both are selected', () => {
    expect(pressedLabels(createWrapper(['sp500', 'vwce']))).toEqual(['S&P 500', 'VWCE'])
  })

  it('should emit the clicked benchmark', async () => {
    const wrapper = createWrapper(['sp500'])

    await wrapper.findAll('button')[1].trigger('click')

    expect(wrapper.emitted('select')).toEqual([['vwce']])
  })
})
