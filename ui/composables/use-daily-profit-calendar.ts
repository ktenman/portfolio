import { computed, ref, watch } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../constants'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { buildDailyProfitCalendar, dayOn, periodLastDays } from '../services/daily-profit-calendar'
import { formatDayRange } from '../utils/iso-dates'
import { formatDateToString } from './use-quick-dates'
import { useToast } from './use-toast'

export type CalendarHistory = 'idle' | 'loading' | 'loaded'

const isDate = (date: string | null) =>
  !!date &&
  /^\d{4}-\d{2}-\d{2}$/.test(date) &&
  Number.isFinite(Date.parse(date)) &&
  new Date(date).toISOString().slice(0, 10) === date

export const useDailyProfitCalendar = (
  props: { rows: PortfolioSummaryDto[] | undefined; history: CalendarHistory },
  loadHistory: () => void
) => {
  const toast = useToast()
  const storedDate = useLocalStorage<string | null>(STORAGE_KEYS.SUMMARY_CALENDAR_DAY, null, {
    listenToStorageChanges: false,
    flush: 'sync',
  })
  const selectedDate = ref('')
  const previewDate = ref<string | null>(null)
  const announcement = ref('')
  const back = ref(0)
  const isRestoring = ref(isDate(storedDate.value))
  const waiting = ref<'restore' | 'step' | null>(null)

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

  const remember = (date: string) => {
    selectedDate.value = date
    storedDate.value = date && date !== lastDays.value[0] ? date : null
  }

  const restore = () => {
    if (!props.rows) return
    const current = calendar.value
    const date = storedDate.value
    if (current && date && date <= current.days[0].date && props.history !== 'loaded') {
      if (waiting.value) return
      waiting.value = 'restore'
      loadHistory()
      return
    }
    const found = date && props.rows.some(row => row.date === date)
    const target = found ? Math.max(lastDays.value.filter(last => date <= last).length - 1, 0) : 0
    if (back.value !== target) {
      back.value = target
      return
    }
    isRestoring.value = false
    remember(found ? date : (lastDays.value[0] ?? ''))
  }

  watch(
    [calendar, index, () => props.history],
    ([current, shown], [, previous]) => {
      if (isRestoring.value) return restore()
      const isStep = previous !== undefined && shown !== previous
      announcement.value = isStep && current ? `Daily profit ${dates.value}` : ''
      if (isStep) previewDate.value = null
      const isHeld = !isStep && day.value && day.value.kind !== 'missing'
      if (!current || isHeld) return
      remember(
        (shown > 0 && current.best ? current.best : current.days[current.days.length - 1]).date
      )
    },
    { immediate: true }
  )

  const select = (date: string) => {
    previewDate.value = null
    const target = calendar.value && dayOn(calendar.value, date)
    if (!target || date === selectedDate.value) return
    remember(date)
    announcement.value = target.note ? `${target.name}. ${target.note}` : target.name
  }

  const step = (direction: number) => {
    back.value = Math.max(Math.min(index.value + direction, oldest.value), 0)
  }

  const earlier = () => {
    if (waiting.value) return
    loadHistory()
    if (props.history === 'loaded') return step(1)
    waiting.value = 'step'
  }

  const later = () => step(-1)

  watch(
    () => props.history,
    state => {
      if (!waiting.value || state === 'loading') return
      const wasRestoring = waiting.value === 'restore'
      waiting.value = null
      if (state === 'loaded') return wasRestoring ? undefined : step(1)
      if (wasRestoring) {
        storedDate.value = null
        restore()
      }
      toast.error('Could not load earlier days. Press the arrow to try again.')
    }
  )

  const period = computed(() => ({
    dates: dates.value,
    earlierDisabled: props.history === 'loaded' && index.value === oldest.value,
    laterDisabled: index.value === 0,
    busy: waiting.value !== null,
  }))

  return { calendar, day, preview, previewDate, announcement, period, select, earlier, later }
}
