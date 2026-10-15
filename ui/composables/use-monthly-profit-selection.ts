import { computed, ref, watch, type Ref } from 'vue'
import { monthOn, type MonthlyProfit } from '../services/monthly-profit'

export const useMonthlyProfitSelection = (monthly: Ref<MonthlyProfit | null | undefined>) => {
  const selectedKey = ref('')
  const previewKey = ref<string | null>(null)
  const announcement = ref('')

  watch(
    monthly,
    current => {
      const months = current?.months
      if (!months?.length || months.some(candidate => candidate.key === selectedKey.value)) return
      selectedKey.value = months[months.length - 1].key
    },
    { immediate: true, flush: 'sync' }
  )

  const month = computed(() => {
    const months = monthly.value?.months
    if (!months) return undefined
    return (
      months.find(candidate => candidate.key === selectedKey.value) ?? months[months.length - 1]
    )
  })

  const preview = computed(() =>
    monthly.value && previewKey.value ? monthOn(monthly.value, previewKey.value) : undefined
  )

  const select = (key: string) => {
    previewKey.value = null
    const target = monthly.value && monthOn(monthly.value, key)
    if (!target || key === month.value?.key) return
    selectedKey.value = key
    announcement.value = [target.name, ...target.notes].join('. ')
  }

  return { month, preview, previewKey, announcement, select }
}
