import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BreakdownPanel from './breakdown-panel.vue'
import { DONUT_COLORS } from '../../constants/chart-colors'
import type { Breakdowns } from '../../composables/use-diversification-result'

vi.mock('chart.js', () => {
  const mockChart: any = vi.fn().mockImplementation(function (_canvas: unknown, config: any) {
    return { data: config.data, destroy: vi.fn(), update: vi.fn(), setActiveElements: vi.fn() }
  })
  mockChart.register = vi.fn()
  return { Chart: mockChart, DoughnutController: vi.fn(), ArcElement: vi.fn() }
})

const breakdowns: Breakdowns = {
  sectors: [{ label: 'Finance', value: 30 }],
  industries: [
    { label: 'Banks', value: 20.3 },
    { label: 'Pharma', value: 4.4 },
    { label: 'Software', value: 18.6 },
    { label: 'Tiny', value: 0.05 },
  ],
  holdings: [{ label: 'NVIDIA', value: 4 }],
  countries: [{ label: 'Spain', value: 5, code: 'ES' }],
}

const benchmark: Breakdowns = {
  sectors: [{ label: 'Finance', value: 15 }],
  industries: [
    { label: 'Banks', value: 10 },
    { label: 'Pharma', value: 10 },
    { label: 'Software', value: 10 },
    { label: 'Ölwirtschaft', value: 70 },
  ],
  holdings: [{ label: 'nvidia', value: 2 }],
  countries: [{ label: 'Spain', value: 1, code: 'ES' }],
}

const mountPanel = (props = {}) =>
  mount(BreakdownPanel, {
    props: { breakdowns, benchmark, benchmarkLabel: 'VGLA', ...props },
  })

