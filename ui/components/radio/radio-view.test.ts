import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import RadioView from './radio-view.vue'

vi.mock('../../composables/use-radio-player', () => ({
  useRadioPlayer: () => ({ attach: vi.fn(), detach: vi.fn() }),
}))

enableAutoUnmount(afterEach)

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
          { path: ':name', component: { template: '<p>Episode</p>' } },
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
  return wrapper
}

describe('RadioView', () => {
  it.each([
    ['/radio', 'Recordings table'],
    ['/radio/shows', 'Shows table'],
    ['/radio/catch-up', 'Schedule'],
  ])('should put the search field above the tab at %s', async (path, content) => {
    const wrapper = await open(path)
    expect([
      wrapper.find('input[aria-label="Search what was said"]').exists(),
      wrapper.text(),
    ]).toEqual([true, expect.stringContaining(content)])
  })

  it('should leave the search field off a recording', async () => {
    const wrapper = await open('/radio/2026-10-17')
    expect([
      wrapper.find('input[aria-label="Search what was said"]').exists(),
      wrapper.text(),
    ]).toEqual([false, 'Episode'])
  })
})
