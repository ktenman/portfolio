import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { useLiveUpdates } from './use-live-updates'
import { renderWithProviders } from '../tests/test-utils'
import { FakeEventSource } from '../tests/fixtures'
import { LiveUpdate } from '../models/generated/domain-models'

enableAutoUnmount(afterEach)

const authenticated = ref(true)

vi.mock('./use-auth-state', () => ({
  useAuthState: () => ({ isAuthenticated: authenticated }),
}))

vi.stubGlobal('EventSource', FakeEventSource)

const stream = () => FakeEventSource.instances[FakeEventSource.instances.length - 1]

const push = async (topic: LiveUpdate) => {
  stream().dispatchEvent(new Event(topic))
  await flushPromises()
}

const fail = async () => {
  stream().readyState = FakeEventSource.CLOSED
  stream().onerror?.(new Event('error'))
  await flushPromises()
}

const setVisibility = async (state: DocumentVisibilityState) => {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(state)
  document.dispatchEvent(new Event('visibilitychange'))
  await flushPromises()
}

const render = () => {
  const wrapper = renderWithProviders(
    defineComponent({
      setup() {
        useLiveUpdates()
        return () => h('div')
      },
    })
  )
  return vi.spyOn(wrapper.queryClient, 'invalidateQueries')
}

describe('useLiveUpdates', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    vi.restoreAllMocks()
    FakeEventSource.instances = []
    authenticated.value = true
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('does not open the stream before the user is authenticated', () => {
    authenticated.value = false
    render()
    expect(FakeEventSource.instances).toHaveLength(0)
  })

  it('opens the live-updates stream once the user is authenticated', async () => {
    authenticated.value = false
    render()
    authenticated.value = true
    await flushPromises()
    expect(stream().url).toBe('/api/live-updates')
  })

  it('refreshes the instruments and the summary when prices change', async () => {
    const invalidate = render()
    await push(LiveUpdate.PRICES)
    expect(invalidate.mock.calls).toEqual([
      [{ queryKey: ['instruments'] }],
      [{ queryKey: ['portfolio-summary', 'current'] }],
      [{ queryKey: ['portfolio-summary', 'range-change'] }],
    ])
  })

  it('refreshes the summary queries when the summary changes', async () => {
    const invalidate = render()
    await push(LiveUpdate.SUMMARY)
    expect(invalidate.mock.calls).toEqual([
      [{ queryKey: ['portfolio-summary', 'current'] }],
      [{ queryKey: ['portfolio-summary', 'intraday'] }],
      [{ queryKey: ['portfolio-summary', 'range-change'] }],
      [{ queryKey: ['instruments'] }],
    ])
  })

  it('refreshes again when the same update arrives twice', async () => {
    const invalidate = render()
    await push(LiveUpdate.PRICES)
    await push(LiveUpdate.PRICES)
    expect(invalidate).toHaveBeenCalledTimes(6)
  })

  it('closes the stream while the tab is hidden', async () => {
    render()
    await setVisibility('hidden')
    expect(stream().readyState).toBe(FakeEventSource.CLOSED)
  })

  it('reopens the stream when the tab becomes visible again', async () => {
    render()
    await setVisibility('hidden')
    await setVisibility('visible')
    expect(FakeEventSource.instances).toHaveLength(2)
  })

  it('restarts the silence countdown when the stream reopens', async () => {
    render()
    vi.advanceTimersByTime(150_000)
    await setVisibility('hidden')
    await setVisibility('visible')
    vi.advanceTimersByTime(150_000)
    expect(FakeEventSource.instances).toHaveLength(2)
  })

  it('reopens the stream after three minutes of silence', () => {
    render()
    vi.advanceTimersByTime(180_000)
    expect(FakeEventSource.instances).toHaveLength(2)
  })

  it('keeps the stream open while updates keep arriving', async () => {
    render()
    vi.advanceTimersByTime(120_000)
    await push(LiveUpdate.SUMMARY)
    vi.advanceTimersByTime(120_000)
    expect(FakeEventSource.instances).toHaveLength(1)
  })

  it('reconnects fifteen seconds after the stream fails', async () => {
    render()
    await fail()
    vi.advanceTimersByTime(15_000)
    expect(FakeEventSource.instances).toHaveLength(2)
  })

  it('keeps one open stream when the tab is switched while a failed stream waits to reconnect', async () => {
    render()
    await fail()
    await setVisibility('hidden')
    await setVisibility('visible')
    vi.advanceTimersByTime(15_000)
    expect(
      FakeEventSource.instances.filter(source => source.readyState !== FakeEventSource.CLOSED)
    ).toHaveLength(1)
  })

  it('replaces a stream whose reconnect stalls for three minutes', async () => {
    render()
    stream().onerror?.(new Event('error'))
    await flushPromises()
    vi.advanceTimersByTime(180_000)
    expect(FakeEventSource.instances).toHaveLength(2)
    expect(FakeEventSource.instances[0].readyState).toBe(FakeEventSource.CLOSED)
  })
})
