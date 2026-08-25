import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ChartModeToggle, { type ChartMode } from './chart-mode-toggle.vue'

const createWrapper = (selected: ChartMode = 'value', worldAvailable = true) =>
  mount(ChartModeToggle, { props: { selected, worldAvailable } })

describe('ChartModeToggle', () => {
  it('should render the euro and both benchmark chips', () => {
    const labels = createWrapper()
      .findAll('.platform-btn')
      .map(button => button.text())

    expect(labels).toEqual(['€', '% vs S&P 500', '% vs World'])
  })

  it('should hide the world chip when the world series is unavailable', () => {
    const labels = createWrapper('value', false)
      .findAll('.platform-btn')
      .map(button => button.text())

    expect(labels).toEqual(['€', '% vs S&P 500'])
  })

  it('should mark the selected mode as active', () => {
    const active = createWrapper('world')
      .findAll('.platform-btn')
      .filter(button => button.classes('active'))
      .map(button => button.text())

    expect(active).toEqual(['% vs World'])
  })

  it('should emit the clicked mode', async () => {
    const wrapper = createWrapper()

    await wrapper.findAll('.platform-btn')[2].trigger('click')

    expect(wrapper.emitted('select')).toEqual([['world']])
  })
})
