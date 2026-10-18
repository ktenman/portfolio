import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import SummaryIntervalSwitch from './summary-interval-switch.vue'

enableAutoUnmount(afterEach)

const mountSwitch = (announcement = '') =>
  mount(SummaryIntervalSwitch, { props: { selected: 'daily', announcement } })

describe('SummaryIntervalSwitch', () => {
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
})
