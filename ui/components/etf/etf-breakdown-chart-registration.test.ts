import { describe, it, expect } from 'vitest'
import { Chart } from 'chart.js'
import './etf-breakdown-chart.vue'

describe('EtfBreakdownChart chart.js registration', () => {
  it('should register the doughnut controller so the donut chart renders', () => {
    expect(() => Chart.registry.getController('doughnut')).not.toThrow()
  })

  it('should register the arc element used by donut segments', () => {
    expect(() => Chart.registry.getElement('arc')).not.toThrow()
  })
})
