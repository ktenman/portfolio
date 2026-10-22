import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioCard from './radio-card.vue'

describe('RadioCard', () => {
  beforeEach(() => localStorage.clear())

  const render = (fold?: 'open' | 'shut') =>
    mount(RadioCard, {
      props: { title: 'Processing', fold },
      slots: { default: '<p class="body">Content</p>', aside: '<span class="aside">34 MB</span>' },
    })

  it('should start shut when asked to', () => {
    expect(render('shut').find('.body').exists()).toBe(false)
  })

  it('should open on a click on its title', async () => {
    const wrapper = render('shut')
    await wrapper.get('button[aria-expanded="false"]').trigger('click')
    expect(wrapper.find('.body').exists()).toBe(true)
  })

  it('should remember a fold after a remount', async () => {
    await render('open').get('button[aria-expanded="true"]').trigger('click')
    expect(render('open').find('.body').exists()).toBe(false)
  })

  it('should keep the aside while shut', () => {
    expect(render('shut').find('.aside').exists()).toBe(true)
  })

  it('should not fold without being asked to', () => {
    expect(render().find('button').exists()).toBe(false)
  })
})
