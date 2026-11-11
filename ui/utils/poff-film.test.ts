import { describe, expect, it } from 'vitest'
import { film, screening } from '../components/poff/poff-fixture'
import router from '../router/index'
import {
  backTarget,
  credits,
  details,
  facts,
  imdbUrl,
  runtime,
  screeningRows,
  titles,
} from './poff-film'

const now = new Date('2026-11-10T15:00:00Z')
const beloved = film(1, 'Armastatu', 'The Beloved', [], {
  originalTitle: 'El ser querido',
  directors: ['Rodrigo Sorogoyen'],
  countries: ['Spain', 'France'],
  year: 2026,
  minutes: 135,
  programmes: ['Best of Festivals'],
  genres: ['Drama'],
  moods: ['intense', 'heartfelt'],
  languages: ['Spanish'],
  subtitles: ['Estonian', 'English'],
  credits: [
    { role: 'Director', names: ['Rodrigo Sorogoyen'] },
    { role: 'Producer', names: ['Pedro Hernández'] },
    { role: 'Cast', names: ['Javier Bardem', 'Victoria Luengo'] },
    { role: 'World sales', names: ['Film Factory'] },
  ],
})

describe('poff film', () => {
  it('writes a length in hours and minutes', () => {
    expect([runtime(135), runtime(45), runtime(null)]).toEqual(['2h 15m', '45m', ''])
  })

  it('lists the English and original titles beside the Estonian one', () => {
    expect(titles(beloved)).toBe('The Beloved · El ser querido')
  })

  it('leaves out a title that repeats the Estonian one', () => {
    expect(titles(film(1, 'Lõpufilm', 'Lõpufilm', []))).toBe('')
  })

  it('lists the director, countries, year and length', () => {
    expect(facts(beloved)).toBe('Rodrigo Sorogoyen · Spain, France · 2026 · 2h 15m')
  })

  it('links a rating to its IMDb page', () => {
    expect(imdbUrl({ id: 'tt27714581', rating: 7.2, votes: 4184 })).toBe(
      'https://www.imdb.com/title/tt27714581/'
    )
  })

  it('lists the details that have a value, capitalising a list of moods', () => {
    expect(details(beloved)).toEqual([
      ['Programme', 'Best of Festivals'],
      ['Genre', 'Drama'],
      ['Country', 'Spain, France'],
      ['Year', '2026'],
      ['Runtime', '2h 15m'],
      ['Language', 'Spanish'],
      ['Subtitles', 'Estonian, English'],
      ['Mood', 'Intense, heartfelt'],
    ])
  })

  it('puts the main credits first and keeps the rest for all credits', () => {
    expect(credits(beloved)).toEqual({
      main: [
        ['Director', 'Rodrigo Sorogoyen'],
        ['Cast', 'Javier Bardem, Victoria Luengo'],
      ],
      rest: [
        ['Producer', 'Pedro Hernández'],
        ['World sales', 'Film Factory'],
      ],
    })
  })

  it('describes each screening still to come, marking tonight and the next open one', () => {
    const rows = screeningRows(
      film(1, 'A', 'A', [
        screening(3, '2026-11-15T18:00:00Z', { hall: '2nd Hall' }),
        screening(1, '2026-11-10T12:00:00Z'),
        screening(2, '2026-11-10T18:00:00Z', { guests: 'Rodrigo Sorogoyen (Director)' }),
      ]),
      now
    )
    expect(
      rows.map(r => [r.screening.id, r.weekday, r.day, r.range, r.tonight, r.place, r.next])
    ).toEqual([
      [2, 'Tue', '10', '20:00–21:40', true, 'Apollo Kino Plaza · 1st Lux Hall', true],
      [3, 'Sun', '15', '20:00–21:40', false, 'Apollo Kino Plaza · 2nd Hall', false],
    ])
  })

  it('marks the first screening with tickets left as the next one', () => {
    const rows = screeningRows(
      film(1, 'A', 'A', [
        screening(1, '2026-11-10T18:00:00Z', { soldOut: true }),
        screening(2, '2026-11-11T18:00:00Z'),
      ]),
      now
    )
    expect(rows.map(r => r.next)).toEqual([false, true])
  })

  it('goes back to the films view it came from with its filters', () => {
    expect(backTarget(router.resolve('/poff/films?genre=Drama'))).toEqual({
      label: 'Back to films',
      to: '/poff/films?genre=Drama',
      history: true,
    })
  })

  it('goes back to the timetable it came from', () => {
    expect(backTarget(router.resolve('/poff?day=2026-11-14'))).toEqual({
      label: 'Back to timetable',
      to: '/poff?day=2026-11-14',
      history: true,
    })
  })

  it('links to the timetable when the page was opened directly', () => {
    expect(backTarget(null)).toEqual({
      label: 'Back to timetable',
      to: '/poff',
      history: false,
    })
  })

  it('links to the timetable when the page was opened from another film', () => {
    expect(backTarget(router.resolve('/poff/film/the-beloved'))).toEqual({
      label: 'Back to timetable',
      to: '/poff',
      history: false,
    })
  })
})
