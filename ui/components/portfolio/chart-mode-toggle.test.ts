import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ChartModeToggle, { type ChartMode } from './chart-mode-toggle.vue'

const createWrapper = (selected: ChartMode = 'value') =>
  mount(ChartModeToggle, { props: { selected } })

describe('ChartModeToggle', () => {
  it('should render the euro and performance chips', () => {
    const labels = createWrapper()
      .findAll('.platform-btn')
      .map(button => button.text())

    expect(labels).toEqual(['€', '% vs S&P 500'])
  })

  it('should mark the selected mode as active', () => {
    const active = createWrapper('performance')
      .findAll('.platform-btn')
      .filter(button => button.classes('active'))
      .map(button => button.text())

    expect(active).toEqual(['% vs S&P 500'])
  })

  it('should emit the clicked mode', async () => {
    const wrapper = createWrapper()

    await wrapper.findAll('.platform-btn')[1].trigger('click')

    expect(wrapper.emitted('select')).toEqual([['performance']])
  })
})
