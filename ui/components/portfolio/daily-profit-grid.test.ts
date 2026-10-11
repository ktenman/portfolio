import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import DailyProfitCell from './daily-profit-cell.vue'
import DailyProfitGrid from './daily-profit-grid.vue'
import DailyProfitLegend from './daily-profit-legend.vue'
import type { DailyProfitCalendar } from '../../services/daily-profit-calendar'
import {
  createCalendarRow,
  createCalendarRows,
  createDailyProfitCalendar,
} from '../../tests/fixtures'

enableAutoUnmount(afterEach)

const createGrid = (
  props: {
    calendar?: DailyProfitCalendar
    selected?: string
    previewed?: string
    compact?: boolean
  } = {}
) =>
  mount(DailyProfitGrid, {
    props: { calendar: createDailyProfitCalendar(), selected: '2025-12-09', ...props },
    attachTo: document.body,
  })

const cellsOf = (wrapper: ReturnType<typeof createGrid>) =>
  wrapper.findAllComponents(DailyProfitCell)

const pressKey = (wrapper: ReturnType<typeof createGrid>, init: KeyboardEventInit) => {
  const event = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init })
  wrapper.element.dispatchEvent(event)
  return event.defaultPrevented
}

describe('DailyProfitCell', () => {
  it.each([
    ['loss-4', ['bg-loss']],
    ['loss-3', ['bg-loss-step-3']],
    ['loss-2', ['bg-loss-step-2']],
    ['loss-1', ['bg-loss-step-1']],
    [
      'flat',
      [
        'relative',
        'after:absolute',
        'after:inset-0',
        'after:m-auto',
        'after:size-[var(--mark,44.44%)]',
        'after:rounded-control',
        'after:bg-change-flat',
      ],
    ],
    ['gain-1', ['bg-gain-step-1']],
    ['gain-2', ['bg-gain-step-2']],
    ['gain-3', ['bg-gain-step-3']],
    ['gain-4', ['bg-gain']],
    ['starting', ['border', 'border-ink-faint', 'bg-surface']],
    ['missing', ['border', 'border-dashed', 'border-ink-faint']],
  ] as const)('should paint a %s cell with %s', (variant, classes) => {
    const wrapper = mount(DailyProfitCell, { props: { variant } })

    expect(wrapper.classes()).toEqual(expect.arrayContaining([...classes]))
  })
})

