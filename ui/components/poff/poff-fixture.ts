import type {
  ImdbRating,
  PoffCatalogueResponse,
  PoffFilm,
  PoffScreening,
} from '../../models/generated/domain-models'

export const screening = (
  id: number,
  start: string,
  overrides: Partial<PoffScreening> = {}
): PoffScreening => ({
  id,
  start,
  end: new Date(Date.parse(start) + 100 * 60000).toISOString(),
  cinema: 'Apollo Kino Plaza',
  city: 'Tallinn',
  hall: '1st Lux Hall',
  code: null,
  qa: false,
  qaMinutes: null,
  guests: null,
  ticket: id + 500000,
  soldOut: false,
  ...overrides,
})

export const film = (
  id: number,
  title: string,
  englishTitle: string,
  screenings: PoffScreening[],
  overrides: Partial<PoffFilm> = {}
): PoffFilm => ({
  id,
  slug: englishTitle.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
  title,
  englishTitle,
  originalTitle: null,
  premiere: null,
  directors: [],
  countries: [],
  year: 2026,
  minutes: 100,
  programmes: [],
  genres: ['Drama'],
  subjects: [],
  moods: [],
  synopsis: [],
  languages: [],
  subtitles: [],
  credits: [],
  stills: [],
  trailer: null,
  screenings,
  imdb: null,
  ...overrides,
})

const rated = (rating: number, votes: number | null): ImdbRating => ({
  id: `tt${votes ?? 0}`,
  rating,
  votes,
})

export const poffCatalogue: PoffCatalogueResponse = {
  syncedAt: '2026-11-10T15:32:00Z',
  films: [
    film(1, 'Ameerika arst', 'American Doctor', [screening(11, '2026-11-10T15:45:00Z')], {
      genres: ['Best of Docs'],
      imdb: rated(8.5, 1210),
    }),
    film(
      2,
      'Shorts Rahvuslik võistlusprogramm 1',
      'Shorts National Competition 1',
      [
        screening(12, '2026-11-10T16:00:00Z', {
          cinema: 'Kino Artis',
          hall: 'Hall 1',
          qa: true,
          qaMinutes: 15,
        }),
      ],
      { genres: ['Short films'], year: null, minutes: null }
    ),
    film(
      3,
      'Kõlakamber',
      'The Echo Chamber',
      [screening(13, '2026-11-10T16:00:00Z', { hall: '2nd Hall' })],
      {
        imdb: rated(5.4, 312),
      }
    ),
    film(
      4,
      'Fotograaf Cannes’is',
      'A Photographer in Cannes',
      [
        screening(14, '2026-11-10T17:00:00Z', {
          hall: '9th Hall',
          soldOut: true,
          qa: true,
          qaMinutes: 20,
        }),
      ],
      { premiere: 'World premiere', genres: ['Biographical', 'Observational documentary'] }
    ),
    film(
      5,
      'Armastatu',
      'The Beloved',
      [
        screening(15, '2026-11-10T18:00:00Z'),
        screening(16, '2026-11-15T18:00:00Z', { hall: '2nd Hall' }),
      ],
      { imdb: rated(7.2, 4184) }
    ),
    film(
      6,
      'Eluröömu nimel',
      'Joy Will Prevail',
      [
        screening(17, '2026-11-10T17:30:00Z', {
          cinema: 'Apollo Kino Solaris',
          hall: '2nd Hall',
          soldOut: true,
        }),
      ],
      {
        premiere: 'International premiere',
        genres: ['Drama', 'Historical film'],
        imdb: rated(8.1, 64),
      }
    ),
    film(
      7,
      'Josephine',
      'Josephine',
      [screening(18, '2026-11-10T19:30:00Z', { cinema: 'Kino Sõprus', hall: 'Main hall' })],
      { genres: ['Drama', 'Thriller'], imdb: rated(8.0, 2300) }
    ),
    film(
      8,
      'Tüdruk ilma nimeta',
      'A Girl Unknown',
      [
        screening(19, '2026-11-10T17:00:00Z', {
          cinema: 'Apollo Kino Tasku',
          city: 'Tartu',
          hall: '2. Hall',
        }),
        screening(20, '2026-11-08T22:30:00Z', { hall: '6th Hall' }),
      ],
      { imdb: rated(6.9, null) }
    ),
    film(
      9,
      'Hilisõhtu ulme',
      'Late Night Sci-Fi',
      [screening(21, '2026-11-22T20:00:00Z', { hall: '6th Hall' })],
      {
        genres: ['Short films'],
      }
    ),
    film(10, 'Avafilm', 'Opening Film', [
      screening(22, '2026-11-06T17:00:00Z', { hall: '1st Lux Hall' }),
    ]),
  ],
}
