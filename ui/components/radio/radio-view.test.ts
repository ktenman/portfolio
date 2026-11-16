import { afterEach, describe, expect, it } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter, useRoute } from 'vue-router'
import { h } from 'vue'
import RadioView from './radio-view.vue'

enableAutoUnmount(afterEach)

const EPISODE = {
  setup: () => {
    const name = String(useRoute().params.name)
    return () => h('p', name === '2026-10-17' ? 'Episode' : `Episode ${name}`)
  },
}

const open = async (path: string) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/radio',
        component: RadioView,
        children: [
          { path: '', name: 'Radio', component: { template: '<p>Recordings table</p>' } },
          { path: 'shows', name: 'RadioShows', component: { template: '<p>Shows table</p>' } },
          { path: 'catch-up', name: 'RadioCatchUp', component: { template: '<p>Schedule</p>' } },
          { path: ':name', component: EPISODE },
        ],
      },
    ],
  })
  await router.push(path)
  const wrapper = mount(
    { template: '<router-view />' },
    { global: { plugins: [router, [VueQueryPlugin, { queryClient: new QueryClient() }]] } }
  )
  await flushPromises()
  return { wrapper, router }
}

describe('RadioView', () => {
  it.each([
    ['/radio', 'Recordings table'],
    ['/radio/shows', 'Shows table'],
    ['/radio/catch-up', 'Schedule'],
  ])('should put the search field above the tab at %s', async (path, content) => {
    const { wrapper } = await open(path)
    expect([
      wrapper.find('input[aria-label="Search what was said"]').exists(),
      wrapper.text(),
    ]).toEqual([true, expect.stringContaining(content)])
  })

  it('should leave the search field off a recording', async () => {
    const { wrapper } = await open('/radio/2026-10-17')
    expect([
      wrapper.find('input[aria-label="Search what was said"]').exists(),
      wrapper.text(),
    ]).toEqual([false, 'Episode'])
  })

  it('should open another recording from a recording', async () => {
    const { wrapper, router } = await open('/radio/week-in-radio-2026-11-16')
    await router.push('/radio/2026-11-14-1200-keskpaevatund?t=845')
    await flushPromises()
    expect(wrapper.text()).toBe('Episode 2026-11-14-1200-keskpaevatund')
  })
})
