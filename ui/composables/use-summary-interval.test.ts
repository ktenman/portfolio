import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { effectScope, nextTick, ref, type EffectScope } from 'vue'
import { useSummaryInterval } from './use-summary-interval'
import type { HistoryStatus } from './use-summary-calendar-query'
import { toasts } from './use-toast'

const KEY = 'portfolio_summary_interval'
const FAILURE = 'Could not load the monthly history. Press Monthly to try again.'

describe('useSummaryInterval', () => {
  let scope: EffectScope

  const setup = (initial: HistoryStatus = 'idle') => {
    const status = ref<HistoryStatus>(initial)
    const platforms = ref<string[]>([])
    const load = vi.fn()
    scope = effectScope()
    const state = scope.run(() => useSummaryInterval({ status, load }, platforms))!
    return { ...state, status, platforms, load }
  }

  const become = async (status: { value: HistoryStatus }, ...values: HistoryStatus[]) => {
    for (const value of values) {
      status.value = value
      await nextTick()
    }
  }

  beforeEach(() => {
    localStorage.clear()
    toasts.value = []
    vi.useFakeTimers()
  })

  afterEach(() => {
    scope.stop()
    vi.useRealTimers()
  })

  it('is Daily without a stored interval and asks for nothing', () => {
    const { interval, view, load } = setup()
    expect([interval.value, view.value, load.mock.calls.length]).toEqual(['daily', 'daily', 0])
  })

  it.each(['weekly', '{}', ''])('reads the stored %j as Daily and rewrites it', stored => {
    localStorage.setItem(KEY, stored)
    const { interval } = setup()
    expect([interval.value, localStorage.getItem(KEY)]).toEqual(['daily', 'daily'])
  })

  it('keeps Daily on screen while the months of a pressed Monthly load', async () => {
    const { interval, view, isLoadingMonths, isUserSwitch, select, status, load } = setup()

    select('monthly')
    const loading = [interval.value, view.value, isLoadingMonths.value, isUserSwitch.value]
    await become(status, 'loading', 'success')

    expect([loading, load.mock.calls.length, view.value, isLoadingMonths.value]).toEqual([
      ['monthly', 'daily', true, true],
      1,
      'monthly',
      false,
    ])
  })

  it('shows a placeholder, never Daily, for a stored Monthly', () => {
    localStorage.setItem(KEY, 'monthly')
    const { view, isLoadingMonths, isUserSwitch, load } = setup()
    expect([view.value, isLoadingMonths.value, isUserSwitch.value, load.mock.calls.length]).toEqual(
      ['placeholder', false, false, 1]
    )
  })

  it('shows a placeholder for a new platform selection until its months arrive', async () => {
    const { view, select, status, platforms, load } = setup()
    select('monthly')
    await become(status, 'loading', 'success')

    platforms.value = ['LHV']
    status.value = 'idle'
    await nextTick()
    const waiting = [view.value, load.mock.calls.length]
    await become(status, 'loading', 'success')

    expect([waiting, view.value]).toEqual([['placeholder', 2], 'monthly'])
  })

  it('asks for nothing when the platforms change in Daily', async () => {
    const { platforms, load } = setup()
    platforms.value = ['LHV']
    await nextTick()
    expect(load).not.toHaveBeenCalled()
  })

  it('returns to Daily with one message when the months fail, and asks again on the next press', async () => {
    const { interval, view, select, status, load } = setup()
    select('monthly')
    await become(status, 'loading', 'error')
    const failed = [
      interval.value,
      view.value,
      localStorage.getItem(KEY),
      toasts.value.map(toast => toast.message),
    ]

    select('monthly')

    expect([failed, load.mock.calls.length]).toEqual([['daily', 'daily', 'daily', [FAILURE]], 2])
  })

  it('reports one failure however often the switch was pressed while loading', async () => {
    const { interval, select, status } = setup()
    select('monthly')
    await become(status, 'loading')
    select('daily')
    select('monthly')
    select('daily')
    select('monthly')

    await become(status, 'error')

    expect([interval.value, toasts.value.length]).toEqual(['daily', 1])
  })

  it.each<HistoryStatus>(['error', 'success'])(
    'stays in Daily without a message when a late %s arrives',
    async late => {
      const { interval, view, select, status } = setup()
      select('monthly')
      await become(status, 'loading')
      select('daily')

      await become(status, late)

      expect([interval.value, view.value, localStorage.getItem(KEY), toasts.value]).toEqual([
        'daily',
        'daily',
        'daily',
        [],
      ])
    }
  )

  it('shows Monthly at once when its months are already there', () => {
    const { view, select } = setup('success')
    select('monthly')
    expect(view.value).toBe('monthly')
  })

  it('does nothing when the selected interval is pressed again', () => {
    const { select, isUserSwitch, load } = setup('success')
    select('monthly')
    isUserSwitch.value = false

    select('monthly')

    expect([isUserSwitch.value, load.mock.calls.length]).toEqual([false, 1])
  })
})
