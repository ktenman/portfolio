import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import type { CalendarHistory } from '../../composables/use-daily-profit-calendar'
import { toasts } from '../../composables/use-toast'
import { createCalendarRow as row, createPeriodRows } from '../../tests/fixtures'
import DailyProfitCalendar from './daily-profit-calendar.vue'

enableAutoUnmount(afterEach)

const STORAGE_KEY = 'portfolio_summary_calendar_day'
const sixMonthRows = () => createPeriodRows().slice(5)
const mountWith = (rows = createPeriodRows(), history: CalendarHistory = 'loaded') =>
  mount(DailyProfitCalendar, { props: { rows, history }, attachTo: document.body })

type Wrapper = ReturnType<typeof mountWith>

const datesOf = (wrapper: Wrapper) => wrapper.find('.calendar-body .period-dates').text()
const selectedName = (wrapper: Wrapper) => wrapper.find('.selected').attributes('aria-label')
const announced = (wrapper: Wrapper) => wrapper.find('p[role="status"]').text()
const saved = () => localStorage.getItem(STORAGE_KEY)
const press = (wrapper: Wrapper, name: string) =>
  wrapper.find(`.calendar-body button[aria-label="${name}"]`).trigger('click')
const pick = (wrapper: Wrapper, name: string) =>
  wrapper.find(`.calendar-grid [aria-label^="${name}"]`).trigger('click')

