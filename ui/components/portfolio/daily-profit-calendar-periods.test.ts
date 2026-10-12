import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import DailyProfitCalendar from './daily-profit-calendar.vue'
import LoadingVeil from '../shared/loading-veil.vue'
import type { CalendarHistory } from '../../composables/use-daily-profit-calendar'
import { toasts } from '../../composables/use-toast'
import { createCalendarRow as row, createPeriodRows } from '../../tests/fixtures'

enableAutoUnmount(afterEach)

const sixMonthRows = () => createPeriodRows().slice(5)

const mountWith = (rows = createPeriodRows(), history?: CalendarHistory) =>
  mount(DailyProfitCalendar, { props: { rows, history }, attachTo: document.body })

type Wrapper = ReturnType<typeof mountWith>

const wide = (wrapper: Wrapper) => wrapper.find('.calendar-body')
const arrow = (wrapper: Wrapper, name: string) => wide(wrapper).find(`button[aria-label="${name}"]`)
const press = (wrapper: Wrapper, name: string) => arrow(wrapper, name).trigger('click')
const datesOf = (wrapper: Wrapper) => wide(wrapper).find('.period-dates').text()
const selectedName = (wrapper: Wrapper) => wrapper.find('.selected').attributes('aria-label')
const announced = (wrapper: Wrapper) => wrapper.find('p[role="status"]').text()
const stateOf = (wrapper: Wrapper) => [datesOf(wrapper), selectedName(wrapper), announced(wrapper)]
const veilsOf = (wrapper: Wrapper) => wrapper.findAllComponents(LoadingVeil).length

