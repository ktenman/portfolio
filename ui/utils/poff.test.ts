import { describe, expect, it } from 'vitest'
import { film, poffCatalogue, screening } from '../components/poff/poff-fixture'
import {
  clock,
  defaultDay,
  festivalDays,
  festivalSummary,
  filmLine,
  fullDate,
  premiere,
  ratingLabel,
  ratingName,
  synced,
  shortCinema,
  splitStarted,
  strongRating,
  tallinnDay,
  ticketUrl,
  timetable,
} from './poff'

const films = poffCatalogue.films
const all = { city: 'Tallinn' as const, rated: false, available: false }

describe('poff', () => {
  it('places a screening at half past midnight on its Tallinn day', () => {
    expect(tallinnDay('2026-11-08T22:30:00Z')).toBe('2026-11-09')
  })

  it('lists every day from the first to the last screening', () => {
    expect(festivalDays(films)).toHaveLength(17)
  })

  it('starts the festival on its first screening day', () => {
    expect(festivalDays(films)[0]).toBe('2026-11-06')
  })

  it('selects today during the festival', () => {
    expect(defaultDay(festivalDays(films), '2026-11-10')).toBe('2026-11-10')
  })

  it('selects the first day before the festival', () => {
    expect(defaultDay(festivalDays(films), '2026-11-01')).toBe('2026-11-06')
  })

  it('selects the last day after the festival', () => {
    expect(defaultDay(festivalDays(films), '2026-11-30')).toBe('2026-11-22')
  })

  it('lists the Tallinn screenings of a day by start time', () => {
    expect(timetable(films, '2026-11-10', all).map(r => r.screening.id)).toEqual([
      11, 13, 12, 14, 17, 15, 18,
    ])
  })

  it('orders screenings at the same time by their Estonian title', () => {
    const same = [
      film(1, 'Õnn', 'Bliss', [screening(1, '2026-11-10T17:00:00Z')]),
      film(2, 'Ärkamine', 'Awakening', [screening(2, '2026-11-10T17:00:00Z')]),
    ]
    expect(timetable(same, '2026-11-10', all).map(r => r.film.title)).toEqual(['Õnn', 'Ärkamine'])
  })

  it('keeps only Tartu screenings for Tartu', () => {
    expect(
      timetable(films, '2026-11-10', { ...all, city: 'Tartu' }).map(r => r.screening.id)
    ).toEqual([19])
  })

  it('keeps films rated seven or more whatever their votes', () => {
    expect(timetable(films, '2026-11-10', { ...all, rated: true }).map(r => r.film.id)).toEqual([
      1, 6, 5, 7,
    ])
  })

  it('hides sold out screenings', () => {
    expect(
      timetable(films, '2026-11-10', { ...all, available: true }).map(r => r.screening.id)
    ).toEqual([11, 13, 12, 15, 18])
  })

  it('moves started screenings under earlier today', () => {
    const rows = timetable(films, '2026-11-10', all)
    expect(splitStarted(rows, new Date('2026-11-10T16:10:00Z')).earlier).toHaveLength(3)
  })

  it('shortens Apollo Kino Plaza to Plaza', () => {
    expect(shortCinema('Apollo Kino Plaza')).toBe('Plaza')
  })

  it('shortens Kino Sõprus to Sõprus', () => {
    expect(shortCinema('Kino Sõprus')).toBe('Sõprus')
  })

  it('shortens Alexela Kontserdimaja to Alexela', () => {
    expect(shortCinema('Alexela Kontserdimaja')).toBe('Alexela')
  })

  it('keeps cinemas without a Kino prefix whole', () => {
    const whole = ['Electric Cinema', 'Alexela Concert Hall', 'Film Museum']
    expect(whole.map(shortCinema)).toEqual(whole)
  })

  it('badges a world premiere', () => {
    expect(premiere(film(1, 'Ööbik', 'Nightingale', [], { premiere: 'World premiere' }))).toBe(
      'World premiere'
    )
  })

  it('does not badge a film in post-production', () => {
    expect(
      premiere(film(1, 'Ööbik', 'Nightingale', [], { premiere: 'Post-production' }))
    ).toBeNull()
  })

  it('spells out a day for screen readers', () => {
    expect(fullDate('2026-11-10')).toBe('Tuesday, 10 November 2026')
  })

  it('sums up the festival', () => {
    expect(festivalSummary(films)).toBe(
      'Black Nights Film Festival · 6–22 Nov 2026 · 10 films · 12 screenings'
    )
  })

  it('sums up a festival without screenings', () => {
    expect(festivalSummary([film(1, 'A', 'A', []), film(2, 'B', 'B', [])])).toBe(
      'Black Nights Film Festival · 2 films · 0 screenings'
    )
  })

  it('names both months of a festival that crosses one', () => {
    const late = [
      film(1, 'A', 'A', [
        screening(1, '2026-10-30T12:00:00Z'),
        screening(2, '2026-11-02T12:00:00Z'),
      ]),
    ]
    expect(festivalSummary(late)).toBe(
      'Black Nights Film Festival · 30 Oct–2 Nov 2026 · 1 film · 2 screenings'
    )
  })

  it('shows the sync time alone on the day it synced', () => {
    expect(synced('2026-11-10T15:32:00Z', '2026-11-10')).toBe('17:32')
  })

  it('shows the sync day when it synced on an earlier day', () => {
    expect(synced('2026-11-09T15:32:00Z', '2026-11-10')).toBe('Mon 9 Nov 17:32')
  })

  it('follows the English title with the genres', () => {
    expect(filmLine(films[2])).toBe('The Echo Chamber · Drama')
  })

  it('leaves out an English title equal to the Estonian one', () => {
    expect(filmLine(films[6])).toBe('Drama, Thriller')
  })

  it('shows a start time in Tallinn time', () => {
    expect(clock('2026-11-10T15:45:00Z')).toBe('17:45')
  })

  it('shows a dash for a film without a rating', () => {
    expect(ratingLabel(null)).toBe('–')
  })

  it('shows a rating with one decimal', () => {
    expect(ratingLabel({ id: 'tt1', rating: 7, votes: 400 })).toBe('7.0')
  })

  it('calls a rating with a hundred votes strong', () => {
    expect(strongRating({ id: 'tt1', rating: 7.2, votes: 100 })).toBe(true)
  })

  it.each([
    ['under one hundred votes', { id: 'tt1', rating: 8.1, votes: 64 }],
    ['without votes', { id: 'tt1', rating: 6.9, votes: null }],
    ['that is missing', { id: 'tt1', rating: null, votes: null }],
  ])('does not call a rating %s strong', (_, imdb) => {
    expect(strongRating(imdb)).toBe(false)
  })

  it('names a rating with its votes', () => {
    expect(ratingName({ id: 'tt1', rating: 7.2, votes: 4184 })).toBe('IMDb 7.2 from 4,184 votes')
  })

  it('names a missing rating', () => {
    expect(ratingName(undefined)).toBe('No IMDb rating')
  })

  it('links tickets to the screening on poff.ee', () => {
    const echo = film(1, 'Kõlakamber', 'The Echo Chamber', [])
    expect(ticketUrl(echo, screening(6688, '2026-11-10T16:00:00Z'))).toBe(
      'https://poff.ee/en/film/the-echo-chamber/#screening_6688'
    )
  })
})
