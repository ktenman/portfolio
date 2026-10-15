import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SummaryIntervalToggle from './summary-interval-toggle.vue'

describe('SummaryIntervalToggle', () => {
  const pressed = (selected: 'daily' | 'monthly') =>
    mount(SummaryIntervalToggle, { props: { selected } })
      .findAll('button')
      .map(button => [button.text(), button.attributes('aria-pressed'), button.classes('active')])

  it('names its group Interval', () => {
    const group = mount(SummaryIntervalToggle, { props: { selected: 'daily' } }).find(
      '[role=group]'
    )
    expect(group.attributes('aria-label')).toBe('Interval')
  })

  it('marks only the selected interval as pressed', () => {
    expect([pressed('daily'), pressed('monthly')]).toEqual([
      [
        ['Daily', 'true', true],
        ['Monthly', 'false', false],
      ],
      [
        ['Daily', 'false', false],
        ['Monthly', 'true', true],
      ],
    ])
  })

  it('emits the pressed interval and keeps the button that was pressed', async () => {
    const wrapper = mount(SummaryIntervalToggle, { props: { selected: 'daily' } })
    const monthly = wrapper.findAll('button')[1]

    await monthly.trigger('click')
    await wrapper.setProps({ selected: 'monthly' })

    expect([wrapper.emitted('select'), wrapper.findAll('button')[1].element]).toEqual([
      [['monthly']],
      monthly.element,
    ])
  })
})
