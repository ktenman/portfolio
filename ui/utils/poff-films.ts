import type { PoffFilm, PoffScreening } from '../models/generated/domain-models'
import { formatWeekdayDate } from './iso-dates'
import { type City, clock, ratedSeven, shortCinema, strongRating, tallinnDay } from './poff'

export interface FilmFilters {
  showing: boolean
  rated: boolean
  tonight: boolean
  genre: string | null
  programme: string | null
  person: string | null
  city: City | null
}

export type FilmSort = 'top' | 'next' | 'az'

export const SORTS: Record<FilmSort, string> = {
  top: 'Top rated',
  next: 'Next screening',
  az: 'A–Z',
}

export const TOP_GENRES = ['Drama', 'Documentary', 'Biographical', 'Thriller', 'Comedy']

const DOCUMENTARY =
  /^(observational|participatory|poetic|performative|reflexive|expository) documentary$/i

const upcoming = (film: PoffFilm, now: Date): PoffScreening[] =>
  film.screenings
    .filter(s => Date.parse(s.start) > now.getTime())
    .sort((a, b) => a.start.localeCompare(b.start))

const open = (film: PoffFilm, now: Date) => upcoming(film, now).filter(s => !s.soldOut)

const inGenre = (film: PoffFilm, genre: string) =>
  genre === 'Documentary' ? film.genres.some(g => DOCUMENTARY.test(g)) : film.genres.includes(genre)

const keeps = (film: PoffFilm, filters: FilmFilters, now: Date, today: string): boolean => {
  const coming = upcoming(film, now)
  return (
    (!filters.city || film.screenings.length > 0) &&
    (!filters.showing || coming.length > 0) &&
    (!filters.rated || ratedSeven(film)) &&
    (!filters.tonight || coming.some(s => tallinnDay(s.start) === today)) &&
    (!filters.genre || inGenre(film, filters.genre)) &&
    (!filters.programme || film.programmes.includes(filters.programme)) &&
    (!filters.person || film.credits.some(c => c.names.includes(filters.person ?? '')))
  )
}

const inCity = (film: PoffFilm, city: City | null): PoffFilm =>
  city ? { ...film, screenings: film.screenings.filter(s => s.city === city) } : film

export const filterFilms = (films: PoffFilm[], filters: FilmFilters, now: Date): PoffFilm[] => {
  const today = tallinnDay(now.toISOString())
  return films
    .map(film => inCity(film, filters.city))
    .filter(film => keeps(film, filters, now, today))
}

const byTitle = (a: PoffFilm, b: PoffFilm) => a.title.localeCompare(b.title, 'et')

const tier = (film: PoffFilm) => {
  if (strongRating(film.imdb)) return 0
  return film.imdb?.rating == null ? 2 : 1
}

const byRating = (a: PoffFilm, b: PoffFilm) =>
  tier(a) - tier(b) ||
  (b.imdb?.rating ?? 0) - (a.imdb?.rating ?? 0) ||
  (b.imdb?.votes ?? 0) - (a.imdb?.votes ?? 0) ||
  byTitle(a, b)

const nextStart = (film: PoffFilm, now: Date) =>
  Date.parse(open(film, now)[0]?.start ?? '') || Number.MAX_SAFE_INTEGER

export const sortFilms = (films: PoffFilm[], sort: FilmSort, now: Date): PoffFilm[] => {
  if (sort !== 'next') return [...films].sort(sort === 'top' ? byRating : byTitle)
  return films
    .map(film => ({ film, start: nextStart(film, now) }))
    .sort((a, b) => a.start - b.start || byTitle(a.film, b.film))
    .map(({ film }) => film)
}

export const soldOut = (film: PoffFilm, now: Date): boolean =>
  upcoming(film, now).length > 0 && open(film, now).length === 0

export const nextLine = (film: PoffFilm, now: Date): string => {
  if (upcoming(film, now).length === 0) return 'No screenings left'
  const [first, ...others] = open(film, now)
  if (!first) return 'Sold out'
  const day = tallinnDay(first.start)
  const when = day === tallinnDay(now.toISOString()) ? 'Today' : formatWeekdayDate(day)
  const more = others.length ? [`${others.length} more`] : []
  return [`${when} ${clock(first.start)}`, shortCinema(first.cinema), ...more].join(' · ')
}

export const genres = (films: PoffFilm[]): string[] =>
  [...new Set(films.flatMap(f => f.genres))].sort((a, b) => a.localeCompare(b, 'en'))
