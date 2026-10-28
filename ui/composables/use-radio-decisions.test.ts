import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useRadioDecisions } from './use-radio-decisions'
import { recording } from '../components/radio/radio-fixture'

const { review, error, invalidateQueries } = vi.hoisted(() => ({
  review: vi.fn(),
  error: vi.fn(),
  invalidateQueries: vi.fn(() => Promise.resolve()),
}))

vi.mock('../services/api', () => ({ radioService: { review } }))
vi.mock('./use-toast', () => ({ useToast: () => ({ error }) }))
vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries }),
}))

describe('useRadioDecisions', () => {
  const shown = ref(recording())
  let decisions = useRadioDecisions(() => shown.value)

  beforeEach(() => {
    vi.useFakeTimers()
    review.mockReset().mockResolvedValue(undefined)
    invalidateQueries.mockClear()
    shown.value = recording()
    decisions = useRadioDecisions(() => shown.value)
  })

  afterEach(async () => {
    await decisions.flush()
    vi.useRealTimers()
  })

  it('should save five quick decisions once', () => {
    ;[0, 1, 2, 3, 0].forEach(i => decisions.choose(i, false))
    vi.advanceTimersByTime(3000)
    expect(review.mock.calls).toEqual([['2026-10-17', { removed: [] }]])
  })

  it('should send the last decision when a newer one arrives during the wait', () => {
    decisions.choose(1, false)
    vi.advanceTimersByTime(2000)
    decisions.choose(1, true)
    vi.advanceTimersByTime(3000)
    expect(review.mock.calls).toEqual([['2026-10-17', { removed: [0, 1, 2, 3] }]])
  })

  it('should show a decision before it is saved', () => {
    decisions.choose(2, false)
    expect([decisions.removed.value, review.mock.calls.length]).toEqual([[0, 1, 3], 0])
  })

  it('should keep an unsaved decision when the recordings are refetched', () => {
    decisions.choose(2, false)
    shown.value = recording()
    expect(decisions.removed.value).toEqual([0, 1, 3])
  })

  it('should save a waiting decision at once when flushed', async () => {
    decisions.choose(2, false)
    await decisions.flush()
    expect(review.mock.calls).toEqual([['2026-10-17', { removed: [0, 1, 3] }]])
  })

  it('should mark the search results stale once a decision is saved', async () => {
    decisions.choose(2, false)
    await decisions.flush()
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['radio-search'] })
  })

  it('should say a decision waits until it is saved', async () => {
    decisions.choose(2, false)
    const before = decisions.waiting.value
    await decisions.flush()
    expect([before, decisions.waiting.value]).toEqual([true, false])
  })

  it('should show what the server has when a save fails', async () => {
    review.mockRejectedValue(new Error('offline'))
    decisions.choose(2, false)
    await decisions.flush()
    expect([decisions.removed.value, decisions.waiting.value]).toEqual([[0, 1, 2, 3], false])
  })

  it('should save a waiting decision when the page is closed', () => {
    decisions.choose(2, false)
    window.dispatchEvent(new Event('beforeunload', { cancelable: true }))
    expect(review.mock.calls).toEqual([['2026-10-17', { removed: [0, 1, 3] }]])
  })

  it('should ask before the page closes while a decision waits', () => {
    decisions.choose(2, false)
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  })

  it('should remove every cut on reset', () => {
    decisions.choose(0, false)
    decisions.reset()
    expect(decisions.removed.value).toEqual([0, 1, 2, 3])
  })
})
