import { ref, watch, type Ref } from 'vue'

const TRANSITION_DURATION = 3000

export function useNumberTransition(value: Ref<number | null | undefined>) {
  const displayValue = ref<number>(value.value ?? 0)
  let animationId: number | null = null

  const easeOutQuart = (t: number): number => 1 - Math.pow(1 - t, 4)

  watch(value, (newValue, oldValue) => {
    if (animationId !== null) {
      cancelAnimationFrame(animationId)
    }

    const start = oldValue ?? 0
    const end = newValue ?? 0
    const startTime = performance.now()

    const animate = (currentTime: number) => {
      const elapsed = currentTime - startTime
      const progress = Math.min(elapsed / TRANSITION_DURATION, 1)
      const easedProgress = easeOutQuart(progress)

      displayValue.value = start + (end - start) * easedProgress

      if (progress < 1) {
        animationId = requestAnimationFrame(animate)
      } else {
        animationId = null
      }
    }

    animationId = requestAnimationFrame(animate)
  })

  return displayValue
}
