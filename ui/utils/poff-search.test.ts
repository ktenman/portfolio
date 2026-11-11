import { describe, expect, it } from 'vitest'
import { film, screening } from '../components/poff/poff-fixture'
import { highlight, searchFilms, searchPeople } from './poff-search'

const now = new Date('2026-11-10T15:00:00Z')
const horse = film(1, 'Metsik hobu üheksa', 'Wild Horse Nine', [], {
  imdb: { id: 'tt1', rating: 8.1, votes: 900 },
  credits: [
    { role: 'Director', names: ['Zoe Wilson'] },
    { role: 'Production', names: ['Wildling Pictures'] },
  ],
})
const echo = film(2, 'Kõlakamber', 'The Echo Chamber', [screening(1, '2026-11-12T18:00:00Z')], {
  imdb: { id: 'tt2', rating: 5.4, votes: 300 },
  countries: ['Estonia'],
  programmes: ['Rebels with a Cause'],
  credits: [
    { role: 'Cast', names: ['Olivia Wilde'] },
    { role: 'Director', names: ['Anna Hints'] },
  ],
})
const behemoth = film(3, 'Behemoth!', 'Behemoth!', [], {
  credits: [
    { role: 'Composer', names: ['Ann Wilber'] },
    { role: 'Cast', names: ['Olivia Wilde', 'Adam Willis'] },
  ],
})
const films = [behemoth, echo, horse]

const titles = (query: string) => searchFilms(films, query, now).map(f => f.title)
const names = (query: string) => searchPeople(films, query).map(p => p.name)

describe('poff-search', () => {
  it('finds a film by the start of a word in its English title', () => {
    expect(titles('wil')).toContain('Metsik hobu üheksa')
  })

  it('finds a film without its accents', () => {
    expect(titles('kolakamber')).toEqual(['Kõlakamber'])
  })

  it('finds the films of a director by name', () => {
    expect(titles('anna hints')).toEqual(['Kõlakamber'])
  })

  it('finds a film by country, programme or cinema', () => {
    expect([titles('estonia'), titles('rebels'), titles('plaza')]).toEqual([
      ['Kõlakamber'],
      ['Kõlakamber'],
      ['Kõlakamber'],
    ])
  })

  it('needs every word typed to start a word of the film', () => {
    expect([titles('horse wild'), titles('ild'), titles('wild cat')]).toEqual([
      ['Metsik hobu üheksa'],
      [],
      [],
    ])
  })

  it('leaves out a film matched only by its production company', () => {
    expect(titles('wildling')).toEqual([])
  })

  it('orders films as top rated', () => {
    expect(titles('wil')).toEqual(['Metsik hobu üheksa', 'Kõlakamber', 'Behemoth!'])
  })

  it('lists directors first, then cast, then the rest of the crew, each by name', () => {
    expect(names('wil')).toEqual(['Zoe Wilson', 'Adam Willis', 'Olivia Wilde', 'Ann Wilber'])
  })

  it('lists a person once with their first role and film', () => {
    const wilde = searchPeople(films, 'wilde')
    expect(wilde.map(p => [p.name, p.role, p.film.title])).toEqual([
      ['Olivia Wilde', 'Cast', 'Behemoth!'],
    ])
  })

  it('leaves companies out of people', () => {
    expect(names('wildling')).toEqual([])
  })

  it('marks the typed start of each matching word', () => {
    expect(highlight('Wild Horse Nine', 'wil')).toEqual([
      { text: 'Wil', mark: true },
      { text: 'd Horse Nine', mark: false },
    ])
  })

  it('marks an accented word by its folded start', () => {
    expect(highlight('Kõlakamber', 'kola')).toEqual([
      { text: 'Kõla', mark: true },
      { text: 'kamber', mark: false },
    ])
  })
})
