import { describe, it, expect, vi } from 'vitest'
import { ref, nextTick, effectScope } from 'vue'
import { useNumberTransition } from './use-number-transition'

describe('useNumberTransition', () => {
  it('should show the value immediately when it arrives for the first time', async () => {
    const value = ref<number | null>(null)
    const display = useNumberTransition(value)
    value.value = 25429
    await nextTick()
    expect(display.value).toBe(25429)
  })

  it('should show the value immediately when it is already present on creation', () => {
    const display = useNumberTransition(ref<number | null>(25429))
    expect(display.value).toBe(25429)
  })

  it('should dont jump straight to the new value when it changes after arriving', async () => {
    const value = ref<number | null>(100)
    const display = useNumberTransition(value)
    value.value = 200
    await nextTick()
    expect(display.value).toBe(100)
  })

  it('should jump straight to the new value when the snap signal changed first', async () => {
    const value = ref<number | null>(100)
    const platforms = ref(['Lightyear'])
    const display = useNumberTransition(value, platforms)
    platforms.value = ['Lightyear', 'Lightyear Business']
    await nextTick()
    value.value = 200
    await nextTick()
    expect(display.value).toBe(200)
  })

  it('should dont jump straight to the change that follows a consumed snap', async () => {
    const value = ref<number | null>(100)
    const platforms = ref(['Lightyear'])
    const display = useNumberTransition(value, platforms)
    platforms.value = []
    await nextTick()
    value.value = 200
    await nextTick()
    value.value = 300
    await nextTick()
    expect(display.value).toBe(200)
  })

  it('should cancel a running animation when the owning scope is disposed', async () => {
    const cancelFrame = vi.spyOn(globalThis, 'cancelAnimationFrame')
    const value = ref<number | null>(100)
    const scope = effectScope()
    scope.run(() => useNumberTransition(value))
    value.value = 200
    await nextTick()
    scope.stop()
    expect(cancelFrame).toHaveBeenCalled()
  })
})
