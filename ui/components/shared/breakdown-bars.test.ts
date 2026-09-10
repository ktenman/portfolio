import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BreakdownBars from './breakdown-bars.vue'
import { DONUT_COLORS } from '../../constants/chart-colors'
import type { ComparedRow } from '../../services/diversification-chart-service'

type Row = ComparedRow & { color?: string }

const rows: Row[] = [
  { label: 'Banken', value: 20.3, benchmark: 10, ratio: 2.03, isOther: false },
  { label: 'Ölwirtschaft', value: 4.4, benchmark: 10, ratio: 0.44, isOther: false },
  { label: 'Other', value: 6.5, isOther: true },
]

const mountBars = (props: Record<string, unknown> = {}) =>
  mount(BreakdownBars, { props: { rows, benchmarkLabel: 'WEBN', ...props } })

describe('BreakdownBars', () => {
  it('renders one row per item', () => {
    expect(mountBars().findAll('.breakdown-row')).toHaveLength(3)
  })

  it('scales the widest bar to the full track', () => {
    expect(mountBars().find('.row-bar').attributes('style')).toContain('width: 100%')
  })

  it('places the benchmark tick at the benchmark share of the shared scale', () => {
    expect(mountBars().find('.row-tick').attributes('style')).toContain('left: 49.26')
  })

  it('renders the Other row without a bar', () => {
    const other = mountBars().findAll('.breakdown-row').slice(-1)[0]
    expect(other.find('.row-bar').exists()).toBe(false)
  })

  it('renders the Other row with an empty track', () => {
    expect(mountBars().findAll('.row-track.empty')).toHaveLength(1)
  })

  it('paints a bar with the colour carried by its row', () => {
    const coloured = rows.map(row => ({ ...row, color: DONUT_COLORS[0] }))
    expect(mountBars({ rows: coloured }).find('.row-bar').attributes('style')).toContain(
      `--row-bar-color: ${DONUT_COLORS[0]}`
    )
  })

  it('leaves the bar unpainted when a row carries no colour', () => {
    expect(mountBars().find('.row-bar').attributes('style')).not.toContain('--row-bar-color')
  })

  it('flags ratios above 2 and below 0.5', () => {
    const flagged = mountBars()
      .findAll('.row-benchmark')
      .map(text => text.classes('flagged'))
    expect(flagged).toEqual([true, true])
  })

  it('shows the benchmark share and ratio on each compared row', () => {
    expect(mountBars().findAll('.row-benchmark')[0].text()).toBe('10.00% · 2.03×')
  })

  it('puts a hover title naming the benchmark on each compared row', () => {
    expect(mountBars().find('.breakdown-row').attributes('title')).toBe(
      'Banken 20.30% · WEBN 10.00%'
    )
  })

  it('puts a hover title without the benchmark on an uncompared row', () => {
    const plain: Row[] = [{ label: 'Banken', value: 20.3, isOther: false }]
    expect(mountBars({ rows: plain }).find('.breakdown-row').attributes('title')).toBe(
      'Banken 20.30%'
    )
  })

  it('hides the benchmark text on a row without a benchmark', () => {
    const plain: Row[] = [{ label: 'Banken', value: 20.3, isOther: false }]
    expect(mountBars({ rows: plain }).find('.row-benchmark').exists()).toBe(false)
  })

  it('shows a flag image on rows carrying a country code', () => {
    const country: Row[] = [{ label: 'Spain', value: 5, code: 'ES', isOther: false }]
    expect(mountBars({ rows: country }).find('.row-flag').attributes('src')).toContain('/es.svg')
  })

  it('cannot divide by zero when every row is empty', () => {
    const empty: Row[] = [{ label: 'Banken', value: 0, isOther: false }]
    expect(mountBars({ rows: empty }).find('.row-bar').attributes('style')).toContain('width: 0%')
  })
})
