import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ChartModeToggle from './chart-mode-toggle.vue'
import type { BenchmarkKey } from '../../composables/use-portfolio-chart'

const createWrapper = (selected: BenchmarkKey[] = []) =>
  mount(ChartModeToggle, { props: { selected } })

const activeLabels = (wrapper: ReturnType<typeof createWrapper>) =>
  wrapper
    .findAll('.platform-btn')
    .filter(button => button.classes('active'))
    .map(button => button.text())

describe('ChartModeToggle', () => {
  it('should render the euro and both benchmark chips', () => {
    const labels = createWrapper()
      .findAll('.platform-btn')
      .map(button => button.text())

    expect(labels).toEqual(['€', '% vs S&P 500', '% vs World'])
  })

  it('should mark the euro chip active when no benchmark is selected', () => {
    expect(activeLabels(createWrapper())).toEqual(['€'])
  })

  it('should mark a selected benchmark as active', () => {
    expect(activeLabels(createWrapper(['world']))).toEqual(['% vs World'])
  })

  it('should mark both benchmarks active when both are selected', () => {
    expect(activeLabels(createWrapper(['sp500', 'world']))).toEqual(['% vs S&P 500', '% vs World'])
  })

  it('should emit the clicked mode', async () => {
    const wrapper = createWrapper()

    await wrapper.findAll('.platform-btn')[2].trigger('click')

    expect(wrapper.emitted('select')).toEqual([['world']])
  })

  it('should emit null when the euro chip is clicked', async () => {
    const wrapper = createWrapper(['sp500'])

    await wrapper.findAll('.platform-btn')[0].trigger('click')

    expect(wrapper.emitted('select')).toEqual([[null]])
  })
})
