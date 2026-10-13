import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { createPortfolioSummaryDto } from '../tests/fixtures'
import { toasts } from './use-toast'
import { useStoredSummaryInterval, useSummaryInterval } from './use-summary-interval'

enableAutoUnmount(afterEach)

const KEY = 'portfolio_summary_interval'
const row = createPortfolioSummaryDto()

const setup = () => {
  const platforms = ref(['LHV'])
  const status = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
  const rows = ref<(typeof row)[] | undefined>(undefined)
  const span = ref('Jun – Dec 2025')
  let result: ReturnType<typeof useSummaryInterval>
  mount({
    setup() {
      result = useSummaryInterval(useStoredSummaryInterval(), platforms, { status, rows, span })
      return {}
    },
    template: '<div />',
  })
  return { result: result!, platforms, status, rows, span }
}

describe('Summary interval', () => {
  beforeEach(() => {
    localStorage.clear()
    toasts.value = []
  })

  it.each([null, 'weekly', 'MONTHLY', ''])('defaults invalid storage %j to Daily', stored => {
    if (stored !== null) localStorage.setItem(KEY, stored)
    const { result } = setup()
    expect(result.interval.value).toBe('daily')
    expect(result.displayInterval.value).toBe('daily')
    expect(localStorage.getItem(KEY)).toBe('daily')
  })

  it('restores Monthly with placeholders and stays silent when ready', async () => {
    localStorage.setItem(KEY, 'monthly')
    const { result, status, rows } = setup()
    expect(result.displayInterval.value).toBe('monthly')
    expect(result.busy.value).toBe(true)
    status.value = 'success'
    rows.value = [row]
    await nextTick()
    expect(result.busy.value).toBe(false)
    expect(result.announcement.value).toBe('')
  })

  it('keeps Daily displayed until the requested Monthly history is ready', async () => {
    const { result, status, rows } = setup()
    result.selectInterval('monthly')
    status.value = 'loading'
    await nextTick()
    expect(result.interval.value).toBe('monthly')
    expect(localStorage.getItem(KEY)).toBe('monthly')
    expect(result.displayInterval.value).toBe('daily')
    expect(result.busy.value).toBe(true)
    status.value = 'success'
    rows.value = [row]
    await nextTick()
    expect(result.displayInterval.value).toBe('monthly')
    expect(result.announcement.value).toBe('Monthly profit Jun – Dec 2025')
    rows.value = [row, { ...row, date: '2026-01-01' }]
    await nextTick()
    expect(result.announcement.value).toBe('Monthly profit Jun – Dec 2025')
  })

  it('switches to usable cached monthly history immediately', async () => {
    const { result, status, rows } = setup()
    status.value = 'success'
    rows.value = [row]
    await nextTick()
    result.selectInterval('monthly')
    await nextTick()
    expect(result.displayInterval.value).toBe('monthly')
    expect(result.busy.value).toBe(false)
  })

  it('returns interval and storage to Daily once on failed activation and allows retry', async () => {
    const { result, status, rows } = setup()
    result.selectInterval('monthly')
    status.value = 'error'
    await nextTick()
    expect(result.interval.value).toBe('daily')
    expect(result.displayInterval.value).toBe('daily')
    expect(localStorage.getItem(KEY)).toBe('daily')
    expect(toasts.value.map(toast => toast.message)).toEqual([
      'Could not load the monthly history. Press Monthly to try again.',
    ])
    status.value = 'loading'
    result.selectInterval('monthly')
    rows.value = []
    status.value = 'success'
    await nextTick()
    expect(result.displayInterval.value).toBe('monthly')
    expect(toasts.value).toHaveLength(1)
  })

  it('retains Monthly after refresh failure when successful history exists', async () => {
    const { result, status, rows } = setup()
    rows.value = [row]
    status.value = 'success'
    result.selectInterval('monthly')
    await nextTick()
    status.value = 'error'
    await nextTick()
    expect(result.displayInterval.value).toBe('monthly')
    expect(result.interval.value).toBe('monthly')
    expect(toasts.value).toHaveLength(0)
  })

  it.each(['success', 'error'] as const)(
    'ignores obsolete %s after returning to Daily',
    async state => {
      const { result, status, rows } = setup()
      result.selectInterval('monthly')
      status.value = 'loading'
      result.selectInterval('daily')
      if (state === 'success') rows.value = [row]
      status.value = state
      await nextTick()
      expect(result.interval.value).toBe('daily')
      expect(result.displayInterval.value).toBe('daily')
      expect(result.announcement.value).toBe('')
      expect(toasts.value).toHaveLength(0)
    }
  )

  it('uses placeholders on a platform change during manual loading and clears its announcement', async () => {
    const { result, status, platforms, rows } = setup()
    result.selectInterval('monthly')
    status.value = 'loading'
    platforms.value = ['SWEDBANK']
    await nextTick()
    expect(result.displayInterval.value).toBe('monthly')
    expect(result.busy.value).toBe(true)
    rows.value = [row]
    status.value = 'success'
    await nextTick()
    expect(result.announcement.value).toBe('')
  })

  it('never overwrites the stored Daily calendar day', async () => {
    localStorage.setItem('portfolio_summary_calendar_day', '2025-12-08')
    const { result, rows, status } = setup()
    rows.value = [row]
    status.value = 'success'
    result.selectInterval('monthly')
    await nextTick()
    result.selectInterval('daily')
    await nextTick()
    expect(localStorage.getItem('portfolio_summary_calendar_day')).toBe('2025-12-08')
  })
})
