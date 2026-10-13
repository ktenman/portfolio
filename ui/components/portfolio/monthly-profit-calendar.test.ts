import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import type { MonthlyProfit, ProfitMonth, ProfitMonthSlot } from '../../services/monthly-profit'
import { createPortfolioSummaryDto } from '../../tests/fixtures'
import MonthlyProfitCalendar from './monthly-profit-calendar.vue'

enableAutoUnmount(afterEach)

const firstMonth = (): ProfitMonth => ({
  key: '2025-06',
  opening: createPortfolioSummaryDto({ date: '2025-06-27' }),
  closing: createPortfolioSummaryDto({ date: '2025-06-30', totalValue: 10100 }),
  rawChange: 100,
  change: 100,
  percent: 1,
  factor: 1.01,
  step: 'gain-2',
  eligible: true,
  coverage: ['from 27 Jun'],
  name: 'June 2025, +€100.00, +1.0%, from 27 Jun',
  rank: 'Best of 3 months',
  closingLabel: 'Closed at €10,100.00 · as of 30 Jun 2025',
  isCurrent: false,
})

const july = (): ProfitMonth => ({
  ...firstMonth(),
  key: '2025-07',
  opening: createPortfolioSummaryDto({ date: '2025-06-30', totalValue: 10100 }),
  closing: createPortfolioSummaryDto({ date: '2025-07-31', totalValue: 10050 }),
  rawChange: -50,
  change: -50,
  percent: -0.5,
  factor: 0.995,
  step: 'loss-1',
  coverage: [],
  name: 'July 2025, −€50.00, −0.5%',
  rank: 'Worst of 3 months',
  closingLabel: 'Closed at €10,050.00 · as of 31 Jul 2025',
})

const september = (): ProfitMonth => ({
  ...firstMonth(),
  key: '2025-09',
  opening: createPortfolioSummaryDto({ date: '2025-07-31', totalValue: 10050 }),
  closing: createPortfolioSummaryDto({ date: '2025-09-12', totalValue: 10110 }),
  rawChange: 60,
  change: 60,
  percent: -3,
  factor: 0.97,
  step: 'loss-3',
  eligible: false,
  coverage: ['31 Jul 2025 – 12 Sep 2025', 'Covers 2 months', 'to 12 Sep'],
  name: 'September 2025, +€60.00, −3.0%, 31 Jul 2025 – 12 Sep 2025, Covers 2 months, to 12 Sep',
  rank: '',
  closingLabel: 'Now at €10,110.00',
  isCurrent: true,
})

const calendarWith = (months = [firstMonth(), july(), september()]): MonthlyProfit => ({
  months,
  years: Array.from(
    {
      length:
        Number(months[months.length - 1].key.slice(0, 4)) - Number(months[0].key.slice(0, 4)) + 1,
    },
    (_, offset) => {
      const year = Number(months[months.length - 1].key.slice(0, 4)) - offset
      return {
        year,
        slots: Array.from({ length: 12 }, (_, index): ProfitMonthSlot => {
          const key = `${year}-${String(index + 1).padStart(2, '0')}`
          const month = months.find(entry => entry.key === key)
          if (month) return { key, kind: 'observed', month }
          if (key >= months[0].key && key <= months[months.length - 1].key) {
            const names = [
              'January',
              'February',
              'March',
              'April',
              'May',
              'June',
              'July',
              'August',
              'September',
              'October',
              'November',
              'December',
            ]
            return { key, kind: 'missing', name: `${names[index]} ${year}, no daily summary` }
          }
          return { key, kind: 'empty', name: '' }
        }),
        change: 110,
        percent: -2.5,
        coverage: ['from 27 Jun', 'YTD to 12 Sep'],
        name: `${year}, +€110.00, −2.5%, from 27 Jun, YTD to 12 Sep`,
      }
    }
  ),
  span: 'Jun – Sep 2025',
  upMonths: 1,
  downMonths: 1,
  flatMonths: 0,
  averageMonth: 25,
  best: firstMonth(),
  worst: july(),
  excludedMonths: 1,
})

