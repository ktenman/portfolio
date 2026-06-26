import { describe, it, expect } from 'vitest'
import { Chart } from 'chart.js'
import './etf-breakdown-chart.vue'

describe('EtfBreakdownChart chart.js registration', () => {
  it('should register the pie controller so the pie chart renders', () => {
    expect(() => Chart.registry.getController('pie')).not.toThrow()
  })

  it('should register the arc element used by pie slices', () => {
    expect(() => Chart.registry.getElement('arc')).not.toThrow()
  })
})
