import { afterEach, describe, it, expect } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import DailyProfitPeriod from './daily-profit-period.vue'

enableAutoUnmount(afterEach)

const createWrapper = (props: { earlierDisabled?: boolean; busy?: boolean } = {}) =>
  mount(DailyProfitPeriod, {
    props: {
      dates: '30 Jun – 31 Dec 2025',
      earlierDisabled: false,
      laterDisabled: false,
      busy: false,
      ...props,
    },
    attachTo: document.body,
  })

const arrow = (wrapper: ReturnType<typeof createWrapper>, name: string) =>
  wrapper.find(`button[aria-label="${name}"]`)

describe('DailyProfitPeriod', () => {
  it('should name the group and put the dates between its two arrows', () => {
    const wrapper = createWrapper()

    expect([
      wrapper.attributes('role'),
      wrapper.attributes('aria-label'),
      Array.from((wrapper.element as HTMLElement).children).map(
        child => child.getAttribute('aria-label') ?? child.textContent
      ),
    ]).toEqual(['group', 'Period', ['Earlier period', '30 Jun – 31 Dec 2025', 'Later period']])
  })

  it('should give each arrow its name as a tooltip', () => {
    const wrapper = createWrapper()

    expect(
      ['Earlier period', 'Later period'].map(name => [
        arrow(wrapper, name).attributes('title'),
        arrow(wrapper, name).attributes('type'),
      ])
    ).toEqual([
      ['Earlier period', 'button'],
      ['Later period', 'button'],
    ])
  })

  it('should emit earlier and later when its arrows are pressed', async () => {
    const wrapper = createWrapper()

    await arrow(wrapper, 'Earlier period').trigger('click')
    await arrow(wrapper, 'Later period').trigger('click')

    expect([wrapper.emitted('earlier'), wrapper.emitted('later')]).toEqual([[[]], [[]]])
  })

  it('should keep a disabled arrow focusable but idle', async () => {
    const wrapper = createWrapper({ earlierDisabled: true })
    const earlier = arrow(wrapper, 'Earlier period')

    await earlier.trigger('click')

    expect([
      earlier.attributes('aria-disabled'),
      earlier.attributes('disabled'),
      wrapper.emitted('earlier'),
    ]).toEqual(['true', undefined, undefined])
  })

  it('should mark only the earlier arrow busy', () => {
    const wrapper = createWrapper({ busy: true })

    expect([
      arrow(wrapper, 'Earlier period').attributes('aria-busy'),
      arrow(wrapper, 'Later period').attributes('aria-busy'),
    ]).toEqual(['true', undefined])
  })
})
