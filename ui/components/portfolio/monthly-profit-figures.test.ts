import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MonthlyProfitFigures from './monthly-profit-figures.vue'
import { buildMonthlyProfit, type MonthlyProfit } from '../../services/monthly-profit'
import { mergeSummariesByDate } from '../../services/summary-aggregator'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../../tests/visual/summary-fixture'

const fixture = () =>
  buildMonthlyProfit(mergeSummariesByDate(SERIES_RESPONSE, [TODAY_SUMMARY]), '2026-01-15')!

const createFigures = (overrides: Partial<MonthlyProfit> = {}) =>
  mount(MonthlyProfitFigures, { props: { monthly: { ...fixture(), ...overrides } } })

describe('MonthlyProfitFigures', () => {
  it('counts the months of each side and averages them', () => {
    const wrapper = createFigures()
    expect([
      wrapper.findAll('.figure-label').map(label => label.text()),
      wrapper.findAll('.figure-value').map(value => value.text().replace(/\s+/g, ' ')),
      wrapper.find('.figure-value.text-gain').text(),
    ]).toEqual([
      ['Up months', 'Down months', 'Flat months', 'Average month'],
      ['7', '0', '0 under ±0.3%', '+€6,334.83'],
      '+€6,334.83',
    ])
  })

  it('prints a dash without an average', () => {
    const wrapper = createFigures({ averageMonth: null })
    expect(wrapper.findAll('.figure-value')[3].text()).toBe('—')
  })

  it('offers the best month ranked by euro profit and no worst month without a loss', async () => {
    const wrapper = createFigures()
    const buttons = wrapper.findAll('button')

    await buttons[0].trigger('click')

    expect([
      buttons.map(button => button.text().replace(/\s+/g, ' ')),
      buttons[0].attributes('aria-description'),
      wrapper.emitted('select'),
    ]).toEqual([['Best month+€12,307.39Nov 2025'], 'Ranked by euro profit', [['2025-11']]])
  })

  it.each([
    [0, undefined],
    [1, '1 month excluded: it covers more than one month'],
    [3, '3 months excluded: they cover more than one month'],
  ])('explains %i excluded intervals', (excluded, text) => {
    const note = createFigures({ excluded }).find('.figures-note')
    expect(note.exists() ? note.text() : undefined).toBe(text)
  })
})
