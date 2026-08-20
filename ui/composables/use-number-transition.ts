import { ref, watch, type Ref } from 'vue'
import { tryOnScopeDispose } from '@vueuse/core'
import { useSnapLatch } from './use-snap-latch'

const TRANSITION_DURATION = 3000

export function useNumberTransition(value: Ref<number | null | undefined>, snapOn?: Ref<unknown>) {
  const displayValue = ref<number>(value.value ?? 0)
  let animationId: number | null = null
  const consumeSnap = useSnapLatch(snapOn)

  const cancel = () => {
    if (animationId === null) return
    cancelAnimationFrame(animationId)
    animationId = null
  }

  const easeOutQuart = (t: number): number => 1 - Math.pow(1 - t, 4)

  watch(value, (newValue, oldValue) => {
    cancel()

    const snapped = consumeSnap()

    if (newValue == null) return

    if (oldValue == null || snapped) {
      displayValue.value = newValue
      return
    }

    const start = oldValue
    const startTime = performance.now()

    const animate = (currentTime: number) => {
      const progress = Math.min((currentTime - startTime) / TRANSITION_DURATION, 1)

      displayValue.value = start + (newValue - start) * easeOutQuart(progress)

      animationId = progress < 1 ? requestAnimationFrame(animate) : null
    }

    animationId = requestAnimationFrame(animate)
  })

  tryOnScopeDispose(cancel)

  return displayValue
}