describe('BreakdownPanel', () => {
  beforeEach(() => localStorage.clear())

  it('renders the four tabs in order as the only dimension controls', () => {
    const group = mountPanel().find('[role="group"][aria-label="Breakdown dimension"]')
    expect(group.findAll('button, input').map(control => control.text())).toEqual([
      'Sectors',
      'Industries',
      'Holdings',
      'Countries',
    ])
  })

  it('opens on Industries by default', () => {
    expect(mountPanel().find('.breakdown-tab.active').text()).toBe('Industries')
  })

  it('falls back to Industries when the stored tab is unknown', () => {
    localStorage.setItem('portfolio_diversification_breakdown_tab', 'companies')
    expect(mountPanel().find('.breakdown-tab.active').text()).toBe('Industries')
  })

  it('renders a real Other country next to the residual row', async () => {
    const wrapper = mountPanel({
      breakdowns: {
        ...breakdowns,
        countries: [...breakdowns.countries, { label: 'Other', value: 2 }],
      },
      benchmark: {
        ...benchmark,
        countries: [...benchmark.countries, { label: 'Ålandinseln', value: 4 }],
      },
    })
    await wrapper.findAll('.breakdown-tab')[3].trigger('click')
    expect(wrapper.findAll('.row-label').map(l => l.text())).toEqual(['Spain', 'Other', 'Other'])
    expect(wrapper.findAll('.row-track.empty')).toHaveLength(1)
  })

  it('restores the persisted tab', () => {
    localStorage.setItem('portfolio_diversification_breakdown_tab', 'countries')
    expect(mountPanel().find('.breakdown-tab.active').text()).toBe('Countries')
  })

  it('flags ratios above 2 and below 0.5 only', () => {
    const flagged = mountPanel()
      .findAll('.row-benchmark')
      .map(b => b.classes('flagged'))
    expect(flagged.slice(0, 3)).toEqual([true, false, true])
  })

  it('shows the benchmark share and ratio on each row', () => {
    expect(mountPanel().findAll('.row-benchmark')[0].text()).toBe('10.00% · 2.03×')
  })

  it('paints each bar with its own colour from the shared palette', () => {
    const bars = mountPanel()
      .findAll('.row-bar')
      .map(bar => bar.attributes('style'))
    expect(bars[0]).toContain(`--row-bar-color: ${DONUT_COLORS[0]}`)
    expect(bars[1]).toContain(`--row-bar-color: ${DONUT_COLORS[1]}`)
  })

  it('renders the Other row without a bar', () => {
    const other = mountPanel().findAll('.breakdown-row').slice(-1)[0]
    expect(other.text()).toContain('Other')
    expect(other.find('.row-bar').exists()).toBe(false)
  })

  it('hides the benchmark text without a benchmark', () => {
    const wrapper = mountPanel({ benchmark: null, benchmarkLabel: undefined })
    expect(wrapper.find('.row-benchmark').exists()).toBe(false)
  })

  it('puts a hover title on each row', () => {
    expect(mountPanel().find('.breakdown-row').attributes('title')).toBe(
      'Banks 20.30% · VGLA 10.00%'
    )
  })

  it('shows a flag image on country rows', async () => {
    const wrapper = mountPanel()
    await wrapper.findAll('.breakdown-tab')[3].trigger('click')
    expect(wrapper.find('.row-flag').attributes('src')).toContain('/es.svg')
  })

  it('places the benchmark tick at the benchmark share of the shared scale', () => {
    expect(mountPanel().find('.row-tick').attributes('style')).toContain('left: 49.26')
  })

  it('hides the benchmark text while the comparison is switched off', () => {
    localStorage.setItem('portfolio_benchmark_compare', 'false')
    expect(mountPanel().find('.row-benchmark').exists()).toBe(false)
  })

  it('persists the comparison toggle', async () => {
    const wrapper = mountPanel()
    await wrapper.find('.compare-input').setValue(false)
    expect(localStorage.getItem('portfolio_benchmark_compare')).toBe('false')
  })

  it('labels the toggle with the benchmark ticker', () => {
    expect(mountPanel().find('.compare-toggle').text()).toBe('vs VGLA')
  })

  it('hides the toggle without a benchmark', () => {
    const wrapper = mountPanel({ benchmark: null, benchmarkLabel: undefined })
    expect(wrapper.find('.compare-toggle').exists()).toBe(false)
  })

  it('shows no benchmark line for a holding the benchmark does not own', async () => {
    const wrapper = mountPanel({
      breakdowns: {
        ...breakdowns,
        holdings: [...breakdowns.holdings, { label: 'Tiny Co', value: 1 }],
      },
    })
    await wrapper.findAll('.breakdown-tab')[2].trigger('click')
    expect(wrapper.findAll('.breakdown-row').map(r => r.find('.row-benchmark').exists())).toEqual([
      true,
      false,
    ])
  })

  it('shows no Other row on Holdings', async () => {
    const wrapper = mountPanel()
    await wrapper.findAll('.breakdown-tab')[2].trigger('click')
    expect(wrapper.findAll('.breakdown-row').map(r => r.find('.row-label').text())).toEqual([
      'NVIDIA',
    ])
  })

  it('opens on the bars', () => {
    expect(mountPanel().find('.breakdown-row').exists()).toBe(true)
  })

  it('replaces the bars with the donut when the donut control is pressed', async () => {
    const wrapper = mountPanel()
    await wrapper.findAll('.view-btn')[0].trigger('click')
    await vi.dynamicImportSettled()
    expect([wrapper.find('canvas').exists(), wrapper.find('.breakdown-row').exists()]).toEqual([
      true,
      false,
    ])
  })

  it('persists the chosen breakdown view', async () => {
    const wrapper = mountPanel()
    await wrapper.findAll('.view-btn')[0].trigger('click')
    expect(localStorage.getItem('portfolio_diversification_breakdown_view')).toBe('donut')
  })

  it('restores the persisted breakdown view', async () => {
    localStorage.setItem('portfolio_diversification_breakdown_view', 'donut')
    const wrapper = mountPanel()
    await vi.dynamicImportSettled()
    expect(wrapper.find('canvas').exists()).toBe(true)
  })

  it('narrows the industry donut to the top count the other dimensions use', async () => {
    const many = Array.from({ length: 20 }, (_, index) => ({
      label: `Industry ${index}`,
      value: 5,
    }))
    localStorage.setItem('portfolio_diversification_breakdown_view', 'donut')
    const wrapper = mountPanel({ breakdowns: { ...breakdowns, industries: many }, benchmark: null })
    await vi.dynamicImportSettled()
    expect(wrapper.findAll('.legend-item')).toHaveLength(16)
  })

  it('widens the industry bars past the donut top count', () => {
    const many = Array.from({ length: 20 }, (_, index) => ({
      label: `Industry ${index}`,
      value: 5,
    }))
    const wrapper = mountPanel({ breakdowns: { ...breakdowns, industries: many }, benchmark: null })
    expect(wrapper.findAll('.breakdown-row')).toHaveLength(20)
  })
})