describe('DailyProfitCalendar storage', () => {
  beforeEach(() => {
    localStorage.clear()
    toasts.value = []
  })

  it.each([
    ['2025-06-29', '30 Dec 2024 – 30 Jun 2025', 'Sun 29 Jun 2025'],
    ['2025-06-30', '30 Dec 2024 – 30 Jun 2025', 'Mon 30 Jun 2025'],
    ['2024-12-28', '28 Dec – 30 Dec 2024', 'Sat 28 Dec 2024'],
  ])('should restore the period that counts %s', async (date, dates, name) => {
    localStorage.setItem(STORAGE_KEY, date)

    const wrapper = mountWith()
    await nextTick()

    expect(datesOf(wrapper)).toBe(dates)
    expect(selectedName(wrapper)).toContain(name)
    expect(announced(wrapper)).toBe('')
    expect(saved()).toBe(date)
  })

  it('should restore the period reached with Earlier and step on from it', async () => {
    const wrapper = mountWith()
    await press(wrapper, 'Earlier period')
    await press(wrapper, 'Earlier period')
    expect(saved()).toBe('2024-12-29')
    wrapper.unmount()

    const restored = mountWith()
    await nextTick()
    expect(selectedName(restored)).toBe('Sun 29 Dec 2024, +€300.00, +0.30%')
    await press(restored, 'Later period')

    expect(datesOf(restored)).toBe('30 Dec 2024 – 30 Jun 2025')
  })

  it.each([
    ['pick', pick, 'Tue 30 Dec 2025', '2025-12-30', 'Wed 31 Dec 2025'],
    ['Later period', press, 'Earlier period', '2025-06-30', 'Later period'],
  ])(
    'should clear storage on returning to the latest day with %s',
    async (_, act, away, date, home) => {
      const wrapper = mountWith()
      await act(wrapper, away)
      expect(saved()).toBe(date)
      await act(wrapper, home)
      expect(saved()).toBeNull()
      wrapper.unmount()

      const restored = mountWith([...createPeriodRows(), row('2026-01-01', 2700)])

      expect(selectedName(restored)).toContain('Thu 1 Jan 2026')
      expect(saved()).toBeNull()
    }
  )

  it('should wait for the initial rows before requesting older rows once', async () => {
    localStorage.setItem(STORAGE_KEY, '2024-12-29')
    const wrapper = mount(DailyProfitCalendar, { props: { rows: undefined, history: 'idle' } })
    expect(wrapper.emitted('loadHistory')).toBeUndefined()

    await wrapper.setProps({ rows: sixMonthRows() })
    expect(wrapper.emitted('loadHistory')).toHaveLength(1)
    expect(wrapper.attributes('aria-hidden')).toBe('true')
    expect(wrapper.find('.period-dates').exists()).toBe(false)
    await wrapper.setProps({ history: 'loading' })
    await wrapper.setProps({ rows: [...sixMonthRows()] })
    expect(wrapper.emitted('loadHistory')).toHaveLength(1)
    expect(wrapper.find('.period-dates').exists()).toBe(false)
    expect(saved()).toBe('2024-12-29')

    await wrapper.setProps({ rows: createPeriodRows(), history: 'loaded' })

    expect(datesOf(wrapper)).toBe('28 Dec – 30 Dec 2024')
    expect(selectedName(wrapper)).toBe('Sun 29 Dec 2024, +€300.00, +0.30%')
    expect(announced(wrapper)).toBe('')
    expect(wrapper.find('.skeleton').exists()).toBe(false)
  })

  it('should restore a picked day of the latest period without requesting older rows', async () => {
    const wrapper = mountWith(sixMonthRows(), 'idle')
    await pick(wrapper, 'Mon 29 Dec 2025')
    wrapper.unmount()

    const restored = mountWith(sixMonthRows(), 'idle')

    expect(selectedName(restored)).toContain('Mon 29 Dec 2025')
    expect(restored.emitted('loadHistory')).toBeUndefined()
  })

  it.each(['2025-02-30', '2025-12-01'])(
    'should discard %j without requesting older rows',
    async date => {
      localStorage.setItem(STORAGE_KEY, date)

      const wrapper = mountWith(sixMonthRows(), 'idle')
      await nextTick()

      expect(saved()).toBeNull()
      expect(datesOf(wrapper)).toBe('30 Jun – 31 Dec 2025')
      expect(selectedName(wrapper)).toContain('Wed 31 Dec 2025')
      expect(wrapper.emitted('loadHistory')).toBeUndefined()
    }
  )

  it('should discard an older day without a row after fetching history', async () => {
    localStorage.setItem(STORAGE_KEY, '2024-12-01')
    const wrapper = mountWith(sixMonthRows(), 'idle')
    expect(wrapper.emitted('loadHistory')).toHaveLength(1)

    await wrapper.setProps({ rows: createPeriodRows(), history: 'loaded' })

    expect(saved()).toBeNull()
    expect(selectedName(wrapper)).toContain('Wed 31 Dec 2025')
    expect(datesOf(wrapper)).toBe('30 Jun – 31 Dec 2025')
    wrapper.unmount()
    expect(mountWith(sixMonthRows(), 'idle').emitted('loadHistory')).toBeUndefined()
  })

  it('should discard the stored day and show the toast when older rows fail to load', async () => {
    localStorage.setItem(STORAGE_KEY, '2025-06-30')
    const wrapper = mountWith(sixMonthRows(), 'idle')

    await wrapper.setProps({ history: 'loading' })
    await wrapper.setProps({ history: 'idle' })

    expect(saved()).toBeNull()
    expect(selectedName(wrapper)).toContain('Wed 31 Dec 2025')
    expect(announced(wrapper)).toBe('')
    expect(toasts.value.map(({ message, type }) => [message, type])).toEqual([
      ['Could not load earlier days. Press the arrow to try again.', 'error'],
    ])
    wrapper.unmount()
    expect(mountWith(sixMonthRows(), 'idle').emitted('loadHistory')).toBeUndefined()
  })

  it('should warn that the calendar could not load when a failed request follows a stored day', async () => {
    localStorage.setItem(STORAGE_KEY, '2025-12-08')
    const wrapper = mount(DailyProfitCalendar, {
      props: { rows: undefined, error: 'Võrk on maas' },
    })

    await wrapper.setProps({ rows: [row('2025-12-09', 2580)] })

    expect([wrapper.attributes('role'), wrapper.text()]).toEqual([
      'alert',
      'Could not load the daily profit calendar: Võrk on maas',
    ])
  })
})
