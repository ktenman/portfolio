import type { ImdbRating, PoffFilm, PoffScreening } from '../models/generated/domain-models'
import { count } from './formatters'
import { addDays, daysBetween, formatDayMonth, formatWeekdayDate, monthName } from './iso-dates'

export type City = 'Tallinn' | 'Tartu'

export interface TimetableFilters {
  city: City
  rated: boolean
  available: boolean
  genre: string | null
}

export interface TimetableRow {
  film: PoffFilm
  screening: PoffScreening
}

export const HEADING = 'mb-2 text-xl font-semibold'

export const LABEL = 'text-label font-semibold tracking-wide text-brass-deep uppercase'

export const OUTLINED =
  'rounded-control border border-control-border font-medium shadow-control transition-colors hover:bg-brass-wash hover:text-brass-deep'

export const CHIP =
  'platform-btn platform-btn-outline relative md-down:h-8 md-down:before:absolute md-down:before:inset-x-0 md-down:before:top-1/2 md-down:before:h-11 md-down:before:-translate-y-1/2'

const PREMIERES = [
  'World premiere',
  'International premiere',
  'Baltic premiere',
  'Estonian premiere',
]

const ZONE = 'Europe/Tallinn'
const DAY = new Intl.DateTimeFormat('sv-SE', {
  timeZone: ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})
const TIME = new Intl.DateTimeFormat('et-EE', {
  timeZone: ZONE,
  hour: '2-digit',
  minute: '2-digit',
})
const FULL = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'UTC',
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
})

export const tallinnDay = (instant: string): string => DAY.format(new Date(instant))

export const festivalDays = (films: PoffFilm[]): string[] => {
  const days = films.flatMap(f => f.screenings.map(s => tallinnDay(s.start))).sort()
  if (days.length === 0) return []
  const length = daysBetween(days[0], days[days.length - 1]) + 1
  return Array.from({ length }, (_, index) => addDays(days[0], index))
}

export const defaultDay = (days: string[], today: string): string => {
  if (days.includes(today) || days.length === 0) return today
  return today < days[0] ? days[0] : days[days.length - 1]
}

const DOCUMENTARY =
  /^(observational|participatory|poetic|performative|reflexive|expository) documentary$/i

export const inGenre = (film: PoffFilm, genre: string): boolean =>
  genre === 'Documentary' ? film.genres.some(g => DOCUMENTARY.test(g)) : film.genres.includes(genre)

const keeps = (row: TimetableRow, day: string, filters: TimetableFilters): boolean =>
  tallinnDay(row.screening.start) === day &&
  row.screening.city === filters.city &&
  (!filters.rated || ratedSeven(row.film)) &&
  (!filters.available || !row.screening.soldOut) &&
  (!filters.genre || inGenre(row.film, filters.genre))

export const timetable = (
  films: PoffFilm[],
  day: string,
  filters: TimetableFilters
): TimetableRow[] =>
  films
    .flatMap(film => film.screenings.map(screening => ({ film, screening })))
    .filter(row => keeps(row, day, filters))
    .sort(
      (a, b) =>
        a.screening.start.localeCompare(b.screening.start) ||
        a.film.title.localeCompare(b.film.title, 'et')
    )

type HourGroup = { time: string; rows: TimetableRow[] }

export const byStart = (rows: TimetableRow[]): HourGroup[] =>
  rows.reduce<HourGroup[]>((groups, row) => {
    const time = clock(row.screening.start)
    const last = groups[groups.length - 1]
    if (last?.time === time) last.rows.push(row)
    else groups.push({ time, rows: [row] })
    return groups
  }, [])

export const FILTER_LABELS = {
  showing: 'Still showing',
  rated: 'IMDb 7+',
  available: 'Hide sold out',
} as const

export const switchLabels = (
  filters: Partial<Record<keyof typeof FILTER_LABELS, boolean>>,
  keys: (keyof typeof FILTER_LABELS)[]
): string[] => keys.filter(key => filters[key]).map(key => FILTER_LABELS[key])

export const timetableSummary = (
  day: string,
  today: string,
  filters: TimetableFilters,
  screenings: number
): string[] => [
  day === today ? `Today, ${formatWeekdayDate(day)}` : formatWeekdayDate(day),
  filters.city,
  ...switchLabels(filters, ['rated', 'available']),
  ...(filters.genre ? [filters.genre] : []),
  count(screenings, 'screening'),
]

export const splitStarted = (rows: TimetableRow[], now: Date) => ({
  earlier: rows.filter(r => Date.parse(r.screening.start) <= now.getTime()),
  coming: rows.filter(r => Date.parse(r.screening.start) > now.getTime()),
})

export const shortCinema = (cinema: string): string =>
  cinema.replace(/^(Apollo )?Kino /, '').replace(' Kontserdimaja', '')

export const premiere = (film: PoffFilm): string | null =>
  film.premiere && PREMIERES.includes(film.premiere) ? film.premiere : null

export const fullDate = (day: string): string => FULL.format(new Date(`${day}T00:00:00Z`))

export const clock = (instant: string): string => TIME.format(new Date(instant))

export const synced = (instant: string, today: string): string =>
  tallinnDay(instant) === today
    ? clock(instant)
    : `${formatWeekdayDate(tallinnDay(instant))} ${clock(instant)}`

const span = (first: string, last: string): string => {
  const start =
    monthName(first) === monthName(last) ? Number(first.slice(8)) : formatDayMonth(first)
  return `${start}–${formatDayMonth(last)} ${last.slice(0, 4)}`
}

export const festivalSummary = (films: PoffFilm[]): string => {
  const days = festivalDays(films)
  const screenings = films.reduce((sum, f) => sum + f.screenings.length, 0)
  const dates = days.length ? [span(days[0], days[days.length - 1])] : []
  return [
    'Black Nights Film Festival',
    ...dates,
    count(films.length, 'film'),
    count(screenings, 'screening'),
  ].join(' · ')
}

export const filmLine = (film: PoffFilm): string =>
  [film.englishTitle === film.title ? '' : film.englishTitle, film.genres.join(', ')]
    .filter(Boolean)
    .join(' · ')

export const ratedSeven = (film: PoffFilm): boolean => (film.imdb?.rating ?? 0) >= 7

export const strongRating = (imdb?: ImdbRating | null): boolean =>
  imdb?.rating != null && (imdb.votes ?? 0) >= 100

export const ratingLabel = (imdb?: ImdbRating | null): string =>
  imdb?.rating == null ? '–' : imdb.rating.toFixed(1)

export const ratingName = (imdb?: ImdbRating | null): string => {
  if (imdb?.rating == null) return 'No IMDb rating'
  if (imdb.votes == null) return `IMDb ${ratingLabel(imdb)}`
  return `IMDb ${ratingLabel(imdb)} from ${imdb.votes.toLocaleString('en-US')} votes`
}

export const ticketUrl = (film: PoffFilm, screening: PoffScreening): string =>
  `https://poff.ee/en/film/${film.slug}/#screening_${screening.id}`
