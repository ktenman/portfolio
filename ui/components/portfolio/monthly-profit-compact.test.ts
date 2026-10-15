import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DailyProfitCell from './daily-profit-cell.vue'
import MonthlyProfitCompact from './monthly-profit-compact.vue'
import { buildMonthlyProfit, type MonthlyProfit } from '../../services/monthly-profit'
import { mergeSummariesByDate } from '../../services/summary-aggregator'
import { createPortfolioSummaryDto } from '../../tests/fixtures'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../../tests/visual/summary-fixture'

const fixture = () =>
  buildMonthlyProfit(mergeSummariesByDate(SERIES_RESPONSE, [TODAY_SUMMARY]), '2026-01-15')!

const row = (date: string, totalProfit = 0, totalValue = 1000) =>
  createPortfolioSummaryDto({ date, totalProfit, totalValue })

const sparse = () =>
  buildMonthlyProfit(
    [
      row('2021-05-31'),
      row('2021-06-30', -50, 950),
      row('2021-07-31', -50, 950),
      row('2021-09-30', 30, 1030),
    ],
    '2030-01-15'
  )!

const createCompact = (monthly: MonthlyProfit = fixture()) =>
  mount(MonthlyProfitCompact, { props: { monthly } })

const texts = (wrapper: ReturnType<typeof createCompact>, selector: string) =>
  wrapper.findAll(selector).map(element => element.text().replace(/\s+/g, ' '))

describe('MonthlyProfitCompact', () => {
  it('heads the card with the counts and the span', () => {
    const wrapper = createCompact()
    expect([
      wrapper.find('h2').text(),
      wrapper.find('.compact-counts').text(),
      wrapper.find('.compact-span').text(),
    ]).toEqual(['Monthly profit', '7 up · 0 down', 'Jun – Dec 2025'])
  })

  it('draws a year as twelve places with its result beside them', () => {
    const wrapper = createCompact()
    expect([
      texts(wrapper, '.dot-letter').join(''),
      texts(wrapper, '.dot-year'),
      wrapper.findAll('.dot-place').length,
      wrapper.findAllComponents(DailyProfitCell).map(dot => dot.props('variant')),
      texts(wrapper, '.dot-total'),
    ]).toEqual([
      'JFMAMJJASOND',
      ['2025'],
      12,
      ['gain-1', 'gain-3', 'gain-4', 'gain-3', 'gain-3', 'gain-4', 'gain-4'],
      ['+37.6%'],
    ])
  })

  it('draws a missing month dashed, a flat month as a mark and a year without a result as a dash', () => {
    const [year] = sparse().years
    const wrapper = createCompact(sparse())
    const unknown = createCompact({ ...sparse(), years: [{ ...year, percent: null }] })
    expect([
      wrapper.findAllComponents(DailyProfitCell).map(dot => dot.props('variant')),
      texts(wrapper, '.dot-total'),
      texts(unknown, '.dot-total'),
    ]).toEqual([['loss-4', 'flat', 'missing', 'gain-4'], ['+3.0%'], ['—']])
  })

  it('hides the dots from assistive technology and gives them no behaviour', async () => {
    const wrapper = createCompact()
    const block = wrapper.find('.dot-block')

    await wrapper.findComponent(DailyProfitCell).trigger('click')

    expect([
      block.attributes('aria-hidden'),
      block.findAll('[tabindex], button, [role]').length,
      wrapper.emitted('select'),
    ]).toEqual(['true', 0, undefined])
  })

  it('lists the flat months, the average and the best month with its own colours', () => {
    const wrapper = createCompact()
    expect([
      texts(wrapper, '.compact-label'),
      texts(wrapper, '.compact-value'),
      texts(wrapper, '.compact-extreme .compact-sub').map(sub =>
        /^Closed at €[\d,]+\.\d\d$/.test(sub)
      ),
      texts(wrapper, '.compact-amount'),
      texts(wrapper, '.compact-percent'),
      wrapper.find('.compact-amount').classes('text-gain'),
      wrapper.find('.compact-percent').classes('text-gain'),
    ]).toEqual([
      ['Flat months', 'Average month', 'Best month · Nov 2025'],
      ['0', '+€6,334.83'],
      [true],
      ['+€12,307.39'],
      ['+8.3%'],
      true,
      true,
    ])
  })

  it('prints a dash without an average and names the worst month', () => {
    const wrapper = createCompact({ ...sparse(), averageMonth: null })
    expect([texts(wrapper, '.compact-value')[1], texts(wrapper, '.compact-label')[2]]).toEqual([
      '—',
      'Worst month · Jun 2021',
    ])
  })

  it('adds the year notes and the excluded note as rows of small text', () => {
    expect(texts(createCompact(sparse()), '.compact-note')).toEqual([
      '2021 from 31 May to 30 Sep',
      '1 month excluded: it covers more than one month',
    ])
  })
})
