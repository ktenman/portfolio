import { computed, ref, watch, type Ref } from 'vue'
import { monthOn, type MonthlyProfit } from '../services/monthly-profit'

export const useMonthlyProfit = (model: Readonly<Ref<MonthlyProfit | null | undefined>>) => {
  const key = ref('')
  const previewKey = ref<string | null>(null)
  const announcement = ref('')

  const month = computed(() => (model.value ? monthOn(model.value, key.value) : undefined))

  const preview = computed(() =>
    model.value && previewKey.value ? monthOn(model.value, previewKey.value) : undefined
  )

  watch(
    model,
    current => {
      if (current && !monthOn(current, key.value))
        key.value = current.months[current.months.length - 1].key
    },
    { immediate: true, flush: 'sync' }
  )

  const select = (next: string) => {
    previewKey.value = null
    const target = model.value && monthOn(model.value, next)
    if (!target || next === key.value) return
    key.value = next
    announcement.value = target.note ? `${target.name}. ${target.note}` : target.name
  }

  return { month, preview, previewKey, announcement, select }
}
