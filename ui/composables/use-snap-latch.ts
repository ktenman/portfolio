import type { Ref } from 'vue'

export function useSnapLatch(snapOn?: Ref<unknown>): () => boolean {
  let last = snapOn?.value

  return () => {
    const snapped = snapOn?.value !== last
    last = snapOn?.value
    return snapped
  }
}
