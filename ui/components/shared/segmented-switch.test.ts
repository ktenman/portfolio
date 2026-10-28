import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import SegmentedSwitch from './segmented-switch.vue'

enableAutoUnmount(afterEach)

const INTERVALS = [
  { value: 'daily', label: 'Daily' },
  { value: 'monthly', label: 'Monthly' },
]

const VIEWS = [
  { value: 'table', label: 'Table', icon: 'M3 5h18M3 12h18M3 19h18' },
  { value: 'heatmap', label: 'Heatmap', icon: 'M3 3h8v8H3zM13 3h8v18h-8zM3 13h8v8H3z' },
]

const mountSwitch = (announcement = '') =>
  mount(SegmentedSwitch, {
    props: { label: 'Interval', options: INTERVALS, selected: 'daily', announcement },
  })

const iconSwitch = () =>
  mount(SegmentedSwitch, { props: { label: 'View', options: VIEWS, selected: 'table' } })

describe('SegmentedSwitch', () => {
  it('groups the segments under Interval with pressed states', () => {
    const group = mountSwitch().find('[role="group"]')

    expect([
      group.attributes('aria-label'),
      group.findAll('button').map(button => button.text()),
      group.findAll('button').map(button => button.attributes('aria-pressed')),
    ]).toEqual(['Interval', ['Daily', 'Monthly'], ['true', 'false']])
  })

  it('emits the chosen interval', async () => {
    const wrapper = mountSwitch()
    const [daily, monthly] = wrapper.findAll('button')

    await monthly.trigger('click')
    await daily.trigger('click')

    expect(wrapper.emitted('select')).toEqual([['monthly'], ['daily']])
  })

  it('keeps the live region in the DOM', () =>
    expect(
      ['', 'Monthly profit Jun – Dec 2025'].map(text =>
        mountSwitch(text).find('p.sr-only[role="status"]').text()
      )
    ).toEqual(['', 'Monthly profit Jun – Dec 2025']))

  it('meets the touch height on phones without growing the segments', () =>
    expect(
      mountSwitch()
        .findAll('button')
        .map(button => [button.classes('md-down:min-h-11'), button.classes('md-down:before:h-11')])
    ).toEqual([
      [false, true],
      [false, true],
    ]))

  it('keeps the text as the accessible name of an icon option', () =>
    expect(
      iconSwitch()
        .findAll('button')
        .map(button => button.text())
    ).toEqual(['Table', 'Heatmap']))

  it('renders no status region without an announcement', () =>
    expect(iconSwitch().find('[role="status"]').exists()).toBe(false))
})
