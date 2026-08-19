import { describe, it, expect } from 'vitest'
import { ref, nextTick } from 'vue'
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

  it('should roll from the previous value rather than from zero', async () => {
    const value = ref<number | null>(null)
    const display = useNumberTransition(value)
    value.value = 1000
    await nextTick()
    value.value = 1001
    await nextTick()
    expect(display.value).toBe(1000)
  })
})
