import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createCalendarRow as row, createMonthlyProfit } from '../../tests/fixtures'
import type { MonthlyProfit } from '../../services/monthly-profit'
import MonthlyProfitFigures from './monthly-profit-figures.vue'

const mountFigures = (model: MonthlyProfit = createMonthlyProfit()) =>
  mount(MonthlyProfitFigures, { props: { model } })

const normalised = (text: string) => text.replace(/\s+/g, ' ').trim()

describe('MonthlyProfitFigures', () => {
  it('shows the counts, flat hint and average', () =>
    expect(
      mountFigures()
        .findAll('.figure-value')
        .map(value => normalised(value.text()))
    ).toEqual(['7', '0', '0 under ±0.3%', '+€6,334.83']))

  it('offers only the best month for an all-gain history', async () => {
    const wrapper = mountFigures()
    const buttons = wrapper.findAll('button')

    await buttons[0].trigger('click')

    expect([
      buttons.length,
      wrapper.text().includes('Worst month'),
      wrapper.emitted('select'),
    ]).toEqual([1, false, [['2025-11']]])
  })

  it('sets the amount and month of a shortcut beneath its label', () =>
    expect(
      mountFigures()
        .findAll('.extreme-label, .extreme-value > span')
        .map(span => normalised(span.text()))
    ).toEqual(['Best month by euro profit', '+€12,307.39', 'Nov 2025']))

  it('shows a dash average and the excluded count without single months', () => {
    const wrapper = mountFigures(
      createMonthlyProfit([row('2025-01-15', 0), row('2025-03-31', 100)], '2030-01-01')
    )

    expect([
      normalised(wrapper.findAll('.figure-value')[3].text()),
      wrapper.find('.figures-excluded').text(),
      wrapper.findAll('button').length,
    ]).toEqual(['—', '1 month not counted, covering more than one month', 0])
  })
})
