import { computed, shallowRef, toValue, type MaybeRefOrGetter } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import type { RecordingSummary } from '../models/generated/domain-models'
import { radioService } from '../services/api'
import { useToast } from './use-toast'

const DELAY = 3000

interface Unsaved {
  name: string
  removed: number[]
}

const unsaved = shallowRef<Unsaved | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined
let pending: (() => Promise<void>) | null = null

const flush = () => {
  clearTimeout(timer)
  return pending ? pending() : Promise.resolve()
}

window.addEventListener('beforeunload', event => {
  if (!pending) return
  event.preventDefault()
  void flush()
})

export const useRadioDecisions = (recording: MaybeRefOrGetter<RecordingSummary>) => {
  const queryClient = useQueryClient()
  const toast = useToast()

  const mine = computed(() =>
    unsaved.value?.name === toValue(recording).name ? unsaved.value : null
  )
  const removed = computed(
    () =>
      mine.value?.removed ?? toValue(recording).cuts.flatMap((cut, i) => (cut.removed ? [i] : []))
  )
  const waiting = computed(() => mine.value !== null)

  const save = async () => {
    const decided = unsaved.value
    pending = null
    if (!decided) return
    await radioService
      .review(decided.name, { removed: decided.removed })
      .then(() => {
        queryClient.invalidateQueries({ queryKey: ['radio', 'search'] })
        return queryClient.invalidateQueries({ queryKey: ['radio'], exact: true })
      })
      .catch(() => toast.error('Could not save the cuts'))
      .finally(() => {
        if (unsaved.value === decided) unsaved.value = null
      })
  }

  const decide = (list: number[]) => {
    const name = toValue(recording).name
    if (unsaved.value && unsaved.value.name !== name) void flush()
    unsaved.value = { name, removed: list }
    clearTimeout(timer)
    pending = save
    timer = setTimeout(save, DELAY)
  }

  const choose = (index: number, remove: boolean) => {
    const others = removed.value.filter(i => i !== index)
    decide(remove ? [...others, index].sort((a, b) => a - b) : others)
  }

  const reset = () => decide(toValue(recording).cuts.map((_, i) => i))

  return { removed, waiting, choose, reset, flush }
}
