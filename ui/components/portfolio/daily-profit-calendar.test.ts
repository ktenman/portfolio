import { describe, it, expect, vi, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import DailyProfitCalendar from './daily-profit-calendar.vue'
import DailyProfitCompact from './daily-profit-compact.vue'
import { createCalendarRow, createCalendarRows } from '../../tests/fixtures'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'

enableAutoUnmount(afterEach)

const mountWith = (rows: PortfolioSummaryDto[] | undefined, error?: string) =>
  mount(DailyProfitCalendar, { props: { rows, error }, attachTo: document.body })

const createWrapper = (rows = createCalendarRows()) => mountWith(rows)

type Wrapper = ReturnType<typeof createWrapper>

const DAY = { dec2: 1, dec4: 3, dec6: 5, dec7: 6, dec8: 7, dec9: 8 }

const clickDay = (wrapper: Wrapper, index: number) =>
  wrapper.findAll('.calendar-grid [role="img"]')[index].trigger('click')

const selectedName = (wrapper: Wrapper) => wrapper.find('.selected').attributes('aria-label')
const announced = (wrapper: Wrapper) => wrapper.find('[role="status"]').text()
const dayButton = (wrapper: Wrapper, name: string) => wrapper.find(`button[aria-label="${name}"]`)

describe('DailyProfitCalendar', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('should select the best day when the rows arrive, without announcing it', () => {
    const wrapper = createWrapper()

    expect([selectedName(wrapper), wrapper.find('.day-label').text(), announced(wrapper)]).toEqual([
      'Tue 9 Dec 2025, +€700.00, +0.70%',
      'Tue 9 Dec 2025',
      '',
    ])
  })

  it('should head the module with the starting day', () => {
    const wrapper = createWrapper()

    expect([
      wrapper.find('.calendar-heading').text(),
      wrapper.find('.calendar-sub').text(),
    ]).toEqual([
      'Every day since 1 Dec',
      'Change in total profit per day. Money you add is not counted as a gain.',
    ])
  })

  it('should select the latest day when no single day has a gain', () => {
    const wrapper = createWrapper([
      createCalendarRow('2025-12-01', 1000),
      createCalendarRow('2025-12-03', 1300),
    ])

    expect([wrapper.find('.day-label').text(), wrapper.find('.day-detail').text()]).toEqual([
      'Wed 3 Dec 2025',
      'Closed at €100,000.00 · XIRR 20.69%',
    ])
    expect(wrapper.findAll('.extreme-btn')).toHaveLength(0)
    expect(wrapper.findAll('.compact-row')).toHaveLength(2)
  })

  it('should select a clicked day and announce its name', async () => {
    const wrapper = createWrapper()

    await clickDay(wrapper, DAY.dec7)

    expect([selectedName(wrapper), wrapper.find('.day-label').text(), announced(wrapper)]).toEqual([
      'Sun 7 Dec 2025, +€60.00, +0.06%',
      'Sun 7 Dec 2025',
      'Sun 7 Dec 2025, +€60.00, +0.06%',
    ])
  })

  it('should announce the note after the name', async () => {
    const wrapper = createWrapper()

    await clickDay(wrapper, DAY.dec6)

    expect(announced(wrapper)).toBe(
      'Sat 6 Dec 2025, +€1,200.00, +1.21%. Covers 2 days, no daily summary for 5 Dec'
    )
  })

  it('should go on with the arrow keys from a clicked day and skip the missing day', async () => {
    const wrapper = createWrapper()

    await clickDay(wrapper, DAY.dec4)
    await wrapper.find('.calendar-grid').trigger('keydown', { key: 'ArrowDown' })

    expect([selectedName(wrapper), announced(wrapper)]).toEqual([
      'Sat 6 Dec 2025, +€1,200.00, +1.21%',
      'Sat 6 Dec 2025, +€1,200.00, +1.21%. Covers 2 days, no daily summary for 5 Dec',
    ])
  })

  it('should move with the day buttons, skip the missing day and keep the focus on the button', async () => {
    const wrapper = createWrapper()
    await clickDay(wrapper, DAY.dec6)
    const previous = dayButton(wrapper, 'Previous day')
    ;(previous.element as HTMLElement).focus()

    await previous.trigger('click')

    expect([selectedName(wrapper), announced(wrapper)]).toEqual([
      'Thu 4 Dec 2025, +€20.00, +0.02%',
      'Thu 4 Dec 2025, +€20.00, +0.02%',
    ])
    expect(document.activeElement).toBe(previous.element)
  })

  it('should stop at the latest day with the Next day button still focused', async () => {
    const wrapper = createWrapper()
    await clickDay(wrapper, DAY.dec8)
    const next = dayButton(wrapper, 'Next day')
    ;(next.element as HTMLElement).focus()

    await next.trigger('click')
    await next.trigger('click')

    expect([selectedName(wrapper), next.attributes('aria-disabled')]).toEqual([
      'Tue 9 Dec 2025, +€700.00, +0.70%',
      'true',
    ])
    expect(document.activeElement).toBe(next.element)
  })

  it('should select and announce the day of the Worst day and the Best day button', async () => {
    const wrapper = createWrapper()
    const [best, worst] = wrapper.findAll('.extreme-btn')

    await worst.trigger('click')
    const afterWorst = [selectedName(wrapper), announced(wrapper)]
    await best.trigger('click')

    expect([afterWorst, [selectedName(wrapper), announced(wrapper)]]).toEqual([
      ['Wed 3 Dec 2025, −€600.00, −0.60%', 'Wed 3 Dec 2025, −€600.00, −0.60%'],
      ['Tue 9 Dec 2025, +€700.00, +0.70%', 'Tue 9 Dec 2025, +€700.00, +0.70%'],
    ])
  })

  it('should keep the selected day when a refresh makes another day the best', async () => {
    const wrapper = createWrapper(createCalendarRows().slice(0, 7))
    const before = selectedName(wrapper)

    await wrapper.setProps({ rows: createCalendarRows() })

    expect([before, selectedName(wrapper), announced(wrapper)]).toEqual([
      'Tue 2 Dec 2025, +€300.00, +0.30%',
      'Tue 2 Dec 2025, +€300.00, +0.30%',
      '',
    ])
    expect(wrapper.find('.extreme-btn').text()).toContain('9 Dec')
    expect(wrapper.findAll('.compact-row')[2].text()).toContain('Tue 9 Dec')
  })

  it('should announce a day only when the date changes, not when its numbers refresh', async () => {
    const wrapper = createWrapper()
    await clickDay(wrapper, DAY.dec8)
    await clickDay(wrapper, DAY.dec9)

    await wrapper.setProps({
      rows: [...createCalendarRows().slice(0, 7), createCalendarRow('2025-12-09', 2630)],
    })
    await clickDay(wrapper, DAY.dec9)

    expect([selectedName(wrapper), announced(wrapper)]).toEqual([
      'Tue 9 Dec 2025, +€750.00, +0.76%',
      '',
    ])
  })

  it('should select again and empty the announcement when the selected day loses its row', async () => {
    const wrapper = createWrapper()
    await clickDay(wrapper, DAY.dec4)
    const rows = createCalendarRows()

    await wrapper.setProps({ rows: [...rows.slice(0, 3), ...rows.slice(4)] })

    expect([selectedName(wrapper), announced(wrapper)]).toEqual([
      'Tue 9 Dec 2025, +€700.00, +0.70%',
      '',
    ])
  })

  it('should select again when the selected day falls before the starting day', async () => {
    const wrapper = createWrapper()
    await clickDay(wrapper, DAY.dec2)

    await wrapper.setProps({ rows: createCalendarRows().slice(2) })

    expect(selectedName(wrapper)).toBe('Tue 9 Dec 2025, +€700.00, +0.70%')
  })

  it('should read Now at for the latest day while it is today', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2025, 11, 9, 12))

    const wrapper = createWrapper()

    expect(wrapper.find('.day-detail').text()).toBe(
      'Best of 6 single days · now at €100,000.00 · XIRR 20.69%'
    )
  })

  it('should read today again when the rows change after midnight', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2025, 11, 9, 12))
    const wrapper = createWrapper()
    vi.setSystemTime(new Date(2025, 11, 10, 12))

    await wrapper.setProps({ rows: createCalendarRows() })

    expect(wrapper.find('.day-detail').text()).toBe(
      'Best of 6 single days · closed at €100,000.00 · XIRR 20.69%'
    )
  })

  it('should switch between the full and the compact version at md with utilities', () => {
    const wrapper = createWrapper()

    expect(wrapper.classes()).toEqual(expect.arrayContaining(['card-shell', 'p-0', 'md:p-6']))
    expect(wrapper.find('.calendar-body').classes()).toEqual(
      expect.arrayContaining(['hidden', 'md:flex'])
    )
    expect(wrapper.findComponent(DailyProfitCompact).classes()).toContain('md:hidden')
  })

  it('should show a placeholder that assistive technology skips until the rows arrive', () => {
    const wrapper = mountWith(undefined)

    expect(wrapper.attributes('aria-hidden')).toBe('true')
    expect(wrapper.findAll('.skeleton').length).toBeGreaterThan(0)
    expect(wrapper.find('.calendar-grid').exists()).toBe(false)
  })

  it.each([
    ['no row', []],
    ['one row', [createCalendarRow('2025-12-09', 2580)]],
  ])('should show nothing with %s in the period', (_, rows) => {
    const wrapper = createWrapper(rows)

    expect(wrapper.find('.daily-profit-calendar').exists()).toBe(false)
  })

  it('should warn that the calendar could not load when a failed request leaves one row', () => {
    const wrapper = mountWith([createCalendarRow('2025-12-09', 2580)], 'Võrk on maas')

    expect([wrapper.attributes('role'), wrapper.text()]).toEqual([
      'alert',
      'Could not load the daily profit calendar: Võrk on maas',
    ])
  })

  it('should keep the calendar and show no warning when a request fails with rows held', () => {
    const wrapper = mountWith(createCalendarRows(), 'Võrk on maas')

    expect([
      wrapper.find('.calendar-grid').exists(),
      wrapper.find('[role="alert"]').exists(),
    ]).toEqual([true, false])
  })

  it('should appear with the best day selected when rows follow a failed request', async () => {
    const wrapper = createWrapper([createCalendarRow('2025-12-09', 2580)])

    await wrapper.setProps({ rows: createCalendarRows() })

    expect([selectedName(wrapper), announced(wrapper)]).toEqual([
      'Tue 9 Dec 2025, +€700.00, +0.70%',
      '',
    ])
  })
})
