import { computed, type Ref } from 'vue'

const TRUNCATION_TOLERANCE_DAYS = 7

const toDateString = (date: Date): string => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

const daysBetween = (a: string, b: string): number =>
  Math.abs(new Date(a).getTime() - new Date(b).getTime()) / 86_400_000

function resolveRequestedStart(period: string): string {
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth()
  const d = now.getDate()
  const upper = period.toUpperCase()
  const yearMatch = upper.match(/^(\d)Y$/)
  if (yearMatch) return toDateString(new Date(y - +yearMatch[1], m, d))
  if (upper === '1M') return toDateString(new Date(y, m - 1, d))
  if (upper === '6M') return toDateString(new Date(y, m - 6, d))
  if (upper === 'YTD') return toDateString(new Date(y, 0, 1))
  if (upper === 'MAX') return '2000-01-01'
  return toDateString(new Date(y - 1, m, d))
}

export function useTruncationDetection(
  data: Ref<{ startDate: string } | null | undefined>,
  period: Ref<string>,
  periods: readonly string[]
) {
  const isTruncated = computed(() => {
    if (!data.value) return false
    const requested = resolveRequestedStart(period.value)
    return (
      data.value.startDate > requested &&
      daysBetween(data.value.startDate, requested) > TRUNCATION_TOLERANCE_DAYS
    )
  })

  const cappedPeriods = computed<Set<string>>(() => {
    if (!data.value || !isTruncated.value) return new Set()
    const dataStart = data.value.startDate
    return new Set(
      periods.filter(p => {
        const start = resolveRequestedStart(p)
        return start < dataStart && daysBetween(dataStart, start) > TRUNCATION_TOLERANCE_DAYS
      })
    )
  })

  return { isTruncated, cappedPeriods }
}
