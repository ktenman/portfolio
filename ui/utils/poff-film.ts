import type { RouteLocationResolved } from 'vue-router'
import type { ImdbRating, PoffFilm } from '../models/generated/domain-models'
import { WEEKDAYS, mondayIndex } from './iso-dates'
import { clock, tallinnDay } from './poff'

const MAIN = ['Director', 'Screenwriter', 'Cinematographer', 'Composer', 'Cast']
const BACK: Record<string, string> = { Poff: 'Back to timetable', PoffFilms: 'Back to films' }

type Row = [string, string]

export const runtime = (minutes: number | null): string => {
  if (!minutes) return ''
  const hours = Math.floor(minutes / 60)
  return hours ? `${hours}h ${minutes % 60}m` : `${minutes}m`
}

export const titles = (film: PoffFilm): string =>
  [...new Set([film.englishTitle, film.originalTitle])]
    .filter(t => t && t !== film.title)
    .join(' · ')

export const facts = (film: PoffFilm): string =>
  [film.directors.join(', '), film.countries.join(', '), film.year, runtime(film.minutes)]
    .filter(Boolean)
    .join(' · ')

export const imdbUrl = (imdb: ImdbRating): string => `https://www.imdb.com/title/${imdb.id}/`

const capital = (text: string): string => text.charAt(0).toUpperCase() + text.slice(1)

export const details = (film: PoffFilm): Row[] =>
  (
    [
      ['Programme', film.programmes.join(', ')],
      ['Genre', film.genres.join(', ')],
      ['Country', film.countries.join(', ')],
      ['Year', film.year?.toString() ?? ''],
      ['Runtime', runtime(film.minutes)],
      ['Language', film.languages.join(', ')],
      ['Subtitles', film.subtitles.join(', ')],
      ['Mood', capital(film.moods.join(', '))],
    ] as Row[]
  ).filter(([, value]) => value)

export const credits = (film: PoffFilm): { main: Row[]; rest: Row[] } => {
  const rows = film.credits
    .filter(c => c.names.length)
    .map(c => [c.role, c.names.join(', ')] as Row)
  return {
    main: rows.filter(([role]) => MAIN.includes(role)),
    rest: rows.filter(([role]) => !MAIN.includes(role)),
  }
}

export const screeningRows = (film: PoffFilm, now: Date) => {
  const today = tallinnDay(now.toISOString())
  const coming = film.screenings
    .filter(s => Date.parse(s.start) > now.getTime())
    .sort((a, b) => a.start.localeCompare(b.start))
  const next = coming.find(s => !s.soldOut)
  return coming.map(s => {
    const day = tallinnDay(s.start)
    return {
      screening: s,
      weekday: WEEKDAYS[mondayIndex(day)],
      day: String(Number(day.slice(8))),
      range: `${clock(s.start)}–${clock(s.end)}`,
      tonight: day === today,
      place: [s.cinema, s.hall].filter(Boolean).join(' · '),
      next: s === next,
    }
  })
}

export const backTarget = (from: RouteLocationResolved | null) => {
  const label = from && BACK[String(from.name)]
  return label
    ? { label, to: from.fullPath, history: true }
    : { label: BACK.Poff, to: '/poff', history: false }
}
