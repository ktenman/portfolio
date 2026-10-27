import { API_ENDPOINTS } from '../../constants/api'
import {
  contexts,
  programme,
  radioDay,
  recording,
  song,
  sections,
  transcript,
} from '../../components/radio/radio-fixture'
import {
  CutState,
  CutStep,
  DayOfWeek,
  ProgrammeCutState,
  RecorderState,
  RecordingState,
  TranscriptState,
} from '../../models/generated/domain-models'
import { apiRoute, type RouteStub } from './stub'

const NAME = '2026-10-17'

const capture = (at: string, programmes: string[], seconds = 4200) => ({
  state: RecordingState.CAPTURED,
  programmes,
  startedAt: Date.parse(at) / 1000,
  seconds,
  parts: [{ file: 'raw.mp3', offsetSeconds: 0, sha256: null, durationSeconds: null }],
  lostSeconds: 0,
  slot: null,
})

const searching = {
  editList: null,
  episodeBytes: null,
  cuts: [],
  timeline: [],
  topics: [],
  transcriptState: TranscriptState.TRANSCRIBED,
  cutState: CutState.NONE,
}

const RECORDINGS = [
  recording({
    ...searching,
    name: '2026-10-26',
    programme: 'Digitund',
    status: capture('2026-10-26T08:55:00Z', ['Digitund']),
    failure: { reason: 'No outline read to vote on', failedAt: '2026-10-26T11:20:00Z' },
    cutState: CutState.FAILED,
    programmeCuts: [programme('Digitund', ProgrammeCutState.FAILED)],
  }),
  recording({
    ...searching,
    name: '2026-10-25',
    programme: 'Digitund, Arenduskeskuste minutid',
    status: {
      ...capture('2026-10-25T08:55:00Z', ['Digitund', 'Arenduskeskuste minutid']),
      state: RecordingState.RECORDING,
      seconds: 3180,
    },
    timeline: [{ start: 0, end: 3180, title: null, cut: false, label: null, summary: null }],
    sizeBytes: 101_800_000,
    transcribed: false,
    speech: null,
    sounds: null,
    transcriptState: TranscriptState.RECORDING,
    programmeCuts: [
      programme('Digitund', ProgrammeCutState.WAITING, {
        transcript: { writtenSeconds: 3180, totalSeconds: 4200, percent: 75, secondsLeft: 540 },
      }),
      programme('Arenduskeskuste minutid', ProgrammeCutState.QUEUED, { after: 'Digitund' }),
    ],
  }),
  recording({
    ...searching,
    name: '2026-10-24',
    status: capture('2026-10-24T08:55:00Z', ['Keskpäevatund', 'Tarkade klubi'], 7800),
    programmeCuts: [
      programme('Keskpäevatund', ProgrammeCutState.FINDING, {
        progress: {
          step: CutStep.READ,
          read: 1,
          percent: 36,
          secondsLeft: 70,
          stepSecondsLeft: 50,
        },
      }),
      programme('Tarkade klubi', ProgrammeCutState.QUEUED, { after: 'Keskpäevatund' }),
    ],
  }),
  recording({
    name: '2026-10-23',
    status: capture('2026-10-23T08:55:00Z', ['Keskpäevatund']),
    render: { percent: 57, secondsLeft: 40 },
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
    recordings: 2,
  },
  {
    id: 231,
    name: 'Tarkade klubi',
    description: null,
    recordEvery: false,
    slots: [{ days: [DayOfWeek.SATURDAY], start: '13:00:00', end: '14:00:00' }],
    recordings: 0,
  },
  {
    id: 12,
    name: 'Hommikuprogramm',
    description: 'Uudised, ilm ja külalised igal tööpäeval',
    recordEvery: false,
    slots: [{ days: WEEKDAYS, start: '06:00:00', end: '10:00:00' }],
    recordings: 0,
  },
  {
    id: 300,
    name: 'Neeme Raud. Siin',
    description: null,
    recordEvery: false,
    slots: [{ days: [DayOfWeek.SATURDAY], start: '10:00:00', end: '12:00:00' }],
    recordings: 0,
  },
  {
    id: 301,
    name: 'Meretund',
    description: null,
    recordEvery: false,
    slots: [{ days: [DayOfWeek.SATURDAY], start: '09:00:00', end: '10:00:00' }],
    recordings: 0,
  },
  {
    id: 302,
    name: 'Ilmaparandaja',
    description: null,
    recordEvery: false,
    slots: [{ days: [DayOfWeek.SATURDAY], start: '14:00:00', end: '15:00:00' }],
    recordings: 0,
  },
  {
    id: 303,
    name: 'Naistejutud Kukus',
    description: null,
    recordEvery: false,
    slots: [{ days: [DayOfWeek.SATURDAY], start: '17:00:00', end: '18:00:00' }],
    recordings: 0,
  },
  {
    id: 304,
    name: 'Käsi pulsil',
    description: null,
    recordEvery: false,
    slots: [{ days: [DayOfWeek.SATURDAY], start: '18:00:00', end: '19:00:00' }],
    recordings: 0,
  },
  {
    id: 305,
    name: 'EMALAEV',
    description: null,
    recordEvery: false,
    slots: [{ days: [DayOfWeek.SATURDAY], start: '22:00:00', end: '23:59:00' }],
    recordings: 0,
  },
  {
    id: 306,
    name: 'Kukkuv Õun',
    description: null,
    recordEvery: false,
    slots: [{ days: [DayOfWeek.SATURDAY], start: '20:00:00', end: '21:00:00' }],
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
    name: '2026-10-31-1200-keskpaevatund',
    programmes: ['Keskpäevatund'],
    start: Date.UTC(2026, 9, 31, 9, 59, 30) / 1000,
    end: Date.UTC(2026, 9, 31, 11, 0, 30) / 1000,
    slot: { start: Date.UTC(2026, 9, 31, 10) / 1000, end: Date.UTC(2026, 9, 31, 11) / 1000 },
  },
  freeBytes: 364_000_000_000,
  hours: 3159,
  recorder: RecorderState.WAITING,
  last: { programme: 'Keskpäevatund', end: Date.UTC(2026, 9, 17, 10, 5, 0) / 1000 },
  format: { codec: 'mp3', bitRate: 256_000 },
}

export const stubRadio: RouteStub = async page => {
  await page.route(/\/api\/radio\/[^/]+\/[^/]+\.(mp3|m4a)/, route => route.abort())
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/[^/]+/position`), route =>
    route.fulfill({ json: { seconds: 0 } })
  )
  await page.route(apiRoute(API_ENDPOINTS.RADIO), route => route.fulfill({ json: RECORDINGS }))
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/programmes`), route =>
    route.fulfill({ json: PROGRAMMES })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/station`), route =>
    route.fulfill({ json: STATION })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/schedule`), route => {
    const date = new URL(route.request().url()).searchParams.get('date')
    const today = radioDay()
    const day =
      date && date !== today.date
        ? radioDay({ date, from: today.from + 86_400, until: today.until + 86_400 })
        : today
    const running = {
      name: '2026-10-17-1310-huvitaja',
      programme: 'Huvitaja',
      start: day.now - 600,
      end: day.now,
      recording: true,
    }
    route.fulfill({ json: { ...day, recorded: [...day.recorded, running] } })
  })
  await page.route(/\/api\/radio\/[^/]+\/cuts(\?|$)/, route => route.fulfill({ json: [] }))
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/${NAME}/cuts`), route =>
    route.fulfill({ json: contexts() })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/${NAME}/transcript`), route =>
    route.fulfill({ json: sections([transcript()[0], song(), ...transcript().slice(1)]) })
  )
}

export const stubRadioOnAir: RouteStub = async page => {
  await page.clock.setFixedTime((SATURDAY_ONE + 37 * 60) * 1000)
  await stubRadio(page)
}

export const stubRadioStill: RouteStub = async page => {
  await page.clock.setFixedTime(Date.now())
  await stubRadio(page)
}

export const stubRadioLongText: RouteStub = async page => {
  await stubRadio(page)
  const lines = Array.from({ length: 90 }, (_, i) => ({
    start: 770 + i * 34,
    end: 800 + i * 34,
    text: ` Lõik ${i + 1}: Keskpäevatund arutab päeva olulisemaid teemasid.`,
    words: [],
    split: null,
    music: false,
  }))
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/${NAME}/transcript`), route =>
    route.fulfill({ json: sections(lines) })
  )
}
