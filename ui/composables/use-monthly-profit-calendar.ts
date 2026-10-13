import { computed, ref, watch } from 'vue'
import { monthOn, type MonthlyProfit } from '../services/monthly-profit'

export const useMonthlyProfitCalendar = (props: { calendar: MonthlyProfit | null }) => {
  const selectedKey = ref('')
  const previewKey = ref<string | null>(null)
  const announcement = ref('')
  const month = computed(() =>
    props.calendar ? monthOn(props.calendar, selectedKey.value) : undefined
  )
  const preview = computed(() =>
    props.calendar && previewKey.value ? monthOn(props.calendar, previewKey.value) : undefined
  )

  watch(
    () => props.calendar,
    current => {
      previewKey.value = null
      announcement.value = ''
      if (!month.value) selectedKey.value = current?.months[current.months.length - 1]?.key ?? ''
    },
    { immediate: true }
  )

  const select = (key: string) => {
    previewKey.value = null
    const target = props.calendar && monthOn(props.calendar, key)
    if (!target || key === selectedKey.value) return
    selectedKey.value = key
    announcement.value = target.name
  }
  return { selectedKey, previewKey, announcement, month, preview, select }
}