describe('DailyProfitCalendar periods', () => {
  beforeEach(() => {
    toasts.value = []
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('steps', () => {
    it("should head both layouts with the module's name and the dates of the latest period", () => {
      const wrapper = mountWith()

      expect([
        wrapper.find('.calendar-heading').text(),
        wrapper.find('.compact-heading').text(),
        wrapper.findAll('.period-dates').map(dates => dates.text()),
      ]).toEqual(['Daily profit', 'Daily profit', ['30 Jun – 31 Dec 2025', '30 Jun – 31 Dec 2025']])
    })

    it('should open the earlier period, select its best day and announce its dates', async () => {
      const wrapper = mountWith()

      await press(wrapper, 'Earlier period')

      expect(stateOf(wrapper)).toEqual([
        '30 Dec 2024 – 30 Jun 2025',
        'Mon 30 Jun 2025, +€600.00, +0.60%',
        'Daily profit 30 Dec 2024 – 30 Jun 2025',
      ])
    })

    it('should select the last day of a period in which no single day has a gain', async () => {
      const wrapper = mountWith([
        row('2024-12-28', 400),
        row('2024-12-29', 300),
        row('2024-12-30', 100),
        ...createPeriodRows().slice(3),
      ])

      await press(wrapper, 'Earlier period')
      await press(wrapper, 'Earlier period')

      expect(selectedName(wrapper)).toBe('Mon 30 Dec 2024, −€200.00, −0.20%')
    })

    it('should clear the previewed day on a step', async () => {
      const wrapper = mountWith()
      await press(wrapper, 'Earlier period')
      await wide(wrapper)
        .find('[aria-label="Mon 30 Jun 2025, +€600.00, +0.60%"]')
        .trigger('pointerenter', { pointerType: 'mouse' })

      await press(wrapper, 'Later period')

      expect(wrapper.find('.day-label').text()).toBe('Tue 30 Dec 2025')
    })

    it('should return to the latest period and its best day after a step back and a step forward', async () => {
      const wrapper = mountWith()

      await press(wrapper, 'Earlier period')
      await press(wrapper, 'Later period')

      expect(stateOf(wrapper)).toEqual([
        '30 Jun – 31 Dec 2025',
        'Tue 30 Dec 2025, +€600.00, +0.60%',
        'Daily profit 30 Jun – 31 Dec 2025',
      ])
    })

    it('should disable only the later arrow on the latest period', () => {
      const wrapper = mountWith()

      expect(
        ['Earlier period', 'Later period'].map(name => [
          arrow(wrapper, name).attributes('aria-disabled'),
          arrow(wrapper, name).attributes('disabled'),
        ])
      ).toEqual([
        [undefined, undefined],
        ['true', undefined],
      ])
    })

    it('should disable only the earlier arrow on the oldest period', async () => {
      const wrapper = mountWith()

      await press(wrapper, 'Earlier period')
      await press(wrapper, 'Earlier period')

      expect(
        ['Earlier period', 'Later period'].map(name => [
          arrow(wrapper, name).attributes('aria-disabled'),
          arrow(wrapper, name).attributes('disabled'),
        ])
      ).toEqual([
        ['true', undefined],
        [undefined, undefined],
      ])
    })

    it('should do nothing and keep the focus when a disabled arrow is pressed', async () => {
      const wrapper = mountWith()
      ;(arrow(wrapper, 'Later period').element as HTMLElement).focus()

      await press(wrapper, 'Later period')

      expect([
        datesOf(wrapper),
        document.activeElement === arrow(wrapper, 'Later period').element,
        announced(wrapper),
      ]).toEqual(['30 Jun – 31 Dec 2025', true, ''])
    })

    it('should keep the focus on the arrow that reached the oldest period', async () => {
      const wrapper = mountWith()
      ;(arrow(wrapper, 'Earlier period').element as HTMLElement).focus()

      await press(wrapper, 'Earlier period')
      await press(wrapper, 'Earlier period')

      expect([
        datesOf(wrapper),
        document.activeElement === arrow(wrapper, 'Earlier period').element,
      ]).toEqual(['28 Dec – 30 Dec 2024', true])
    })

    it('should stop the arrow keys of the grid at the edge of the period', async () => {
      const wrapper = mountWith()
      await press(wrapper, 'Earlier period')
      await press(wrapper, 'Earlier period')
      const grid = wrapper.find('.calendar-body .calendar-grid')

      await grid.trigger('keydown', { key: 'ArrowDown' })
      await grid.trigger('keydown', { key: 'ArrowDown' })

      expect(selectedName(wrapper)).toBe('Mon 30 Dec 2024, −€100.00, −0.10%')
    })

    it('should keep the selected day and stay silent when the rows refresh without a step', async () => {
      const wrapper = mountWith()
      await press(wrapper, 'Earlier period')

      await wrapper.setProps({ rows: [...createPeriodRows().slice(0, 8), row('2025-12-31', 2550)] })

      expect(stateOf(wrapper)).toEqual([
        '30 Dec 2024 – 30 Jun 2025',
        'Mon 30 Jun 2025, +€600.00, +0.60%',
        '',
      ])
    })

    it('should move an earlier period forward when the latest day moves on', async () => {
      const wrapper = mountWith()
      await press(wrapper, 'Earlier period')

      await wrapper.setProps({ rows: [...createPeriodRows(), row('2026-01-01', 2700)] })

      expect(stateOf(wrapper)).toEqual([
        '29 Jun – 29 Dec 2025',
        'Mon 30 Jun 2025, +€600.00, +0.60%',
        '',
      ])
    })

    it('should show the oldest period when the rows no longer reach the period on screen', async () => {
      const wrapper = mountWith()
      await press(wrapper, 'Earlier period')
      await press(wrapper, 'Earlier period')

      await wrapper.setProps({ rows: createPeriodRows().slice(3) })

      expect([
        datesOf(wrapper),
        arrow(wrapper, 'Earlier period').attributes('aria-disabled'),
      ]).toEqual(['28 Jun – 30 Jun 2025', 'true'])
    })

    it('should step from the row under the heading in the phone layout', async () => {
      const wrapper = mountWith()

      await wrapper.find('.compact-period button[aria-label="Earlier period"]').trigger('click')

      expect(wrapper.find('.compact-period .period-dates').text()).toBe('30 Dec 2024 – 30 Jun 2025')
    })
  })

  describe('older rows', () => {
    const failToLoad = async () => {
      vi.useFakeTimers()
      const wrapper = mountWith(sixMonthRows(), 'idle')
      await press(wrapper, 'Earlier period')
      await wrapper.setProps({ history: 'loading' })
      await wrapper.setProps({ history: 'idle' })
      return wrapper
    }

    it('should ask for the older rows on the first press and wait on the latest period', async () => {
      const wrapper = mountWith(createPeriodRows(), 'idle')

      await press(wrapper, 'Earlier period')

      expect([
        wrapper.emitted('loadHistory'),
        datesOf(wrapper),
        arrow(wrapper, 'Earlier period').attributes('aria-busy'),
        veilsOf(wrapper),
      ]).toEqual([[[]], '30 Jun – 31 Dec 2025', 'true', 2])
    })

    it('should ignore further presses while the older rows load', async () => {
      const wrapper = mountWith(createPeriodRows(), 'idle')

      await press(wrapper, 'Earlier period')
      await press(wrapper, 'Earlier period')
      await press(wrapper, 'Earlier period')

      expect(wrapper.emitted('loadHistory')).toHaveLength(1)
    })

    it('should make the waiting step once the older rows are in', async () => {
      const wrapper = mountWith(sixMonthRows(), 'idle')
      await press(wrapper, 'Earlier period')

      await wrapper.setProps({ rows: createPeriodRows(), history: 'loaded' })

      expect([
        ...stateOf(wrapper),
        arrow(wrapper, 'Earlier period').attributes('aria-busy'),
        veilsOf(wrapper),
      ]).toEqual([
        '30 Dec 2024 – 30 Jun 2025',
        'Mon 30 Jun 2025, +€600.00, +0.60%',
        'Daily profit 30 Dec 2024 – 30 Jun 2025',
        undefined,
        0,
      ])
    })

    it('should stay and disable the earlier arrow when the answer holds no earlier day', async () => {
      const wrapper = mountWith(sixMonthRows(), 'idle')
      await press(wrapper, 'Earlier period')

      await wrapper.setProps({ history: 'loaded' })

      expect([
        ...stateOf(wrapper),
        arrow(wrapper, 'Earlier period').attributes('aria-disabled'),
        arrow(wrapper, 'Earlier period').attributes('aria-busy'),
      ]).toEqual([
        '30 Jun – 31 Dec 2025',
        'Tue 30 Dec 2025, +€600.00, +0.60%',
        '',
        'true',
        undefined,
      ])
    })

    it('should show the error toast and stay when the older rows fail to load', async () => {
      const wrapper = await failToLoad()

      expect([
        toasts.value.map(({ message, type }) => [message, type]),
        datesOf(wrapper),
        veilsOf(wrapper),
      ]).toEqual([
        [['Could not load earlier days. Press the arrow to try again.', 'error']],
        '30 Jun – 31 Dec 2025',
        0,
      ])
    })

    it('should take the next press after a failed load', async () => {
      const wrapper = await failToLoad()

      await press(wrapper, 'Earlier period')

      expect(wrapper.emitted('loadHistory')).toHaveLength(2)
    })

    it('should show no toast when the older rows go idle with no press waiting', async () => {
      const wrapper = mountWith(createPeriodRows(), 'loaded')

      await wrapper.setProps({ history: 'idle' })

      expect(toasts.value).toEqual([])
    })

    it('should ask for the older rows on every step back, also when it holds them', async () => {
      const wrapper = mountWith()

      await press(wrapper, 'Earlier period')
      await press(wrapper, 'Earlier period')

      expect(wrapper.emitted('loadHistory')).toHaveLength(2)
    })
  })
})
