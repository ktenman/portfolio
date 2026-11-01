import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { Station } from '../../models/generated/domain-models'
import { radioService } from '../../services/api'
import RadioTabs from './radio-tabs.vue'
import { radioSearch } from './radio-fixture'

vi.mock('../../services/api', () => ({ radioService: { search: vi.fn() } }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const page = { template: '<p />' }

const open = async (path: string) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/radio', component: page },
      { path: '/radio/shows', component: page },
      { path: '/radio/catch-up', component: page },
      { path: '/radio/:name', component: page },
    ],
  })
  await router.push(path)
  const wrapper = mount(RadioTabs, {
    slots: { default: '<p>Recordings table</p>' },
    global: { plugins: [router, [VueQueryPlugin, { queryClient: new QueryClient() }]] },
  })
  await flushPromises()
  return { router, wrapper }
}

const chips = (wrapper: VueWrapper) =>
  wrapper.findAll('[role="group"][aria-label="Station"] button.platform-btn')

const chip = (wrapper: VueWrapper, label: string) =>
  chips(wrapper).find(button => button.text() === label)!

describe('RadioTabs', () => {
  beforeEach(() => vi.mocked(radioService.search).mockReset().mockResolvedValue(radioSearch()))

  afterEach(() => vi.useRealTimers())

  it('should put the typed words in the address after a pause', async () => {
    const { router, wrapper } = await open('/radio/catch-up?day=2026-10-14')
    vi.useFakeTimers()
    await wrapper.get('input').setValue('  valimis ')
    await vi.advanceTimersByTimeAsync(300)
    expect(router.currentRoute.value.query).toEqual({ day: '2026-10-14', q: 'valimis' })
  })

  it.each([
    ['/radio/catch-up', '/radio'],
    ['/radio', '/radio/catch-up'],
  ])(
    'should drop the typed words when a view tab opens before the pause from %s to %s',
    async (from, to) => {
      const { router, wrapper } = await open(from)
      vi.useFakeTimers()
      await wrapper.get('input').setValue('valimis')
      await wrapper.get(`nav a[href="${to}"]`).trigger('click')
      await vi.advanceTimersByTimeAsync(300)
      expect(router.currentRoute.value.fullPath).toBe(to)
    }
  )

  it('should show the results in place of the tab while the field has text', async () => {
    const { wrapper } = await open('/radio?q=valimis')
    expect([
      wrapper.text().includes('Recordings table'),
      wrapper.text().includes('Said on air'),
    ]).toEqual([false, true])
  })

  it('should bring the tab back when the field is cleared', async () => {
    const { wrapper } = await open('/radio?q=valimis')
    vi.useFakeTimers()
    await wrapper.get('button[aria-label="Clear search"]').trigger('click')
    await vi.advanceTimersByTimeAsync(300)
    expect(wrapper.text()).toContain('Recordings table')
  })

  it.each([
    ['/radio', 'Recordings'],
    ['/radio/shows', 'Shows'],
    ['/radio/catch-up', 'Catch-up'],
    ['/radio?station=aripaev', 'Recordings'],
  ])('should mark the tab of %s as the current page', async (path, label) => {
    const { wrapper } = await open(path)
    expect(wrapper.get('nav a.active[aria-current="page"]').text()).toBe(label)
  })

  it('should highlight no view tab while the results replace it', async () => {
    const { wrapper } = await open('/radio?q=valimis')
    expect(wrapper.findAll('nav a.active')).toHaveLength(0)
  })

  it('should mark no view tab as the current page while the results replace it', async () => {
    const { wrapper } = await open('/radio?q=valimis')
    expect(wrapper.findAll('nav a[aria-current="page"]')).toHaveLength(0)
  })

  it('should fill the field from the address', async () => {
    const { wrapper } = await open('/radio?q=valimis')
    expect(wrapper.get('input').element.value).toBe('valimis')
  })

  it('should fill the field with a close word that was picked', async () => {
    const { router, wrapper } = await open('/radio?q=valimis')
    await router.push('/radio?q=valised')
    expect(wrapper.get('input').element.value).toBe('valised')
  })

  it('should offer all stations and each one by name', async () => {
    const { wrapper } = await open('/radio')
    expect(chips(wrapper).map(button => button.text())).toEqual(['All', 'Kuku', 'Äripäev'])
  })

  it.each([
    ['/radio', 'All'],
    ['/radio?station=kuku', 'Kuku'],
    ['/radio?station=aripaev', 'Äripäev'],
    ['/radio?station=KUKU', 'All'],
    ['/radio?station=õhtu', 'All'],
  ])('should press only the chip that %s selects', async (path, label) => {
    const { wrapper } = await open(path)
    const pressed = chips(wrapper).filter(button => button.attributes('aria-pressed') === 'true')
    expect(pressed.map(button => button.text())).toEqual([label])
  })

  it('should put a picked station in the address beside the other params', async () => {
    const { router, wrapper } = await open('/radio/catch-up?day=2026-10-14&q=valimis')
    await chip(wrapper, 'Äripäev').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({
      day: '2026-10-14',
      q: 'valimis',
      station: 'aripaev',
    })
  })

  it('should take the station out of the address when All is picked', async () => {
    const { router, wrapper } = await open('/radio/catch-up?day=2026-10-14&station=aripaev')
    await chip(wrapper, 'All').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ day: '2026-10-14' })
  })

  it('should replace the address when a chip is picked', async () => {
    const { router, wrapper } = await open('/radio')
    const replace = vi.spyOn(router, 'replace')
    await chip(wrapper, 'Kuku').trigger('click')
    expect(replace).toHaveBeenCalledOnce()
  })

  it('should carry the station across a view tab', async () => {
    const { router, wrapper } = await open('/radio?station=aripaev')
    await wrapper.get('nav a[href="/radio/shows?station=aripaev"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/radio/shows?station=aripaev')
  })

  it('should leave a station it cannot read off the view tabs', async () => {
    const { wrapper } = await open('/radio?station=KUKU')
    expect(wrapper.findAll('nav a').map(a => a.attributes('href'))).toEqual([
      '/radio',
      '/radio/shows',
      '/radio/catch-up',
    ])
  })

  it('should search the station in the address', async () => {
    await open('/radio?q=valimis&station=aripaev')
    expect(radioService.search).toHaveBeenLastCalledWith('valimis', null, Station.ARIPAEV)
  })
})
