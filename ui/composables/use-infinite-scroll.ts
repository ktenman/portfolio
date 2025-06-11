import { onMounted, onUnmounted, Ref, unref } from 'vue'

interface UseInfiniteScrollOptions {
  threshold?: number
  rootMargin?: string
  enabled?: Ref<boolean> | boolean
}

export function useInfiniteScroll(
  target: Ref<HTMLElement | null> | HTMLElement | null,
  callback: () => void | Promise<void>,
  options: UseInfiniteScrollOptions = {}
) {
  const { threshold = 0.1, rootMargin = '100px', enabled = true } = options

  let observer: IntersectionObserver | null = null
  let isLoading = false

  const handleIntersect = async (entries: IntersectionObserverEntry[]) => {
    const isEnabled = unref(enabled)

    if (!isEnabled || isLoading) return

    const entry = entries[0]
    if (entry && entry.isIntersecting) {
      isLoading = true
      try {
        await callback()
      } finally {
        isLoading = false
      }
    }
  }

  const start = () => {
    const element = unref(target)
    if (!element) return

    observer = new IntersectionObserver(handleIntersect, {
      threshold,
      rootMargin,
    })

    observer.observe(element)
  }

  const stop = () => {
    if (observer) {
      observer.disconnect()
      observer = null
    }
  }

  onMounted(() => {
    start()
  })

  onUnmounted(() => {
    stop()
  })

  return {
    start,
    stop,
  }
}