const createWrapper = (calendar: MonthlyProfit | null = calendarWith()) =>
  mount(MonthlyProfitCalendar, { props: { calendar }, attachTo: document.body })

type Wrapper = ReturnType<typeof createWrapper>

const monthCell = (wrapper: Wrapper, key: string) =>
  wrapper.find(`.monthly-profit-grid [data-month="${key}"]`)

const selectedKey = (wrapper: Wrapper) =>
  wrapper.find('.monthly-profit-grid [aria-current="true"]').attributes('data-month')

const announced = (wrapper: Wrapper) => wrapper.find('[role="status"]').text()

const pressKey = (wrapper: Wrapper, init: KeyboardEventInit) => {
  const event = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init })
  wrapper.find('.monthly-profit-grid').element.dispatchEvent(event)
  return event.defaultPrevented
}

describe('MonthlyProfitCalendar', () => {
  it('should explain insufficient history when no monthly result exists', () => {
    const wrapper = createWrapper(null)

    expect(wrapper.text()).toBe('Not enough daily summary history to calculate monthly changes.')
    expect(wrapper.find('[role="application"]').exists()).toBe(false)
  })

  it('should select the latest observed month without an initial announcement', () => {
    const wrapper = createWrapper()

    expect([selectedKey(wrapper), announced(wrapper)]).toEqual(['2025-09', ''])
  })

  it('should commit a clicked month with one complete live description', async () => {
    const wrapper = createWrapper()

    await monthCell(wrapper, '2025-06').trigger('click')

    expect([selectedKey(wrapper), announced(wrapper)]).toEqual([
      '2025-06',
      'June 2025, +€100.00, +1.0%, from 27 Jun',
    ])
    expect(wrapper.findAll('[role="status"]')).toHaveLength(1)
    expect(document.activeElement).toBe(wrapper.find('.monthly-profit-grid').element)
  })

  it('should keep one grid Tab stop described by the selected month', () => {
    const wrapper = createWrapper()
    const grid = wrapper.find('.monthly-profit-grid')
    const selected = grid.find('[aria-current="true"]')

    expect(grid.attributes()).toMatchObject({
      role: 'application',
      'aria-label': 'Monthly profit calendar',
      tabindex: '0',
      'aria-describedby': selected.attributes('id'),
    })
    expect(grid.findAll('[tabindex]')).toHaveLength(0)
    expect(grid.findAll('[id]')).toHaveLength(1)
    expect(selected.attributes('aria-label')).toBe(
      'September 2025, +€60.00, −3.0%, 31 Jul 2025 – 12 Sep 2025, Covers 2 months, to 12 Sep'
    )
  })

  it('should preview a mouse month without selecting or announcing it', async () => {
    const wrapper = createWrapper()

    await monthCell(wrapper, '2025-06').trigger('pointerenter', { pointerType: 'mouse' })

    expect([
      wrapper.find('.monthly-profit-panel').attributes('data-month'),
      selectedKey(wrapper),
      announced(wrapper),
    ]).toEqual(['2025-06', '2025-09', ''])
    expect(monthCell(wrapper, '2025-06').classes()).toContain('previewed')
  })

  it('should restore the selected readout after the pointer leaves the grid', async () => {
    const wrapper = createWrapper()
    await monthCell(wrapper, '2025-06').trigger('pointerenter', { pointerType: 'mouse' })

    await wrapper.find('.monthly-profit-grid').trigger('pointerleave')

    expect(wrapper.find('.monthly-profit-panel').attributes('data-month')).toBe('2025-09')
    expect(wrapper.findAll('.previewed')).toHaveLength(0)
  })

  it('should ignore touch hover', async () => {
    const wrapper = createWrapper()

    await monthCell(wrapper, '2025-06').trigger('pointerenter', { pointerType: 'touch' })

    expect([
      wrapper.find('.monthly-profit-panel').attributes('data-month'),
      selectedKey(wrapper),
      announced(wrapper),
    ]).toEqual(['2025-09', '2025-09', ''])
  })

  it('should name missing slots without making them selectable', async () => {
    const wrapper = createWrapper()
    const missing = monthCell(wrapper, '2025-08')

    await missing.trigger('click')
    await missing.trigger('pointerenter', { pointerType: 'mouse' })

    expect(missing.attributes()).toMatchObject({
      role: 'img',
      'aria-label': 'August 2025, no daily summary',
    })
    expect([selectedKey(wrapper), announced(wrapper)]).toEqual(['2025-09', ''])
    expect(wrapper.findAll('.previewed')).toHaveLength(0)
    expect(missing.classes()).not.toContain('selectable')
  })

  it('should keep positions outside observed history empty and unnamed', async () => {
    const wrapper = createWrapper()
    const empty = monthCell(wrapper, '2025-01')

    await empty.trigger('click')

    expect([empty.attributes('aria-hidden'), empty.attributes('role'), empty.text()]).toEqual([
      'true',
      undefined,
      '',
    ])
    expect(selectedKey(wrapper)).toBe('2025-09')
  })

  it('should retain the selected key when refreshed months arrive', async () => {
    const wrapper = createWrapper()
    await monthCell(wrapper, '2025-06').trigger('click')

    await wrapper.setProps({
      calendar: calendarWith([
        firstMonth(),
        july(),
        september(),
        {
          ...september(),
          key: '2025-10',
        },
      ]),
    })

    expect([selectedKey(wrapper), announced(wrapper)]).toEqual(['2025-06', ''])
  })

  it('should return to the latest observed month when selection disappears', async () => {
    const wrapper = createWrapper()
    await monthCell(wrapper, '2025-06').trigger('click')

    await wrapper.setProps({ calendar: calendarWith([july(), september()]) })

    expect([selectedKey(wrapper), announced(wrapper)]).toEqual(['2025-09', ''])
  })

  it.each([
    ['2025-07', 'ArrowRight', '2025-09'],
    ['2025-09', 'ArrowLeft', '2025-07'],
    ['2025-06', 'ArrowRight', '2025-07'],
  ])('should navigate from %s with %s to %s through observed months', async (from, key, to) => {
    const wrapper = createWrapper()
    await monthCell(wrapper, from).trigger('click')

    const cancelled = pressKey(wrapper, { key })
    await wrapper.vm.$nextTick()

    expect([selectedKey(wrapper), cancelled]).toEqual([to, true])
  })

  it.each([
    ['2025-06', 'ArrowLeft'],
    ['2025-09', 'ArrowRight'],
    ['2025-09', 'ArrowUp'],
    ['2025-06', 'ArrowDown'],
  ])('should stay at the boundary on %s with %s without scrolling the page', async (from, key) => {
    const wrapper = createWrapper()
    await monthCell(wrapper, from).trigger('click')
    const before = announced(wrapper)

    const cancelled = pressKey(wrapper, { key })
    await wrapper.vm.$nextTick()

    expect([selectedKey(wrapper), announced(wrapper), cancelled]).toEqual([from, before, true])
  })

  it.each([
    ['2025-06', 'ArrowUp', '2027-06'],
    ['2025-06', 'ArrowDown', '2023-06'],
    ['2024-07', 'ArrowUp', '2025-07'],
    ['2025-07', 'ArrowDown', '2024-07'],
  ])(
    'should navigate vertically from %s with %s to %s in twelve-month increments',
    async (from, key, to) => {
      const months = ['2023-06', '2024-07', '2025-06', '2025-07', '2027-06'].map(monthKey => ({
        ...firstMonth(),
        key: monthKey,
        name: `${monthKey}, +€100.00, +1.0%`,
      }))
      const wrapper = createWrapper(calendarWith(months))
      await monthCell(wrapper, from).trigger('click')

      pressKey(wrapper, { key })
      await wrapper.vm.$nextTick()

      expect(selectedKey(wrapper)).toBe(to)
      expect(announced(wrapper)).toBe(`${to}, +€100.00, +1.0%`)
    }
  )

  it.each([
    ['2025-12', 'ArrowRight', '2026-01'],
    ['2026-01', 'ArrowLeft', '2025-12'],
  ])('should cross the year boundary from %s with %s to %s', async (from, key, to) => {
    const wrapper = createWrapper(
      calendarWith([
        { ...firstMonth(), key: '2025-12' },
        { ...september(), key: '2026-01' },
      ])
    )
    await monthCell(wrapper, from).trigger('click')

    pressKey(wrapper, { key })
    await wrapper.vm.$nextTick()

    expect(selectedKey(wrapper)).toBe(to)
  })

  it.each([
    { key: 'ArrowLeft', altKey: true },
    { key: 'ArrowLeft', metaKey: true },
    { key: 'ArrowDown', ctrlKey: true },
    { key: 'Tab' },
  ])('should leave modified keys and Tab to the browser: %o', init => {
    const wrapper = createWrapper()

    expect(pressKey(wrapper, init)).toBe(false)
    expect([selectedKey(wrapper), announced(wrapper)]).toEqual(['2025-09', ''])
  })

  it('should clear hover when committing a keyboard selection', async () => {
    const wrapper = createWrapper()
    await monthCell(wrapper, '2025-06').trigger('pointerenter', { pointerType: 'mouse' })

    pressKey(wrapper, { key: 'ArrowLeft' })
    await wrapper.vm.$nextTick()

    expect([
      selectedKey(wrapper),
      wrapper.find('.monthly-profit-panel').attributes('data-month'),
    ]).toEqual(['2025-07', '2025-07'])
    expect(wrapper.findAll('.previewed')).toHaveLength(0)
  })

  it('should skip missing months with panel arrows and retain their focus at the end', async () => {
    const wrapper = createWrapper()
    const previous = wrapper.find('button[aria-label="Previous month"]')
    ;(previous.element as HTMLElement).focus()

    await previous.trigger('click')
    expect(selectedKey(wrapper)).toBe('2025-07')
    await previous.trigger('click')
    await previous.trigger('click')

    expect([selectedKey(wrapper), previous.attributes('aria-disabled')]).toEqual([
      '2025-06',
      'true',
    ])
    expect(document.activeElement).toBe(previous.element)
  })

  it('should commit Best and Worst shortcuts through the same selection state', async () => {
    const wrapper = createWrapper()
    const shortcuts = wrapper.findAll('.monthly-profit-extreme-btn')
    expect(shortcuts).toHaveLength(2)

    await shortcuts[1].trigger('click')
    expect([selectedKey(wrapper), announced(wrapper)]).toEqual([
      '2025-07',
      'July 2025, −€50.00, −0.5%',
    ])
    await shortcuts[0].trigger('click')
    expect([selectedKey(wrapper), announced(wrapper)]).toEqual([
      '2025-06',
      'June 2025, +€100.00, +1.0%, from 27 Jun',
    ])
  })

  it('should explain the monthly color scale and show a missing key only when needed', async () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.monthly-profit-legend').exists()).toBe(true)
    expect(wrapper.find('.monthly-profit-legend').text()).toContain('±0.3%')
    expect(wrapper.find('.monthly-profit-legend').text()).toContain('No daily summary')

    await wrapper.setProps({ calendar: calendarWith([firstMonth(), july()]) })

    expect(wrapper.find('.monthly-profit-legend').text()).not.toContain('No daily summary')
  })

  it('should render the compact calendar with decorative dots for phones', () => {
    const wrapper = createWrapper()

    expect(wrapper.find('.monthly-profit-compact').exists()).toBe(true)
    expect(wrapper.find('.monthly-profit-compact').findAll('.monthly-profit-dot')).toHaveLength(12)
    expect(wrapper.find('.monthly-profit-compact').findAll('button, [tabindex]')).toHaveLength(0)
  })

  it('should show year-level coverage visibly while the total stays numeric', () => {
    const wrapper = createWrapper()

    expect(wrapper.find('.monthly-profit-main .monthly-profit-coverage').exists()).toBe(true)
    expect(wrapper.find('.monthly-profit-main .monthly-profit-coverage').text()).toContain(
      '2025 · from 27 Jun'
    )
    expect(wrapper.find('.monthly-profit-grid .monthly-profit-year-total').text()).toBe('−2.5%')
  })
})
