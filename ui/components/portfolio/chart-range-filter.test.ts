import { beforeEach, describe, it, expect } from 'vitest'
import { enableAutoUnmount, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import ChartRangeFilter from './chart-range-filter.vue'
import { TimeRange } from '../../models/generated/domain-models'

enableAutoUnmount(beforeEach)

const createWrapper = (selected: TimeRange = TimeRange.ONE_MONTH) =>
  mount(ChartRangeFilter, { props: { selected }, attachTo: document.body })

const group = (wrapper: VueWrapper, label: string) =>
  wrapper.find(`[role="group"][aria-label="${label}"]`)

const labels = (wrapper: VueWrapper, label: string) =>
  group(wrapper, label)
    .findAll('button')
    .map(button => button.text())

const button = (wrapper: VueWrapper, label: string, text: string) =>
  group(wrapper, label)
    .findAll('button')
    .find(candidate => candidate.text() === text)!

const more = (wrapper: VueWrapper) => wrapper.find('[aria-controls="period-picker"]')

const openPicker = async (selected: TimeRange) => {
  const wrapper = createWrapper(selected)
  await more(wrapper).trigger('click')
  return wrapper
}

const isOpen = (wrapper: VueWrapper) => wrapper.find('#period-picker').exists()

describe('ChartRangeFilter', () => {
  it('should show the shortlist and a More cell in the period row', () => {
    expect(labels(createWrapper(), 'Chart period')).toEqual([
      '1D',
      '1W',
      '1M',
      '6M',
      'YTD',
      '1Y',
      '5Y',
      'MAX',
      'More',
    ])
  })

  it('should press the selected shortlist period', () => {
    const pressed = createWrapper(TimeRange.ONE_YEAR)
      .findAll('[aria-pressed="true"]')
      .map(cell => cell.text())

    expect(pressed).toEqual(['1Y'])
  })

  it('should show a period outside the shortlist on the ninth cell and mark it active', () => {
    const ninth = more(createWrapper(TimeRange.SIX_YEARS))

    expect([ninth.text(), ninth.attributes('data-active')]).toEqual(['6Y', 'true'])
  })

  it('should leave the More cell inactive while a shortlist period is selected', () => {
    expect(more(createWrapper(TimeRange.MAX)).attributes('data-active')).toBeUndefined()
  })

  it('should emit a shortlist period when it is clicked', async () => {
    const wrapper = createWrapper()

    await button(wrapper, 'Chart period', 'YTD').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['YTD']])
  })

  it('should keep the popover closed until More is clicked', () => {
    expect(isOpen(createWrapper())).toBe(false)
  })

  it('should open the popover with the unit and length rows', async () => {
    const wrapper = await openPicker(TimeRange.SIX_YEARS)

    expect([labels(wrapper, 'Unit'), labels(wrapper, 'Length')]).toEqual([
      ['Days', 'Weeks', 'Months', 'Years'],
      ['1', '2', '3', '4', '5', '6'],
    ])
  })

  it('should mark the popover expanded on the More cell', async () => {
    const wrapper = await openPicker(TimeRange.ONE_MONTH)

    expect(more(wrapper).attributes('aria-expanded')).toBe('true')
  })

  it('should press the unit and length of the selected period', async () => {
    const wrapper = await openPicker(TimeRange.SIX_YEARS)

    const pressed = wrapper
      .find('#period-picker')
      .findAll('[aria-pressed="true"]')
      .map(cell => cell.text())

    expect(pressed).toEqual(['Years', '6'])
  })

  it('should apply a picked unit at the current length', async () => {
    const wrapper = await openPicker(TimeRange.SIX_YEARS)

    await button(wrapper, 'Unit', 'Months').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['6M']])
  })

  it('should keep the popover open after a unit is picked', async () => {
    const wrapper = await openPicker(TimeRange.SIX_YEARS)

    await button(wrapper, 'Unit', 'Weeks').trigger('click')

    expect(isOpen(wrapper)).toBe(true)
  })

  it('should apply a picked length in the current unit', async () => {
    const wrapper = await openPicker(TimeRange.SIX_YEARS)

    await button(wrapper, 'Length', '2').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['2Y']])
  })

  it('should close the popover after a length is picked', async () => {
    const wrapper = await openPicker(TimeRange.SIX_YEARS)

    await button(wrapper, 'Length', '2').trigger('click')

    expect(isOpen(wrapper)).toBe(false)
  })

  it('should press no unit on the year to date range', async () => {
    const wrapper = await openPicker(TimeRange.YTD)

    expect(group(wrapper, 'Unit').findAll('[aria-pressed="true"]')).toHaveLength(0)
  })

  it('should disable every length on the max range', async () => {
    const wrapper = await openPicker(TimeRange.MAX)

    const disabled = group(wrapper, 'Length')
      .findAll('button')
      .map(cell => cell.attributes('disabled') !== undefined)

    expect(disabled).toEqual([true, true, true, true, true, true])
  })

  it('should apply nothing when a unit is picked on the year to date range', async () => {
    const wrapper = await openPicker(TimeRange.YTD)

    await button(wrapper, 'Unit', 'Months').trigger('click')

    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('should enable the lengths once a unit is picked on the max range', async () => {
    const wrapper = await openPicker(TimeRange.MAX)

    await button(wrapper, 'Unit', 'Months').trigger('click')

    expect(button(wrapper, 'Length', '3').attributes('disabled')).toBeUndefined()
  })

  it('should apply the unit picked on the max range with the picked length', async () => {
    const wrapper = await openPicker(TimeRange.MAX)
    await button(wrapper, 'Unit', 'Months').trigger('click')

    await button(wrapper, 'Length', '3').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['3M']])
  })

  it('should close the popover when More is clicked again', async () => {
    const wrapper = await openPicker(TimeRange.ONE_MONTH)

    await more(wrapper).trigger('click')

    expect(isOpen(wrapper)).toBe(false)
  })

  it('should close the popover on Escape', async () => {
    const wrapper = await openPicker(TimeRange.ONE_MONTH)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()

    expect(isOpen(wrapper)).toBe(false)
  })

  it('should return focus to the More cell on Escape', async () => {
    const wrapper = await openPicker(TimeRange.ONE_MONTH)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()

    expect(document.activeElement).toBe(more(wrapper).element)
  })

  it('should close the popover on a click outside it', async () => {
    const wrapper = await openPicker(TimeRange.ONE_MONTH)
    await new Promise(resolve => setTimeout(resolve, 0))

    document.body.click()
    await nextTick()

    expect(isOpen(wrapper)).toBe(false)
  })

  it('should close the popover when a shortlist period is picked', async () => {
    const wrapper = await openPicker(TimeRange.SIX_YEARS)

    await button(wrapper, 'Chart period', '1Y').trigger('click')

    expect(isOpen(wrapper)).toBe(false)
  })
})
