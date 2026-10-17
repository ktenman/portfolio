import { describe, it, expect } from 'vitest'
import { ref, watch } from 'vue'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { buildMonthlyProfit, type MonthlyProfit } from '../services/monthly-profit'
import { mergeHistoricalWithCurrent } from '../services/summary-aggregator'
import { createCalendarRow } from '../tests/fixtures'
import { SERIES_RESPONSE, TODAY_SUMMARY } from '../tests/visual/summary-fixture'
import { useMonthlyProfit } from './use-monthly-profit'

const ROWS = mergeHistoricalWithCurrent(SERIES_RESPONSE, TODAY_SUMMARY)
const NOVEMBER = 'November 2025, +€12,307.39, +8.3%, Best of 7 months, ranked by euro profit'

const build = (rows: PortfolioSummaryDto[] = ROWS) => buildMonthlyProfit(rows, '2026-02-15')!

const without = (key: string) => build(ROWS.filter(row => !row.date.startsWith(key)))

const setup = (initial: MonthlyProfit | null | undefined = build()) => {
  const model = ref<MonthlyProfit | null | undefined>(initial)
  const selection = useMonthlyProfit(model)
  const spoken: string[] = []
  watch(selection.announcement, text => spoken.push(text), { flush: 'sync' })
  return { model, spoken, ...selection }
}

describe('useMonthlyProfit', () => {
  it('selects the latest month when the months arrive', () => {
    const { model, month, announcement } = setup(undefined)

    model.value = build()

    expect([month.value?.key, announcement.value]).toEqual(['2025-12', ''])
  })

  it('announces a committed selection once', () => {
    const { select, spoken } = setup()

    select('2025-11')
    select('2025-11')

    expect(spoken).toEqual([NOVEMBER])
  })

  it('adds the note to the announcement', () => {
    const { select, announcement } = setup()

    select('2025-11')
    select('2025-12')

    expect(announcement.value).toBe(
      'December 2025, +€9,110.23, +5.4%, 3rd best of 7 months, ranked by euro profit. No daily summary for 30 Dec'
    )
  })

  it('keeps the selected month when a refresh adds a month', () => {
    const { model, month, select, spoken } = setup()

    select('2025-11')
    model.value = build([...ROWS, createCalendarRow('2026-01-31', 60000, 180000)])

    expect([
      month.value?.key,
      model.value.months[model.value.months.length - 1].key,
      spoken,
    ]).toEqual(['2025-11', '2026-01', [NOVEMBER]])
  })

  it('falls back to the latest month when the selected one disappears', () => {
    const { model, month, select } = setup()

    select('2025-11')
    model.value = without('2025-11')

    expect(month.value?.key).toBe('2025-12')
  })

  it('drops a preview of a month that disappears', () => {
    const { model, month, preview, previewKey } = setup()

    previewKey.value = '2025-07'
    model.value = without('2025-07')

    expect([preview.value, month.value?.key]).toEqual([undefined, '2025-12'])
  })

  it('ignores unknown months', () => {
    const { month, select, announcement } = setup()

    select('2024-02')

    expect([month.value?.key, announcement.value]).toEqual(['2025-12', ''])
  })
})
