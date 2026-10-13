import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useMonthlyProfitSelection } from './use-monthly-profit-selection'
import { buildMonthlyProfit, type MonthlyProfit } from '../services/monthly-profit'
import { createPortfolioSummaryDto } from '../tests/fixtures'

const row = (date: string, totalProfit = 0, totalValue = 1000) =>
  createPortfolioSummaryDto({ date, totalProfit, totalValue })

const ROWS = [
  row('2025-01-31'),
  row('2025-02-27', 10, 1010),
  row('2025-03-31', 30, 1030),
  row('2025-04-30', 20, 1020),
]

const model = (rows = ROWS) => buildMonthlyProfit(rows, '2030-01-15')

const setup = (initial: MonthlyProfit | null | undefined = model()) => {
  const monthly = ref(initial)
  return { monthly, ...useMonthlyProfitSelection(monthly) }
}

describe('useMonthlyProfitSelection', () => {
  it('starts on the latest month without announcing it', () => {
    const { month, preview, announcement } = setup()
    expect([month.value?.key, preview.value, announcement.value]).toEqual([
      '2025-04',
      undefined,
      '',
    ])
  })

  it('has no month without a model', () => {
    expect([undefined, null].map(none => useMonthlyProfitSelection(ref(none)).month.value)).toEqual(
      [undefined, undefined]
    )
  })

  it('announces a committed selection with its notes', () => {
    const { month, announcement, select } = setup()

    select('2025-02')

    expect([month.value?.key, announcement.value]).toEqual([
      '2025-02',
      'February 2025, to 27 Feb, +€10.00, +1.0%. No daily summary after 27 Feb. No daily summary for 1 to 26 Feb',
    ])
  })

  it('ignores the selected month and a month without a result', () => {
    const { month, announcement, select } = setup()

    select('2025-04')
    select('2024-01')

    expect([month.value?.key, announcement.value]).toEqual(['2025-04', ''])
  })

  it('keeps its month when the model is refreshed or gains a newer month', () => {
    const { monthly, month, announcement, select } = setup()
    select('2025-03')
    const announced = announcement.value

    monthly.value = model([...ROWS, row('2025-05-31', 40, 1040)])

    expect([month.value?.key, announcement.value === announced]).toEqual(['2025-03', true])
  })

  it('stays on the latest month when a refresh adds one and nothing was selected', () => {
    const { monthly, month, announcement } = setup()

    monthly.value = model([...ROWS, row('2025-05-31', 40, 1040)])

    expect([month.value?.key, announcement.value]).toEqual(['2025-05', ''])
  })

  it('falls back to the latest month when a refresh drops the selected one', () => {
    const { monthly, month, select } = setup()
    select('2025-02')

    monthly.value = model(ROWS.slice(1))

    expect(month.value?.key).toBe('2025-04')
  })

  it('previews without announcing and drops the preview on a selection', () => {
    const { preview, previewKey, announcement, select } = setup()
    previewKey.value = '2025-02'
    const previewed = [preview.value?.key, announcement.value]

    select('2025-03')

    expect([previewed, preview.value]).toEqual([['2025-02', ''], undefined])
  })
})
