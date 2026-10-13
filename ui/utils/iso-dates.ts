const DAY_MS = 86400000
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const FULL_MONTHS = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
]

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

const monthNumber = (key: string): number => Number(key.slice(0, 4)) * 12 + Number(key.slice(5)) - 1

export const monthKey = (date: string): string => date.slice(0, 7)

export const addMonths = (key: string, months: number): string => {
  const total = monthNumber(key) + months
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`
}

export const monthsBetween = (from: string, to: string): number =>
  monthNumber(to) - monthNumber(from)

export const lastDayOfMonth = (key: string): string => addDays(`${addMonths(key, 1)}-01`, -1)

export const formatMonthYear = (key: string): string =>
  `${MONTHS[Number(key.slice(5)) - 1]} ${key.slice(0, 4)}`

export const formatFullMonth = (key: string): string =>
  `${FULL_MONTHS[Number(key.slice(5)) - 1]} ${key.slice(0, 4)}`

export const formatMonthSpan = (first: string, last: string): string => {
  if (first === last) return formatMonthYear(last)
  const isSameYear = first.slice(0, 4) === last.slice(0, 4)
  const start = isSameYear ? MONTHS[Number(first.slice(5)) - 1] : formatMonthYear(first)
  return `${start} – ${formatMonthYear(last)}`
}
