const DAY_MS = 86400000
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
export const MONTHS = [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
]
const FULL_MONTH_FORMAT = new Intl.DateTimeFormat('en-US', {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

const toIsoDate = (utcMs: number): string => new Date(utcMs).toISOString().slice(0, 10)

export const addDays = (date: string, days: number): string =>
  toIsoDate(Date.parse(date) + days * DAY_MS)

export const daysBetween = (from: string, to: string): number =>
  (Date.parse(to) - Date.parse(from)) / DAY_MS

export const monthsBefore = (date: string, months: number): string => {
  const source = new Date(date)
  const year = source.getUTCFullYear()
  const targetMonth = source.getUTCMonth() - months
  const lastDay = new Date(Date.UTC(year, targetMonth + 1, 0)).getUTCDate()
  return toIsoDate(Date.UTC(year, targetMonth, Math.min(source.getUTCDate(), lastDay)))
}

export const addMonths = (month: string, count: number): string => {
  const start = new Date(month)
  return toIsoDate(Date.UTC(start.getUTCFullYear(), start.getUTCMonth() + count)).slice(0, 7)
}

export const lastDayOfMonth = (month: string): string => addDays(`${addMonths(month, 1)}-01`, -1)

export const mondayIndex = (date: string): number => (new Date(date).getUTCDay() + 6) % 7

export const monthName = (date: string): string => MONTHS[new Date(date).getUTCMonth()]

export const formatDayMonth = (date: string): string =>
  `${new Date(date).getUTCDate()} ${monthName(date)}`

export const formatWeekdayDate = (date: string): string =>
  `${WEEKDAYS[mondayIndex(date)]} ${formatDayMonth(date)}`

export const formatFullDate = (date: string): string =>
  `${formatWeekdayDate(date)} ${new Date(date).getUTCFullYear()}`

export const formatDayRange = (first: string, last: string): string => {
  const firstYear = new Date(first).getUTCFullYear()
  const lastYear = new Date(last).getUTCFullYear()
  const start =
    firstYear === lastYear ? formatDayMonth(first) : `${formatDayMonth(first)} ${firstYear}`
  return `${start} – ${formatDayMonth(last)} ${lastYear}`
}

export const formatMonth = (month: string): string =>
  `${monthName(month)} ${new Date(month).getUTCFullYear()}`

export const formatFullMonth = (month: string): string => FULL_MONTH_FORMAT.format(new Date(month))

export const formatMonthSpan = (first: string, last: string): string => {
  if (first === last) return formatMonth(last)
  const sameYear = new Date(first).getUTCFullYear() === new Date(last).getUTCFullYear()
  return `${sameYear ? monthName(first) : formatMonth(first)} – ${formatMonth(last)}`
}
