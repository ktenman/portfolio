import type { PoffFilm, TextPart } from '../models/generated/domain-models'
import { sortFilms } from './poff-films'

export interface PoffPerson {
  name: string
  role: string
  film: PoffFilm
}

const COMPANIES = new Set([
  'Production',
  'Co production',
  'World sales',
  'Distribution',
  'Sales agent',
  'Broadcaster',
])
const WORD = /[\p{L}\p{N}]+/gu
const BY_NAME = new Intl.Collator('en').compare
const BY_TITLE = new Intl.Collator('et').compare
const RANKS: Record<string, number> = { Director: 0, Cast: 1 }

const fold = (text: string) => text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()

const words = (...texts: string[]) => texts.flatMap(text => fold(text).match(WORD) ?? [])

const matches = (terms: string[], index: string[]) =>
  terms.length > 0 && terms.every(term => index.some(word => word.startsWith(term)))

interface Indexed {
  words: string[]
  people: (Omit<PoffPerson, 'film'> & { words: string[] })[]
}

const indexes = new WeakMap<PoffFilm, Indexed>()

const build = (film: PoffFilm): Indexed => {
  const people = film.credits
    .filter(credit => !COMPANIES.has(credit.role))
    .flatMap(credit => credit.names.map(name => ({ name, role: credit.role, words: words(name) })))
  return {
    people,
    words: [
      ...words(film.title, film.englishTitle, film.originalTitle ?? ''),
      ...people.flatMap(person => person.words),
      ...words(...film.countries, ...film.genres, ...film.programmes),
      ...words(...film.screenings.map(screening => screening.cinema)),
    ],
  }
}

const indexed = (film: PoffFilm) => {
  const index = indexes.get(film) ?? build(film)
  indexes.set(film, index)
  return index
}

export const searchFilms = (films: PoffFilm[], query: string, now: Date): PoffFilm[] => {
  const terms = words(query)
  return sortFilms(
    films.filter(film => matches(terms, indexed(film).words)),
    'top',
    now
  )
}

const rank = (role: string) => RANKS[role] ?? 2

export const searchPeople = (films: PoffFilm[], query: string): PoffPerson[] => {
  const terms = words(query)
  const found = films
    .flatMap(film =>
      indexed(film)
        .people.filter(person => matches(terms, person.words))
        .map(({ name, role }) => ({ name, role, film }))
    )
    .sort(
      (a, b) =>
        rank(a.role) - rank(b.role) ||
        BY_NAME(a.name, b.name) ||
        BY_TITLE(a.film.title, b.film.title)
    )
  const first = new Map<string, PoffPerson>()
  found.forEach(person => {
    if (!first.has(person.name)) first.set(person.name, person)
  })
  return [...first.values()]
}

const prefix = (word: string, term: string) => {
  const length = [...word].findIndex((_, i) => fold(word.slice(0, i + 1)).length >= term.length)
  return length < 0 ? word.length : length + 1
}

export const highlight = (text: string, query: string): TextPart[] => {
  const terms = words(query)
  const parts: TextPart[] = []
  let last = 0
  for (const match of text.matchAll(WORD)) {
    const term = terms.find(t => fold(match[0]).startsWith(t))
    if (!term) continue
    const end = match.index + prefix(match[0], term)
    parts.push(
      { text: text.slice(last, match.index), mark: false },
      { text: text.slice(match.index, end), mark: true }
    )
    last = end
  }
  parts.push({ text: text.slice(last), mark: false })
  return parts.filter(part => part.text)
}
