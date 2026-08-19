import { ref, watch, type Ref } from 'vue'

const FLASH_DURATION = 3000
const DEFAULT_THRESHOLD = 0.001

export function useFlashOnChange(
  value: Ref<number | null | undefined>,
  threshold: number = DEFAULT_THRESHOLD
): Ref<string> {
  const flashClass = ref('')
  let previous = value.value ?? null
  let timeoutId: ReturnType<typeof setTimeout> | null = null

  watch(value, newValue => {
    if (newValue === null || newValue === undefined) return

    if (previous === null) {
      previous = newValue
      return
    }

    const delta = newValue - previous
    previous = newValue
    if (Math.abs(delta) <= threshold) return

    flashClass.value = delta > 0 ? 'value-increase' : 'value-decrease'

    if (timeoutId !== null) clearTimeout(timeoutId)
    timeoutId = setTimeout(() => {
      flashClass.value = ''
      timeoutId = null
    }, FLASH_DURATION)
  })

  return flashClass
}
