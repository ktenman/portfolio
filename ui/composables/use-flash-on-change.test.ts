import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useFlashOnChange } from './use-flash-on-change'

describe('useFlashOnChange', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should dont flash before the value has arrived', async () => {
    const value = ref<number | null>(null)
    const flash = useFlashOnChange(value)
    value.value = 100
    await nextTick()
    expect(flash.value).toBe('')
  })

  it('should flash an increase when the value rises', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 101
    await nextTick()
    expect(flash.value).toBe('value-increase')
  })

  it('should flash a decrease when the value falls', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 99
    await nextTick()
    expect(flash.value).toBe('value-decrease')
  })

  it('should dont flash when the change is below the threshold', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 100.0001
    await nextTick()
    expect(flash.value).toBe('')
  })

  it('should respect a custom threshold for small percentage moves', async () => {
    const value = ref<number | null>(0.15)
    const flash = useFlashOnChange(value, 0.00001)
    value.value = 0.1502
    await nextTick()
    expect(flash.value).toBe('value-increase')
  })

  it('should clear the flash after the animation finishes', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 200
    await nextTick()
    vi.advanceTimersByTime(3000)
    expect(flash.value).toBe('')
  })

  it('should keep the flash while the animation is still running', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 200
    await nextTick()
    vi.advanceTimersByTime(2999)
    expect(flash.value).toBe('value-increase')
  })

  it('should replace an increase with a decrease when the value reverses mid-flash', async () => {
    const value = ref<number | null>(100)
    const flash = useFlashOnChange(value)
    value.value = 200
    await nextTick()
    vi.advanceTimersByTime(1000)
    value.value = 150
    await nextTick()
    vi.advanceTimersByTime(2500)
    expect(flash.value).toBe('value-decrease')
  })
})
