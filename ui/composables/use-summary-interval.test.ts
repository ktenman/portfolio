import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { computed, nextTick, ref, watch, type WritableComputedRef } from 'vue'
import { enableAutoUnmount } from '@vue/test-utils'
import { renderWithProviders } from '../tests/test-utils'
import { createMonthlyProfit } from '../tests/fixtures'
import { STORAGE_KEYS } from '../constants'
import { NOT_ENOUGH_HISTORY, type MonthlyProfit } from '../services/monthly-profit'
import { toasts } from './use-toast'
import { useStoredInterval, useSummaryInterval, type SummaryInterval } from './use-summary-interval'

enableAutoUnmount(afterEach)

const KEY = STORAGE_KEYS.SUMMARY_INTERVAL
const FAILED = 'Could not load the monthly history. Press Monthly to try again.'

const setup = (model?: MonthlyProfit | null, error: Error | null = null) => {
  const source = {
    model: ref<MonthlyProfit | null | undefined>(model),
    error: ref<Error | null>(error),
    platforms: ref(['LHV']),
  }
  let state!: ReturnType<typeof useSummaryInterval> & {
    interval: WritableComputedRef<SummaryInterval>
  }
  renderWithProviders({
    setup() {
      const interval = useStoredInterval()
      state = { ...useSummaryInterval(interval, source), interval }
      return {}
    },
    template: '<div></div>',
  })
  const spoken: string[] = []
  watch(state.announcement, value => value && spoken.push(value), { flush: 'sync' })
  return { ...state, source, spoken }
}

const toastsShown = () => toasts.value.map(toast => [toast.message, toast.type])

const pendingSwitch = async () => {
  const state = setup()
  state.choose('monthly')
  await nextTick()
  return state
}

const storedSwitch = async () => {
  localStorage.setItem(KEY, 'monthly')
  return setup()
}

describe('useSummaryInterval', () => {
  beforeEach(() => {
    localStorage.clear()
    toasts.value = []
  })

  it('opens Daily when nothing is stored', () => {
    const { interval, view } = setup()

    expect([interval.value, view.value]).toEqual(['daily', 'daily'])
  })

  it.each(['Monthly', '"monthly"', '', 'weekly'])('opens Daily for the stored value %j', value => {
    localStorage.setItem(KEY, value)

    expect(setup().interval.value).toBe('daily')
  })

  it('restores a stored Monthly silently', async () => {
    localStorage.setItem(KEY, 'monthly')
    const { view, source, spoken } = setup()
    const restored = view.value

    source.model.value = createMonthlyProfit()
    await nextTick()

    expect([restored, spoken]).toEqual(['monthly', []])
  })

  it('switches to cached months at once and announces the span', async () => {
    const { view, choose, spoken } = setup(createMonthlyProfit())

    choose('monthly')
    await nextTick()

    expect([view.value, spoken, localStorage.getItem(KEY)]).toEqual([
      'monthly',
      ['Monthly profit Jun – Dec 2025'],
      'monthly',
    ])
  })

  it('keeps Daily while the first Monthly request is pending', async () => {
    const { view, source, spoken } = await pendingSwitch()
    const pending = view.value

    source.model.value = createMonthlyProfit()
    await nextTick()

    expect([pending, view.value, spoken.length]).toEqual(['pending', 'monthly', 1])
  })

  it('announces once despite refreshed months', async () => {
    const { source, spoken } = await pendingSwitch()
    source.model.value = createMonthlyProfit()
    await nextTick()

    source.model.value = createMonthlyProfit()
    await nextTick()

    expect(spoken).toHaveLength(1)
  })

  it('announces when there is not enough history', async () => {
    const { source, spoken } = await pendingSwitch()

    source.model.value = null
    await nextTick()

    expect(spoken).toEqual([NOT_ENOUGH_HISTORY])
  })

  it.each([
    ['a stored', storedSwitch],
    ['a pressed', pendingSwitch],
  ])('returns %s Monthly to Daily with one toast when its first load fails', async (_, start) => {
    const { interval, source } = await start()

    source.error.value = new Error('down')
    await nextTick()

    expect([interval.value, localStorage.getItem(KEY), toastsShown()]).toEqual([
      'daily',
      'daily',
      [[FAILED, 'error']],
    ])
  })

  it('shows the toast again for a new failed attempt', async () => {
    const { choose, source } = await pendingSwitch()
    source.error.value = new Error('down')
    await nextTick()

    choose('monthly')
    source.error.value = null
    await nextTick()
    source.error.value = new Error('down')
    await nextTick()

    expect(toasts.value).toHaveLength(2)
  })

  it('ignores a failure after returning to Daily', async () => {
    const { interval, choose, source } = await pendingSwitch()
    choose('daily')

    source.error.value = new Error('down')
    await nextTick()

    expect([interval.value, toasts.value]).toEqual(['daily', []])
  })

  it('does not announce months that arrive after returning to Daily', async () => {
    const { choose, source, spoken } = await pendingSwitch()
    choose('daily')

    source.model.value = createMonthlyProfit()
    await nextTick()

    expect(spoken).toEqual([])
  })

  it('keeps Monthly when a refresh fails with months shown', async () => {
    localStorage.setItem(KEY, 'monthly')
    const { interval, source } = setup(createMonthlyProfit())

    source.error.value = new Error('down')
    await nextTick()

    expect([interval.value, toasts.value]).toEqual(['monthly', []])
  })

  it('cancels a pending switch when platforms change', async () => {
    const { view, source, spoken } = await pendingSwitch()
    source.platforms.value = ['IBKR']
    await nextTick()
    const afterChange = view.value

    source.model.value = createMonthlyProfit()
    await nextTick()

    expect([afterChange, spoken]).toEqual(['monthly', []])
  })

  it('ignores pressing the selected interval again', async () => {
    const { choose, spoken } = setup(createMonthlyProfit())

    choose('monthly')
    await nextTick()
    choose('monthly')
    await nextTick()

    expect(spoken).toEqual(['Monthly profit Jun – Dec 2025'])
  })

  it('keeps Monthly when pressed while the previous failure is still reported', async () => {
    const { view, choose, source } = setup(undefined, new Error('down'))
    choose('monthly')
    await nextTick()
    const pressed = view.value

    source.error.value = null
    await nextTick()
    source.model.value = createMonthlyProfit()
    await nextTick()

    expect([pressed, view.value, toasts.value]).toEqual(['pending', 'monthly', []])
  })

  it('leaves the monthly model unbuilt while Daily is shown', async () => {
    const tick = ref(0)
    let builds = 0
    const model = computed(() => {
      builds += 1
      return tick.value >= 0 ? createMonthlyProfit() : null
    })
    renderWithProviders({
      setup() {
        useSummaryInterval(useStoredInterval(), {
          model,
          error: ref(null),
          platforms: ref(['LHV']),
        })
        return {}
      },
      template: '<div></div>',
    })

    tick.value += 1
    await nextTick()

    expect(builds).toBe(0)
  })
})
