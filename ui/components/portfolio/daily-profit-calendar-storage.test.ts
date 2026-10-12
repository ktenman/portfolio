import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import type { CalendarHistory } from '../../composables/use-daily-profit-calendar'
import { toasts } from '../../composables/use-toast'
import {
  createCalendarRow as row,
  createCalendarRows,
  createPeriodRows,
} from '../../tests/fixtures'
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
    ['click', 'Sun 7 Dec 2025', '2025-12-07'],
    ['arrow key', 'Tue 9 Dec 2025', '2025-12-09'],
    ['Previous day', 'Tue 9 Dec 2025', '2025-12-09'],
    ['Next day', 'Mon 8 Dec 2025', '2025-12-08'],
    ['Best day', 'Tue 9 Dec 2025', '2025-12-09'],
    ['Worst day', 'Wed 3 Dec 2025', '2025-12-03'],
  ])('should restore a day picked with %s after remounting', async (action, name, date) => {
    const rows = [...createCalendarRows(), row('2025-12-10', 2560)]
    const wrapper = mountWith(rows)
    if (action === 'click') await pick(wrapper, name)
    else if (action === 'arrow key')
      await wrapper.find('.calendar-grid').trigger('keydown', { key: 'ArrowUp' })
    else if (['Best day', 'Worst day'].includes(action))
      await wrapper.findAll('.extreme-btn')[action === 'Best day' ? 0 : 1].trigger('click')
    else {
      if (action === 'Next day') await pick(wrapper, 'Sun 7 Dec 2025')
      await press(wrapper, action)
    }
    expect(saved()).toBe(date)
    wrapper.unmount()

    const restored = mountWith(rows)

    expect(selectedName(restored)).toContain(name)
    expect(datesOf(restored)).toBe('1 Dec – 10 Dec 2025')
    expect(announced(restored)).toBe('')
    expect(restored.emitted('loadHistory')).toBeUndefined()
  })

  it('should keep the stored day when another day is previewed', async () => {
    localStorage.setItem(STORAGE_KEY, '2025-12-29')
    const wrapper = mountWith()

    await wrapper
      .find('.calendar-grid [aria-label^="Tue 30 Dec 2025"]')
      .trigger('pointerenter', { pointerType: 'mouse' })

    expect(wrapper.find('.day-label').text()).toBe('Tue 30 Dec 2025')
    expect(saved()).toBe('2025-12-29')
    wrapper.unmount()
    expect(selectedName(mountWith())).toContain('Mon 29 Dec 2025')
  })

  it.each([
    ['2025-06-29', '30 Dec 2024 – 30 Jun 2025', 'Sun 29 Jun 2025'],
    ['2025-06-30', '30 Dec 2024 – 30 Jun 2025', 'Mon 30 Jun 2025'],
    ['2024-12-30', '28 Dec – 30 Dec 2024', 'Mon 30 Dec 2024'],
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

  it('should open the preceding period when a picked starting day is restored', async () => {
    const wrapper = mountWith()
    await pick(wrapper, 'Mon 30 Jun 2025')
    expect(selectedName(wrapper)).toContain('starting day')
    wrapper.unmount()

    const restored = mountWith()
    await nextTick()

    expect(datesOf(restored)).toBe('30 Dec 2024 – 30 Jun 2025')
    expect(selectedName(restored)).toBe('Mon 30 Jun 2025, +€600.00, +0.60%')
    expect(announced(restored)).toBe('')
  })

  it.each(['.calendar-body', '.compact-period'])(
    'should restore a period reached by stepping in %s',
    async selector => {
      const wrapper = mountWith()
      await wrapper.find(`${selector} button[aria-label="Earlier period"]`).trigger('click')
      expect(saved()).toBe('2025-06-30')
      wrapper.unmount()

      const restored = mountWith()
      await nextTick()

      expect(datesOf(restored)).toBe('30 Dec 2024 – 30 Jun 2025')
      expect(selectedName(restored)).toBe('Mon 30 Jun 2025, +€600.00, +0.60%')
    }
  )

  it('should select and save the best day when stepping forward to an earlier period', async () => {
    const wrapper = mountWith()
    await press(wrapper, 'Earlier period')
    await press(wrapper, 'Earlier period')

    await press(wrapper, 'Later period')

    expect(saved()).toBe('2025-06-30')
    expect(selectedName(wrapper)).toBe('Mon 30 Jun 2025, +€600.00, +0.60%')
  })

  it.each(['pick', 'Later period'])(
    'should clear storage on returning to the latest day with %s',
    async action => {
      const wrapper = mountWith()
      if (action === 'pick') {
        await pick(wrapper, 'Tue 30 Dec 2025')
        expect(saved()).toBe('2025-12-30')
        await pick(wrapper, 'Wed 31 Dec 2025')
      } else {
        await press(wrapper, 'Earlier period')
        expect(saved()).toBe('2025-06-30')
        await press(wrapper, action)
      }
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

  it('should restore a day in the latest period without requesting older rows', () => {
    localStorage.setItem(STORAGE_KEY, '2025-12-29')

    const wrapper = mountWith(sixMonthRows(), 'idle')

    expect(selectedName(wrapper)).toContain('Mon 29 Dec 2025')
    expect(wrapper.emitted('loadHistory')).toBeUndefined()
  })

  it.each(['invalid', '2025-02-30', '2025-2-28', '2025-12-01', '2026-01-01', '2025-12-31', ''])(
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

  it.each(['2024-12-01', '2025-06-01'])(
    'should discard an older day without a row after fetching history: %s',
    async date => {
      localStorage.setItem(STORAGE_KEY, date)
      const wrapper = mountWith(sixMonthRows(), 'idle')
      expect(wrapper.emitted('loadHistory')).toHaveLength(1)

      await wrapper.setProps({ rows: createPeriodRows(), history: 'loaded' })

      expect(saved()).toBeNull()
      expect(selectedName(wrapper)).toContain('Wed 31 Dec 2025')
      expect(datesOf(wrapper)).toBe('30 Jun – 31 Dec 2025')
      wrapper.unmount()
      expect(mountWith(sixMonthRows(), 'idle').emitted('loadHistory')).toBeUndefined()
    }
  )

  it('should discard the stored day and show the toast when older rows fail to load', async () => {
    localStorage.setItem(STORAGE_KEY, '2025-06-29')
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

  it('should restore across platform selections that still contain the picked day', async () => {
    const wrapper = mountWith()
    await press(wrapper, 'Earlier period')
    wrapper.unmount()

    const restored = mountWith(createPeriodRows().map(entry => ({ ...entry, totalValue: 5000 })))
    await nextTick()

    expect(datesOf(restored)).toBe('30 Dec 2024 – 30 Jun 2025')
    expect(selectedName(restored)).toBe('Mon 30 Jun 2025, +€600.00, +13.64%')
  })

  it('should leave the card and day in place after another tab picks an older day until remounting', async () => {
    const wrapper = mountWith()
    await nextTick()
    localStorage.setItem(STORAGE_KEY, '2024-12-29')
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: STORAGE_KEY,
        newValue: '2024-12-29',
        storageArea: localStorage,
      })
    )
    await nextTick()

    expect(selectedName(wrapper)).toContain('Wed 31 Dec 2025')
    expect(datesOf(wrapper)).toBe('30 Jun – 31 Dec 2025')
    wrapper.unmount()

    const restored = mountWith()
    await nextTick()

    expect(selectedName(restored)).toContain('Sun 29 Dec 2024')
    expect(datesOf(restored)).toBe('28 Dec – 30 Dec 2024')
  })
})
