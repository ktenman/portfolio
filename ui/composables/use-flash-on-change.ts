import { watch, type Ref } from 'vue'
import { refAutoReset } from '@vueuse/core'

const FLASH_DURATION = 3000
const THRESHOLD = 0.001

export function useFlashOnChange(
  value: Ref<number | null | undefined>,
  snapOn?: Ref<unknown>
): Ref<string> {
  const flashClass = refAutoReset('', FLASH_DURATION)
  let lastSnapOn = snapOn?.value

  watch(value, (newValue, oldValue) => {
    const snapped = snapOn?.value !== lastSnapOn
    lastSnapOn = snapOn?.value

    if (newValue == null || oldValue == null || snapped) return

    const delta = newValue - oldValue
    if (Math.abs(delta) <= THRESHOLD) return

    flashClass.value = delta > 0 ? 'value-increase' : 'value-decrease'
  })

  return flashClass
}
