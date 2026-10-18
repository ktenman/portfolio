import { API_ENDPOINTS } from '../../constants/api'
import { contexts, recording, song, transcript } from '../../components/radio/radio-fixture'
import {
  CutState,
  DayOfWeek,
  RecordingState,
  TranscriptState,
} from '../../models/generated/domain-models'
import { apiRoute, type RouteStub } from './stub'

const NAME = '2026-10-17'

const RECORDINGS = [
  recording({
    name: '2026-10-24',
    status: {
      state: RecordingState.RECORDING,
      programmes: ['Keskpäevatund'],
      startedAt: Date.UTC(2026, 9, 24, 8, 55, 0) / 1000,
      seconds: 1260,
      parts: [{ file: 'raw.mp3', offsetSeconds: 0, sha256: null }],
    },
    sizeBytes: 40_300_000,
    transcribed: false,
    editList: null,
    episodeBytes: null,
    cuts: [],
    timeline: [],
    transcriptState: TranscriptState.RECORDING,
    cutState: CutState.NONE,
  }),
  recording(),
]

const WEEKDAYS = [
  DayOfWeek.MONDAY,
  DayOfWeek.TUESDAY,
  DayOfWeek.WEDNESDAY,
  DayOfWeek.THURSDAY,
  DayOfWeek.FRIDAY,
]

const PROGRAMMES = [
  {
    id: 175,
    name: 'Keskpäevatund',
    description: 'Nädala tähtsamad teemad poliitikute ja arvamusliidritega',
    recordEvery: true,
    slots: [{ days: [DayOfWeek.SATURDAY], start: '12:00:00', end: '13:00:00' }],
    repeats: [{ days: [DayOfWeek.SUNDAY], start: '01:00:00', end: '02:00:00' }],
    recordings: 2,
  },
  {
    id: 231,
    name: 'Tarkade klubi',
    description: null,
    recordEvery: false,
    slots: [{ days: [DayOfWeek.SATURDAY], start: '13:00:00', end: '14:00:00' }],
    repeats: [],
    recordings: 0,
  },
  {
    id: 12,
    name: 'Hommikuprogramm',
    description: 'Uudised, ilm ja külalised igal tööpäeval',
    recordEvery: false,
    slots: [{ days: WEEKDAYS, start: '06:00:00', end: '10:00:00' }],
    repeats: [],
    recordings: 0,
  },
]

const SATURDAY_ONE = Date.UTC(2026, 9, 24, 10, 0, 0) / 1000

const STATION = {
  onAir: { name: 'Tarkade klubi', start: SATURDAY_ONE, end: SATURDAY_ONE + 3600, recorded: false },
  next: {
    name: 'Ilmaparandaja',
    start: SATURDAY_ONE + 3600,
    end: SATURDAY_ONE + 7200,
    recorded: false,
  },
  capture: {
    name: '2026-10-31-keskpaevatund',
    programmes: ['Keskpäevatund'],
    start: Date.UTC(2026, 9, 31, 9, 55, 0) / 1000,
    end: Date.UTC(2026, 9, 31, 11, 5, 0) / 1000,
  },
  freeBytes: 364_000_000_000,
  hours: 3159,
}

export const stubRadio: RouteStub = async page => {
  await page.route(/\/api\/radio\/[^/]+\/[^/]+\.(mp3|m4a)/, route => route.abort())
  await page.route(apiRoute(API_ENDPOINTS.RADIO), route => route.fulfill({ json: RECORDINGS }))
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/programmes`), route =>
    route.fulfill({ json: PROGRAMMES })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/station`), route =>
    route.fulfill({ json: STATION })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/${NAME}/cuts`), route =>
    route.fulfill({ json: contexts() })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/${NAME}/transcript`), route =>
    route.fulfill({ json: [transcript()[0], song(), ...transcript().slice(1)] })
  )
}
