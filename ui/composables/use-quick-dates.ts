import { ref, watch, nextTick, type Ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'

export type QuickDatePreset =
  | 'today'
  | 'last7Days'
  | 'thisWeek'
  | 'lastWeek'
  | 'last30Days'
  | 'thisMonth'
  | 'lastMonth'
  | 'thisYear'
  | 'lastYear'

export interface QuickDateOption {
  preset: QuickDatePreset
  label: string
}

export const QUICK_DATE_OPTIONS: QuickDateOption[] = [
  { preset: 'today', label: 'Today' },
  { preset: 'last7Days', label: 'Last 7 Days' },
  { preset: 'thisWeek', label: 'This Week' },
  { preset: 'lastWeek', label: 'Last Week' },
  { preset: 'last30Days', label: 'Last 30 Days' },
  { preset: 'thisMonth', label: 'This Month' },
  { preset: 'lastMonth', label: 'Last Month' },
  { preset: 'thisYear', label: 'This Year' },
  { preset: 'lastYear', label: 'Last Year' },
]

export interface UseQuickDatesOptions {
  fromDateKey: string
  untilDateKey: string
  selectedQuickDateKey: string
  onDateSet?: () => void
}

export interface UseQuickDatesReturn {
  fromDate: Ref<string>
  untilDate: Ref<string>
  selectedQuickDate: Ref<string>
  setQuickDate: (preset: QuickDatePreset) => void
  clearDates: () => void
  formatDateToString: (date: Date) => string
}

export function formatDateToString(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function calculateDateRange(preset: QuickDatePreset): { from: Date; until: Date } {
  const today = new Date()

  switch (preset) {
    case 'today':
      return { from: today, until: today }

    case 'last7Days': {
      const sevenDaysAgo = new Date(today)
      sevenDaysAgo.setDate(today.getDate() - 6)
      return { from: sevenDaysAgo, until: today }
    }

    case 'thisWeek': {
      const dayOfWeek = today.getDay()
      const monday = new Date(today)
      monday.setDate(today.getDate() - (dayOfWeek === 0 ? 6 : dayOfWeek - 1))
      const sunday = new Date(monday)
      sunday.setDate(monday.getDate() + 6)
      return { from: monday, until: sunday }
    }

    case 'lastWeek': {
      const dayOfWeek = today.getDay()
      const lastMonday = new Date(today)
      lastMonday.setDate(today.getDate() - (dayOfWeek === 0 ? 6 : dayOfWeek - 1) - 7)
      const lastSunday = new Date(lastMonday)
      lastSunday.setDate(lastMonday.getDate() + 6)
      return { from: lastMonday, until: lastSunday }
    }

    case 'last30Days': {
      const thirtyDaysAgo = new Date(today)
      thirtyDaysAgo.setDate(today.getDate() - 29)
      return { from: thirtyDaysAgo, until: today }
    }

    case 'thisMonth': {
      const firstDay = new Date(today.getFullYear(), today.getMonth(), 1)
      const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0)
      return { from: firstDay, until: lastDay }
    }

    case 'lastMonth': {
      const firstDay = new Date(today.getFullYear(), today.getMonth() - 1, 1)
      const lastDay = new Date(today.getFullYear(), today.getMonth(), 0)
      return { from: firstDay, until: lastDay }
    }

    case 'thisYear': {
      const firstDay = new Date(today.getFullYear(), 0, 1)
      const lastDay = new Date(today.getFullYear(), 11, 31)
      return { from: firstDay, until: lastDay }
    }

    case 'lastYear': {
      const firstDay = new Date(today.getFullYear() - 1, 0, 1)
      const lastDay = new Date(today.getFullYear() - 1, 11, 31)
      return { from: firstDay, until: lastDay }
    }
  }
}

export function getLabelForPreset(preset: QuickDatePreset): string {
  const option = QUICK_DATE_OPTIONS.find(o => o.preset === preset)
  return option?.label ?? ''
}

export function useQuickDates(options: UseQuickDatesOptions): UseQuickDatesReturn {
  const fromDate = useLocalStorage<string>(options.fromDateKey, '')
  const untilDate = useLocalStorage<string>(options.untilDateKey, '')
  const selectedQuickDate = useLocalStorage<string>(options.selectedQuickDateKey, '')
  const manualDateChange = ref(false)

  watch([fromDate, untilDate], () => {
    if (!manualDateChange.value) {
      selectedQuickDate.value = ''
    }
  })

  const setQuickDate = (preset: QuickDatePreset) => {
    manualDateChange.value = true
    const range = calculateDateRange(preset)
    fromDate.value = formatDateToString(range.from)
    untilDate.value = formatDateToString(range.until)
    selectedQuickDate.value = getLabelForPreset(preset)
    options.onDateSet?.()
    nextTick(() => {
      manualDateChange.value = false
    })
  }

  const clearDates = () => {
    fromDate.value = ''
    untilDate.value = ''
    selectedQuickDate.value = ''
  }

  return {
    fromDate,
    untilDate,
    selectedQuickDate,
    setQuickDate,
    clearDates,
    formatDateToString,
  }
}
