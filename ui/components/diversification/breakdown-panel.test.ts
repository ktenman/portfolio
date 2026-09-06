import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import BreakdownPanel from './breakdown-panel.vue'
import type { Breakdowns } from '../../composables/use-diversification-result'

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
    props: { breakdowns, benchmark, benchmarkLabel: 'WEBN', coverage: 0.92, ...props },
  })

describe('BreakdownPanel', () => {
  beforeEach(() => localStorage.clear())

  it('renders four tabs in order', () => {
    const labels = mountPanel()
      .findAll('.breakdown-tab:not(.compare-toggle)')
      .map(t => t.text())
    expect(labels).toEqual(['Sectors', 'Industries', 'Holdings', 'Countries'])
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

  it('renders the Other row without a bar', () => {
    const other = mountPanel().findAll('.breakdown-row').slice(-1)[0]
    expect(other.text()).toContain('Other')
    expect(other.find('.row-bar').exists()).toBe(false)
  })

  it('hides the legend and benchmark text without a benchmark', () => {
    const wrapper = mountPanel({ benchmark: null, benchmarkLabel: undefined })
    expect(wrapper.find('.panel-legend').exists()).toBe(false)
    expect(wrapper.find('.row-benchmark').exists()).toBe(false)
  })

  it('shows the coverage badge and hides it when coverage is null', () => {
    expect(mountPanel().find('.coverage-badge').text()).toBe('Covers 92% of portfolio value')
    expect(mountPanel({ coverage: null }).find('.coverage-badge').exists()).toBe(false)
  })

  it('puts a hover title on each row', () => {
    expect(mountPanel().find('.breakdown-row').attributes('title')).toBe(
      'Banks 20.30% · WEBN 10.00%'
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

  it('hides the legend and benchmark text while the comparison is switched off', () => {
    localStorage.setItem('portfolio_benchmark_compare', 'false')
    const wrapper = mountPanel()
    expect([
      wrapper.find('.panel-legend').exists(),
      wrapper.find('.row-benchmark').exists(),
    ]).toEqual([false, false])
  })

  it('persists the comparison toggle', async () => {
    const wrapper = mountPanel()
    await wrapper.find('.compare-input').setValue(false)
    expect(localStorage.getItem('portfolio_benchmark_compare')).toBe('false')
  })

  it('labels the toggle with the benchmark ticker', () => {
    expect(mountPanel().find('.compare-toggle').text()).toBe('vs WEBN')
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
})
