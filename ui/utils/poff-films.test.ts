import { describe, expect, it } from 'vitest'
import { film, poffCatalogue, screening } from '../components/poff/poff-fixture'
import { filmCount, filterFilms, genres, nextLine, sortFilms, type FilmFilters } from './poff-films'

const now = new Date('2026-11-10T15:00:00Z')
const none: FilmFilters = {
  showing: false,
  rated: false,
  tonight: false,
  genre: null,
  programme: null,
  person: null,
}
const ids = (films: { id: number }[]) => films.map(f => f.id)
const rated = (id: number, rating: number | null, votes: number | null, title = `Film ${id}`) =>
  film(id, title, title, [], { imdb: rating == null ? null : { id: `tt${id}`, rating, votes } })

describe('poff films', () => {
  it('keeps only films with a screening still to come', () => {
    expect(ids(filterFilms(poffCatalogue.films, { ...none, showing: true }, now))).toEqual([
      1, 2, 3, 4, 5, 6, 7, 8, 9,
    ])
  })

  it('keeps films rated 7 or more', () => {
    expect(ids(filterFilms(poffCatalogue.films, { ...none, rated: true }, now))).toEqual([
      1, 5, 6, 7,
    ])
  })

  it('keeps films with a screening later tonight', () => {
    expect(ids(filterFilms(poffCatalogue.films, { ...none, tonight: true }, now))).toEqual([
      1, 2, 3, 4, 5, 6, 7, 8,
    ])
  })

  it('counts every documentary genre as a documentary but not a fake one', () => {
    const films = [
      film(1, 'A', 'A', [], { genres: ['Poetic documentary'] }),
      film(2, 'B', 'B', [], { genres: ['Fake documentary'] }),
      film(3, 'C', 'C', [], { genres: ['Expository documentary', 'Drama'] }),
    ]
    expect(ids(filterFilms(films, { ...none, genre: 'Documentary' }, now))).toEqual([1, 3])
  })

  it('keeps films of the chosen genre', () => {
    expect(ids(filterFilms(poffCatalogue.films, { ...none, genre: 'Thriller' }, now))).toEqual([7])
  })

  it('keeps films in the chosen programme', () => {
    const films = [
      film(1, 'A', 'A', [], { programmes: ['Best of Docs'] }),
      film(2, 'B', 'B', [], { programmes: ['Official Selection - Competition'] }),
    ]
    expect(ids(filterFilms(films, { ...none, programme: 'Best of Docs' }, now))).toEqual([1])
  })

  it('keeps films that credit the chosen person in any role', () => {
    const films = [
      film(1, 'A', 'A', [], { credits: [{ role: 'Cast', names: ['Tõnu Kark'] }] }),
      film(2, 'B', 'B', [], { credits: [{ role: 'Director', names: ['Elmo Nüganen'] }] }),
    ]
    expect(ids(filterFilms(films, { ...none, person: 'Elmo Nüganen' }, now))).toEqual([2])
  })

  it('puts well-voted films first by rating and votes, then thinly voted ones, then unrated by title', () => {
    const films = [
      rated(1, null, null, 'Öö'),
      rated(2, 7.5, 50),
      rated(3, 8.0, 150),
      rated(4, 8.0, 900),
      rated(5, 9.1, 12),
      rated(6, null, null, 'Aed'),
    ]
    expect(ids(sortFilms(films, 'top', now))).toEqual([4, 3, 5, 2, 6, 1])
  })

  it('sorts by the next screening with a ticket, leaving finished and sold-out films last', () => {
    const films = [
      film(1, 'Läinud', 'Gone', [screening(1, '2026-11-09T18:00:00Z')]),
      film(2, 'Hiljem', 'Later', [screening(2, '2026-11-12T18:00:00Z')]),
      film(3, 'Varsti', 'Soon', [screening(3, '2026-11-10T18:00:00Z')]),
      film(4, 'Täis', 'Full', [screening(4, '2026-11-10T16:00:00Z', { soldOut: true })]),
    ]
    expect(ids(sortFilms(films, 'next', now))).toEqual([3, 2, 1, 4])
  })

  it('sorts by the Estonian title', () => {
    const films = [
      film(1, 'Öö', 'Night', []),
      film(2, 'Zoo', 'Zoo', []),
      film(3, 'Aed', 'Garden', []),
    ]
    expect(ids(sortFilms(films, 'az', now))).toEqual([3, 2, 1])
  })

  it('names the next screening today with how many more there are', () => {
    const shown = film(1, 'A', 'A', [
      screening(1, '2026-11-10T16:00:00Z'),
      screening(2, '2026-11-11T16:00:00Z', { cinema: 'Kino Artis' }),
      screening(3, '2026-11-12T16:00:00Z'),
    ])
    expect(nextLine(shown, now)).toBe('Today 18:00 · Plaza · 2 more')
  })

  it('names a later screening by its day and skips sold-out ones', () => {
    const shown = film(1, 'A', 'A', [
      screening(1, '2026-11-10T16:00:00Z', { soldOut: true }),
      screening(2, '2026-11-19T18:00:00Z', { cinema: 'Kino Sõprus' }),
    ])
    expect(nextLine(shown, now)).toBe('Thu 19 Nov 20:00 · Sõprus')
  })

  it('says sold out when every screening left is sold out', () => {
    const shown = film(1, 'A', 'A', [screening(1, '2026-11-10T16:00:00Z', { soldOut: true })])
    expect(nextLine(shown, now)).toBe('Sold out')
  })

  it('says no screenings are left after the last one has started', () => {
    const shown = film(1, 'A', 'A', [screening(1, '2026-11-10T14:00:00Z')])
    expect(nextLine(shown, now)).toBe('No screenings left')
  })

  it('counts a single film in the singular', () => {
    expect([filmCount(1), filmCount(271)]).toEqual(['1 film', '271 films'])
  })

  it('lists every genre once in alphabetical order', () => {
    expect(genres(poffCatalogue.films).slice(0, 3)).toEqual([
      'Best of Docs',
      'Biographical',
      'Drama',
    ])
  })
})
