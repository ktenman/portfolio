const DAY_MS = 86400000
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

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
