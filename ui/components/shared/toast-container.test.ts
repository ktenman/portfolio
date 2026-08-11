import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import ToastContainer from './toast-container.vue'
import { toasts, useToast, type ToastType } from '../../composables/use-toast'

enableAutoUnmount(afterEach)

const DURATIONS: Record<ToastType, number> = {
  success: 4000,
  error: 7500,
  info: 5000,
  warning: 6000,
}

describe('ToastContainer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    toasts.value = []
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders nothing when no toast is queued', () => {
    const wrapper = mount(ToastContainer)

    expect(wrapper.findAll('.toast')).toHaveLength(0)
  })

  it('renders a queued toast with the show class so it stays visible', async () => {
    const wrapper = mount(ToastContainer)

    useToast().success('Õnnestus')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.toast').classes()).toContain('show')
  })

  it('renders the message text next to the type label', async () => {
    const wrapper = mount(ToastContainer)

    useToast().error('Ühendus katkes')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.toast-body').text()).toBe('✕ Error: Ühendus katkes')
  })

  it('renders markup in a message as text rather than as html', async () => {
    const wrapper = mount(ToastContainer)

    useToast().info('<img src=x onerror="alert(1)">')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.toast-body').findAll('img')).toHaveLength(0)
  })

  it.each([
    ['success', 'toast-success'],
    ['error', 'toast-error'],
    ['info', 'toast-info'],
    ['warning', 'toast-warning'],
  ] as const)('styles a %s toast with %s', async (type, accent) => {
    const wrapper = mount(ToastContainer)

    useToast()[type]('Teade')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.toast').classes()).toContain(accent)
  })

  it('renders every queued toast', async () => {
    const wrapper = mount(ToastContainer)

    useToast().success('Esimene')
    useToast().warning('Teine')
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll('.toast')).toHaveLength(2)
  })

  it('removes a toast when its close button is clicked', async () => {
    const wrapper = mount(ToastContainer)
    useToast().success('Õnnestus')
    await wrapper.vm.$nextTick()

    await wrapper.find('.btn-close').trigger('click')

    expect(wrapper.findAll('.toast')).toHaveLength(0)
  })

  it.each(Object.entries(DURATIONS) as [ToastType, number][])(
    'dismisses a %s toast at %i ms and not a tick earlier',
    async (type, duration) => {
      const wrapper = mount(ToastContainer)
      useToast()[type]('Teade')

      vi.advanceTimersByTime(duration - 1)
      await wrapper.vm.$nextTick()
      expect(wrapper.findAll('.toast')).toHaveLength(1)

      vi.advanceTimersByTime(1)
      await wrapper.vm.$nextTick()

      expect(wrapper.findAll('.toast')).toHaveLength(0)
    }
  )

  it('dismisses only the expired toast when durations differ', async () => {
    const wrapper = mount(ToastContainer)
    useToast().success('Lühike')
    useToast().error('Pikk')

    vi.advanceTimersByTime(DURATIONS.success)
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.toast-body').text()).toBe('✕ Error: Pikk')
  })
})
