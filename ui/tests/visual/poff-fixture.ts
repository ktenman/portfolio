import { API_ENDPOINTS } from '../../constants/api'
import type { PoffCatalogueResponse, PoffFilm } from '../../models/generated/domain-models'
import { film, screening } from '../../components/poff/poff-fixture'
import { apiRoute, type RouteStub } from './stub'

const PLAZA = 'Apollo Kino Plaza'
const SOLARIS = 'Apollo Kino Solaris'
const ARTIS = 'Kino Artis'
const SOPRUS = 'Kino Sõprus'

type Showing = [
  local: string,
  minutes: number,
  title: string,
  english: string,
  genres: string[],
  cinema: string,
  hall: string,
  extra?: Partial<PoffFilm> & {
    qa?: number
    soldOut?: boolean
    rating?: [number, number]
    day?: string
  },
]

const SHOWINGS: Showing[] = [
  ['10:00', 70, 'Laste lühifilmid', "Children's Shorts", ['Short films'], ARTIS, 'Hall 1'],
  [
    '12:30',
    104,
    'Valge laev',
    'The White Ship',
    ['Drama'],
    PLAZA,
    '3rd Hall',
    { rating: [6.8, 340] },
  ],
  ['14:00', 118, 'Kevade', 'Spring', ['Drama'], SOPRUS, 'Main hall', { rating: [7.9, 12000] }],
  ['15:15', 88, 'Mere ääres', 'By the Sea', ['Documentary'], SOLARIS, '4th Hall'],
  [
    '16:30',
    96,
    'Talvine teekond',
    'Winter Journey',
    ['Drama'],
    PLAZA,
    '5th Lux Hall',
    { rating: [7.1, 88] },
  ],
  [
    '17:45',
    96,
    'Ameerika arst',
    'American Doctor',
    ['Best of Docs'],
    PLAZA,
    '1st Lux Hall',
    { rating: [8.5, 1210] },
  ],
  [
    '18:00',
    106,
    'Shorts Rahvuslik võistlusprogramm 1',
    'Shorts National Competition 1',
    ['Short films'],
    ARTIS,
    'Hall 1',
    { qa: 15 },
  ],
  [
    '18:00',
    128,
    'Kõlakamber',
    'The Echo Chamber',
    ['Drama'],
    PLAZA,
    '2nd Hall',
    { rating: [5.4, 312] },
  ],
  [
    '18:00',
    98,
    'Pärast tormi',
    'Memory from the Sea',
    ['Drama', 'Observational documentary'],
    PLAZA,
    '8th Hall',
    { qa: 20, premiere: 'World premiere' },
  ],
  [
    '18:30',
    100,
    'Demokraatia maitse',
    'The Taste of Democracy',
    ['Drama', 'Comedy'],
    PLAZA,
    '7th Hall',
    { qa: 20, premiere: 'World premiere' },
  ],
  [
    '19:00',
    92,
    'Fotograaf Cannes’is',
    'A Photographer in Cannes',
    ['Biographical', 'Observational documentary'],
    PLAZA,
    '9th Hall',
    { qa: 20, premiere: 'World premiere', soldOut: true },
  ],
  [
    '19:00',
    100,
    'Metsa tütred',
    'Daughters of the Forest',
    ['Observational documentary'],
    SOLARIS,
    '1st Saku Hall',
    { rating: [8.5, 60] },
  ],
  [
    '19:00',
    107,
    'Tüdruk ilma nimeta',
    'A Girl Unknown',
    ['Drama'],
    'Apollo Kino Tasku',
    '2. Hall',
    { rating: [6.9, 410] },
  ],
  [
    '19:15',
    102,
    'Südames valmis',
    'Brace Your Heart',
    ['Drama'],
    SOPRUS,
    'Main hall',
    { rating: [7.2, 45] },
  ],
  [
    '19:30',
    112,
    'Eluröömu nimel',
    'Joy Will Prevail',
    ['Drama', 'Historical film'],
    SOLARIS,
    '2nd Hall',
    { premiere: 'International premiere', soldOut: true, rating: [8.1, 64] },
  ],
  [
    '20:00',
    140,
    'Armastatu',
    'The Beloved',
    ['Drama'],
    PLAZA,
    '1st Lux Hall',
    { rating: [7.2, 4184] },
  ],
  [
    '20:15',
    96,
    'Burgundia',
    'Burgundy',
    ['Mood film'],
    PLAZA,
    '8th Hall',
    { soldOut: true, rating: [7.6, 20] },
  ],
  [
    '20:45',
    105,
    'Betooni ajalugu',
    'The History of Concrete',
    ['Drama', 'Essay film'],
    PLAZA,
    '2nd Hall',
    { rating: [7.4, 150] },
  ],
  [
    '21:00',
    85,
    'Surma varjus',
    'In the Shadow of Death',
    ['Drama', 'Adventure'],
    PLAZA,
    '9th Hall',
    { rating: [7.4, 900] },
  ],
  ['21:15', 89, 'Late Night Sci-Fi', 'Late Night Sci-Fi', ['Short films'], PLAZA, '6th Hall'],
  [
    '21:30',
    97,
    'Jõgi',
    'River',
    ['Horror', 'Thriller'],
    SOLARIS,
    '4th Hall',
    { rating: [7.0, 2100] },
  ],
  [
    '21:30',
    126,
    'Josephine',
    'Josephine',
    ['Drama', 'Thriller'],
    SOPRUS,
    'Main hall',
    { rating: [8.0, 2300] },
  ],
  [
    '19:00',
    120,
    'Avafilm',
    'Opening Film',
    ['Drama'],
    PLAZA,
    '1st Lux Hall',
    { day: '2026-11-06' },
  ],
  [
    '19:00',
    110,
    'Lõpufilm',
    'Closing Film',
    ['Drama'],
    PLAZA,
    '1st Lux Hall',
    { day: '2026-11-22' },
  ],
]

const instant = (day: string, local: string) => new Date(`${day}T${local}:00+02:00`).toISOString()

const toFilm = (
  [local, minutes, title, english, genres, cinema, hall, extra = {}]: Showing,
  index: number
): PoffFilm => {
  const { qa, soldOut, rating, day = '2026-11-10', ...overrides } = extra
  const start = instant(day, local)
  const id = 7000 + index
  return film(
    id,
    title,
    english,
    [
      screening(id, start, {
        end: new Date(Date.parse(start) + minutes * 60000).toISOString(),
        cinema,
        city: cinema === 'Apollo Kino Tasku' ? 'Tartu' : 'Tallinn',
        hall,
        qa: qa !== undefined,
        qaMinutes: qa ?? null,
        soldOut: soldOut ?? false,
      }),
    ],
    {
      genres,
      minutes,
      imdb: rating ? { id: `tt${id}`, rating: rating[0], votes: rating[1] } : null,
      ...overrides,
    }
  )
}

const CATALOGUE: PoffCatalogueResponse = {
  syncedAt: '2026-11-10T15:32:00Z',
  films: SHOWINGS.map(toFilm),
}

export const stubPoff: RouteStub = async page => {
  await page.clock.setFixedTime(new Date('2026-11-10T15:40:00Z'))
  await page.route(apiRoute(`${API_ENDPOINTS.POFF}/catalogue`), route =>
    route.fulfill({ json: CATALOGUE })
  )
}
