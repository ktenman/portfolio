import { watch, type Ref } from 'vue'
import { refAutoReset } from '@vueuse/core'

const FLASH_DURATION = 3000
const THRESHOLD = 0.001

export function useFlashOnChange(value: Ref<number | null | undefined>): Ref<string> {
  const flashClass = refAutoReset('', FLASH_DURATION)

  watch(value, (newValue, oldValue) => {
    if (newValue == null || oldValue == null) return

    const delta = newValue - oldValue
    if (Math.abs(delta) <= THRESHOLD) return

    flashClass.value = delta > 0 ? 'value-increase' : 'value-decrease'
  })

  return flashClass
}