describe('DailyProfitGrid', () => {
  it('should give every day the variant of its step or kind', () => {
    const variants = cellsOf(createGrid()).map(cell => cell.props('variant'))

    expect(variants).toEqual([
      'starting',
      'gain-2',
      'loss-2',
      'flat',
      'missing',
      'gain-3',
      'gain-1',
      'loss-1',
      'gain-2',
    ])
  })

  it('should place every day in the column of its week and the row of its weekday', () => {
    const places = cellsOf(createGrid()).map(cell => cell.attributes('style'))

    expect(places).toEqual([
      'grid-column: 2; grid-row: 2;',
      'grid-column: 2; grid-row: 3;',
      'grid-column: 2; grid-row: 4;',
      'grid-column: 2; grid-row: 5;',
      'grid-column: 2; grid-row: 6;',
      'grid-column: 2; grid-row: 7;',
      'grid-column: 2; grid-row: 8;',
      'grid-column: 3; grid-row: 2;',
      'grid-column: 3; grid-row: 3;',
    ])
  })

  it('should start a period that begins mid-week on the row of its weekday', () => {
    const calendar = createDailyProfitCalendar(createCalendarRows().slice(2))
    const places = cellsOf(createGrid({ calendar })).map(cell => cell.attributes('style'))

    expect([places[0], places[5], places[6]]).toEqual([
      'grid-column: 2; grid-row: 4;',
      'grid-column: 3; grid-row: 2;',
      'grid-column: 3; grid-row: 3;',
    ])
  })

  it.each([
    [2, 8],
    [1, 2],
  ])('should set %i week columns for a history of %i rows', (weeks, rows) => {
    const calendar = createDailyProfitCalendar(createCalendarRows().slice(0, rows))
    const wrapper = createGrid({ calendar, selected: '2025-12-02' })

    expect((wrapper.element as HTMLElement).style.getPropertyValue('--weeks')).toBe(String(weeks))
  })

  it('should name every cell as an image with a tooltip', () => {
    const names = [
      'Mon 1 Dec 2025, starting day',
      'Tue 2 Dec 2025, +€300.00, +0.30%',
      'Wed 3 Dec 2025, −€600.00, −0.60%',
      'Thu 4 Dec 2025, +€20.00, +0.02%',
      'Fri 5 Dec 2025, no daily summary',
      'Sat 6 Dec 2025, +€1,200.00, +1.21%',
      'Sun 7 Dec 2025, +€60.00, +0.06%',
      'Mon 8 Dec 2025, −€100.00, −0.10%',
      'Tue 9 Dec 2025, +€700.00, +0.70%',
    ]
    const cells = cellsOf(createGrid())

    expect(cells.map(cell => cell.attributes('aria-label'))).toEqual(names)
    expect(cells.map(cell => cell.attributes('title'))).toEqual(names)
    expect(cells.every(cell => cell.attributes('role') === 'img')).toBe(true)
  })

  it('should label the month above its column and Mon, Wed, Fri and Sun beside their rows', () => {
    const wrapper = createGrid()
    const labels = [...wrapper.findAll('.month-label'), ...wrapper.findAll('.weekday-label')]

    expect(labels.map(label => [label.text(), label.attributes('style')])).toEqual([
      ['Dec', 'grid-column: 2;'],
      ['Mon', 'grid-row: 2;'],
      ['Wed', 'grid-row: 4;'],
      ['Fri', 'grid-row: 6;'],
      ['Sun', 'grid-row: 8;'],
    ])
    expect(labels.every(label => label.attributes('aria-hidden') === 'true')).toBe(true)
  })

  it('should mark only the month name above the last week column', () => {
    const calendar = createDailyProfitCalendar([
      createCalendarRow('2025-10-27', 1000),
      createCalendarRow('2025-12-01', 1100),
    ])
    const wrapper = createGrid({ calendar, selected: '2025-12-01' })

    expect(
      wrapper.findAll('.month-label').map(label => [label.text(), label.classes('last-week')])
    ).toEqual([
      ['Nov', false],
      ['Dec', true],
    ])
  })

  it('should leave the month name of a single week unmarked', () => {
    const calendar = createDailyProfitCalendar([
      createCalendarRow('2025-12-01', 1000),
      createCalendarRow('2025-12-02', 1100),
    ])
    const wrapper = createGrid({ calendar, selected: '2025-12-02' })

    expect(
      wrapper.findAll('.month-label').map(label => [label.text(), label.classes('last-week')])
    ).toEqual([['Dec', false]])
  })

  it('should be one named Tab stop that is described by the selected cell', () => {
    const wrapper = createGrid({ selected: '2025-12-06' })
    const selected = wrapper.findAll('.selected')

    expect(wrapper.attributes()).toMatchObject({
      role: 'application',
      'aria-label': 'Daily profit calendar',
      tabindex: '0',
      'aria-describedby': selected[0].attributes('id'),
    })
    expect(wrapper.findAll('[tabindex]')).toHaveLength(1)
    expect(wrapper.findAll('[id]')).toHaveLength(1)
    expect(wrapper.findAll('[aria-current]')).toHaveLength(1)
    expect(
      selected.map(cell => [cell.attributes('aria-label'), cell.attributes('aria-current')])
    ).toEqual([['Sat 6 Dec 2025, +€1,200.00, +1.21%', 'true']])
  })

  it.each([
    ['2025-12-04', 'ArrowDown', '2025-12-06'],
    ['2025-12-06', 'ArrowUp', '2025-12-04'],
    ['2025-12-02', 'ArrowRight', '2025-12-09'],
    ['2025-12-09', 'ArrowLeft', '2025-12-02'],
  ])('should move from %s with %s to %s', (selected, key, expected) => {
    const wrapper = createGrid({ selected })

    const cancelled = pressKey(wrapper, { key })

    expect(wrapper.emitted('select')).toEqual([[expected]])
    expect(cancelled).toBe(true)
  })

  it.each([
    ['2025-12-09', 'ArrowDown'],
    ['2025-12-09', 'ArrowRight'],
    ['2025-12-01', 'ArrowUp'],
    ['2025-12-01', 'ArrowLeft'],
  ])('should stay on %s with %s and still keep the page from scrolling', (selected, key) => {
    const wrapper = createGrid({ selected })

    const cancelled = pressKey(wrapper, { key })

    expect(wrapper.emitted('select')).toBeUndefined()
    expect(cancelled).toBe(true)
  })

  it.each([
    [{ key: 'ArrowLeft', altKey: true }],
    [{ key: 'ArrowLeft', metaKey: true }],
    [{ key: 'ArrowUp', ctrlKey: true }],
    [{ key: 'Tab' }],
    [{ key: 'PageDown' }],
  ])('should leave %o to the browser', init => {
    const wrapper = createGrid({ selected: '2025-12-09' })

    const cancelled = pressKey(wrapper, init)

    expect(wrapper.emitted('select')).toBeUndefined()
    expect(cancelled).toBe(false)
  })

  it('should select a clicked day and take the focus so the arrow keys go on from it', async () => {
    const wrapper = createGrid()

    await cellsOf(wrapper)[6].trigger('click')

    expect(wrapper.emitted('select')).toEqual([['2025-12-07']])
    expect(document.activeElement).toBe(wrapper.element)
  })

  it('should not select a missing day', async () => {
    const wrapper = createGrid()

    await cellsOf(wrapper)[4].trigger('click')

    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('should preview the day under the mouse until the pointer leaves the map', async () => {
    const wrapper = createGrid()

    await cellsOf(wrapper)[6].trigger('pointerenter', { pointerType: 'mouse' })
    await wrapper.trigger('pointerleave')

    expect(wrapper.emitted('preview')).toEqual([['2025-12-07'], [null]])
  })

  it.each([
    ['a missing day under the mouse', 4, 'mouse'],
    ['a day under a finger', 6, 'touch'],
  ])('should not preview %s', async (_, index, pointerType) => {
    const wrapper = createGrid()

    await cellsOf(wrapper)[index].trigger('pointerenter', { pointerType })

    expect(wrapper.emitted('preview')).toBeUndefined()
  })

  it.each([
    ['2025-12-07', ['Sun 7 Dec 2025, +€60.00, +0.06%']],
    ['2025-12-09', []],
  ])('should ring the previewed day %s unless it is the selected day', (previewed, expected) => {
    const wrapper = createGrid({ selected: '2025-12-09', previewed })

    expect(wrapper.findAll('.previewed').map(cell => cell.attributes('aria-label'))).toEqual(
      expected
    )
  })

  describe('compact', () => {
    it('should keep the month names and the dots and drop the weekday labels', () => {
      const wrapper = createGrid({ compact: true })

      expect(wrapper.classes()).toContain('compact')
      expect(wrapper.findAll('.month-label').map(label => label.text())).toEqual(['Dec'])
      expect(wrapper.findAll('.weekday-label')).toHaveLength(0)
      expect(cellsOf(wrapper)).toHaveLength(9)
    })

    it('should place the weeks from the first column', () => {
      const wrapper = createGrid({ compact: true })

      expect(wrapper.find('.month-label').attributes('style')).toBe('grid-column: 1;')
      expect(cellsOf(wrapper)[7].attributes('style')).toBe('grid-column: 2; grid-row: 2;')
    })

    it('should be hidden from assistive technology with nothing to focus, name or select', async () => {
      const wrapper = createGrid({ compact: true, selected: undefined })
      const cell = cellsOf(wrapper)[6]

      await cell.trigger('click')

      expect(wrapper.attributes()).toMatchObject({ 'aria-hidden': 'true' })
      expect(['role', 'tabindex', 'aria-label'].map(name => wrapper.attributes(name))).toEqual([
        undefined,
        undefined,
        undefined,
      ])
      expect(['role', 'aria-label', 'title'].map(name => cell.attributes(name))).toEqual([
        undefined,
        undefined,
        undefined,
      ])
      expect(wrapper.emitted('select')).toBeUndefined()
    })
  })
})

describe('DailyProfitLegend', () => {
  it('should run from the darkest loss to the darkest gain with the flat mark in the middle', () => {
    const fills = mount(DailyProfitLegend, { props: { missing: false } })
      .findAll('.legend-swatch')
      .map(swatch => swatch.classes().filter(name => name.includes('bg-')))

    expect(fills).toEqual([
      ['bg-loss'],
      ['bg-loss-step-3'],
      ['bg-loss-step-2'],
      ['bg-loss-step-1'],
      ['after:bg-change-flat'],
      ['bg-gain-step-1'],
      ['bg-gain-step-2'],
      ['bg-gain-step-3'],
      ['bg-gain'],
    ])
  })

  it('should print the step values of the one constant under the joints they mark', () => {
    const texts = mount(DailyProfitLegend, { props: { missing: false } })
      .findAll('.legend-text')
      .map(text => [text.text(), text.attributes('style')])

    expect(texts).toEqual([
      ['Loss', undefined],
      ['1.5', 'grid-column: 2 / span 2;'],
      ['0.75', 'grid-column: 3 / span 2;'],
      ['0.3', 'grid-column: 4 / span 2;'],
      ['±0.05%', 'grid-column: 6;'],
      ['0.3', 'grid-column: 7 / span 2;'],
      ['0.75', 'grid-column: 8 / span 2;'],
      ['1.5', 'grid-column: 9 / span 2;'],
      ['Gain', undefined],
    ])
  })

  it('should key the dashed swatch only when a day has no daily summary', () => {
    const keysOf = (missing: boolean) =>
      mount(DailyProfitLegend, { props: { missing } })
        .findAll('.legend-missing')
        .map(key => [key.findComponent(DailyProfitCell).props('variant'), key.text()])

    expect([keysOf(false), keysOf(true)]).toEqual([[], [['missing', 'No daily summary']]])
  })
})
