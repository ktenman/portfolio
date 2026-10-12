import { computed, ref, watch } from 'vue'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { buildDailyProfitCalendar, dayOn, periodLastDays } from '../services/daily-profit-calendar'
import { formatDayRange } from '../utils/iso-dates'
import { formatDateToString } from './use-quick-dates'
import { useToast } from './use-toast'

export type CalendarHistory = 'idle' | 'loading' | 'loaded'

export const useDailyProfitCalendar = (
  props: { rows: PortfolioSummaryDto[] | undefined; history: CalendarHistory },
  loadHistory: () => void
) => {
  const toast = useToast()
  const selectedDate = ref('')
  const previewDate = ref<string | null>(null)
  const announcement = ref('')
  const back = ref(0)
  const isWaiting = ref(false)

  const lastDays = computed(() => periodLastDays(props.rows ?? []))
  const oldest = computed(() =>
    props.history === 'loaded' ? Math.max(lastDays.value.length - 1, 0) : 0
  )
  const index = computed(() => Math.min(back.value, oldest.value))

  const calendar = computed(() =>
    props.rows
      ? buildDailyProfitCalendar(
          props.rows,
          formatDateToString(new Date()),
          lastDays.value[index.value]
        )
      : null
  )

  const dates = computed(() => {
    const days = calendar.value?.days
    return days ? formatDayRange(days[0].date, days[days.length - 1].date) : ''
  })

  const day = computed(() => calendar.value && dayOn(calendar.value, selectedDate.value))

  const preview = computed(() =>
    calendar.value && previewDate.value ? dayOn(calendar.value, previewDate.value) : undefined
  )

  watch(
    [calendar, index],
    ([current, shown], [, previous]) => {
      const isStep = previous !== undefined && shown !== previous
      announcement.value = isStep && current ? `Daily profit ${dates.value}` : ''
      if (isStep) previewDate.value = null
      const isHeld = !isStep && day.value && day.value.kind !== 'missing'
      if (!current || isHeld) return
      selectedDate.value = (current.best ?? current.days[current.days.length - 1]).date
    },
    { immediate: true }
  )

  const select = (date: string) => {
    previewDate.value = null
    const target = calendar.value && dayOn(calendar.value, date)
    if (!target || date === selectedDate.value) return
    selectedDate.value = date
    announcement.value = target.note ? `${target.name}. ${target.note}` : target.name
  }

  const step = (direction: number) => {
    back.value = Math.max(Math.min(index.value + direction, oldest.value), 0)
  }

  const earlier = () => {
    if (isWaiting.value) return
    loadHistory()
    if (props.history === 'loaded') return step(1)
    isWaiting.value = true
  }

  const later = () => step(-1)

  watch(
    () => props.history,
    state => {
      if (!isWaiting.value || state === 'loading') return
      isWaiting.value = false
      if (state === 'loaded') return step(1)
      toast.error('Could not load earlier days. Press the arrow to try again.')
    }
  )

  const period = computed(() => ({
    dates: dates.value,
    earlierDisabled: props.history === 'loaded' && index.value === oldest.value,
    laterDisabled: index.value === 0,
    busy: isWaiting.value,
  }))

  return { calendar, day, preview, previewDate, announcement, period, select, earlier, later }
}
