import { beforeEach, describe, it, expect } from 'vitest'
import { enableAutoUnmount, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import InstrumentPeriodPicker from './instrument-period-picker.vue'
import { TimeRange } from '../../models/generated/domain-models'

enableAutoUnmount(beforeEach)

const createWrapper = (selected: TimeRange = TimeRange.ONE_DAY) =>
  mount(InstrumentPeriodPicker, { props: { selected }, attachTo: document.body })

const toggle = (wrapper: VueWrapper) => wrapper.find('[aria-controls="period-picker"]')

const openPicker = async (selected?: TimeRange) => {
  const wrapper = createWrapper(selected)
  await toggle(wrapper).trigger('click')
  return wrapper
}

const cell = (wrapper: VueWrapper, label: string, text: string) =>
  wrapper
    .find(`[role="group"][aria-label="${label}"]`)
    .findAll('button')
    .find(candidate => candidate.text() === text)!

const isOpen = (wrapper: VueWrapper) => wrapper.find('#period-picker').exists()

describe('InstrumentPeriodPicker', () => {
  it.each([
    [TimeRange.ONE_DAY, '1D'],
    [TimeRange.ONE_YEAR, '1Y'],
    [TimeRange.THREE_WEEKS, '3W'],
  ])('should show %s alone on one button', (selected, text) => {
    expect(
      createWrapper(selected)
        .findAll('button')
        .map(button => button.text())
    ).toEqual([text])
  })

  it('should name the button after the price change period', () => {
    expect(cell(createWrapper(TimeRange.ONE_YEAR), 'Price change period', '1Y').exists()).toBe(true)
  })

  it('should leave the button neutral at rest on a period outside the shortlist', () => {
    expect(toggle(createWrapper(TimeRange.SIX_YEARS)).attributes('data-active')).toBeUndefined()
  })

  it('should mark the button active while the popover is open', async () => {
    const wrapper = await openPicker()

    expect(toggle(wrapper).attributes('data-active')).toBe('true')
  })

  it('should list the shortlist above the unit and length rows', async () => {
    const wrapper = await openPicker()

    const rows = wrapper
      .find('#period-picker')
      .findAll('[role="group"]')
      .map(row => row.findAll('button').map(button => button.text()))

    expect(rows).toEqual([
      ['1D', '1W', '1M', '6M', 'YTD', '1Y', '5Y', 'MAX'],
      ['Days', 'Weeks', 'Months', 'Years'],
      ['1', '2', '3', '4', '5', '6'],
    ])
  })

  it('should press only the selected period in the shortlist row', async () => {
    const wrapper = await openPicker(TimeRange.YTD)

    const pressed = wrapper
      .find('#period-picker')
      .findAll('[aria-pressed="true"]')
      .map(button => button.text())

    expect(pressed).toEqual(['YTD'])
  })

  it('should emit a shortlist period picked in the popover', async () => {
    const wrapper = await openPicker()

    await cell(wrapper, 'Common periods', 'MAX').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['MAX']])
  })

  it('should close the popover when a shortlist period is picked', async () => {
    const wrapper = await openPicker()

    await cell(wrapper, 'Common periods', 'MAX').trigger('click')

    expect(isOpen(wrapper)).toBe(false)
  })

  it('should emit the same length in the unit picked', async () => {
    const wrapper = await openPicker(TimeRange.ONE_WEEK)

    await cell(wrapper, 'Unit', 'Months').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['1M']])
  })

  it('should stay open when the unit changes', async () => {
    const wrapper = await openPicker(TimeRange.ONE_WEEK)

    await cell(wrapper, 'Unit', 'Months').trigger('click')

    expect(isOpen(wrapper)).toBe(true)
  })

  it('should emit the period a length makes in the current unit', async () => {
    const wrapper = await openPicker(TimeRange.ONE_WEEK)

    await cell(wrapper, 'Length', '3').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['3W']])
  })

  it('should close the popover when a length is picked', async () => {
    const wrapper = await openPicker(TimeRange.ONE_WEEK)

    await cell(wrapper, 'Length', '3').trigger('click')

    expect(isOpen(wrapper)).toBe(false)
  })

  it('should close the popover on Escape', async () => {
    const wrapper = await openPicker()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()

    expect(isOpen(wrapper)).toBe(false)
  })

  it('should return focus to the button on Escape', async () => {
    const wrapper = await openPicker()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()

    expect(document.activeElement).toBe(toggle(wrapper).element)
  })

  it('should close the popover on a click outside it', async () => {
    const wrapper = await openPicker()
    await new Promise(resolve => setTimeout(resolve, 0))

    document.body.click()
    await nextTick()

    expect(isOpen(wrapper)).toBe(false)
  })
})
