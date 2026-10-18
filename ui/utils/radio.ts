import {
  type AirSlot,
  BoundaryMethod,
  CutState,
  DayOfWeek,
  type KeptSpan,
  type RecordingSummary,
  RecordingState,
  TranscriptState,
} from '../models/generated/domain-models'
import type { BadgeTone } from '../components/radio/radio-badge.vue'

const ZONE = 'Europe/Tallinn'

export const METHODS: Record<BoundaryMethod, string> = {
  [BoundaryMethod.DIGITAL_SILENCE]: 'digivaikus',
  [BoundaryMethod.STATION_JINGLE]: 'jaama džingel',
  [BoundaryMethod.LEVEL_DIP]: 'helitaseme langus',
  [BoundaryMethod.TRANSCRIPT_ONLY]: 'ainult tekst',
}

const DAYS = Object.values(DayOfWeek)
const LETTER = 'ETKNRLP'

const weekdays = (days: DayOfWeek[]): string =>
  days
    .map(d => DAYS.indexOf(d))
    .reduce<number[][]>((runs, i) => {
      const last = runs[runs.length - 1]
      if (last && last[last.length - 1] === i - 1) last.push(i)
      else runs.push([i])
      return runs
    }, [])
    .map(run =>
      run.length > 2
        ? `${LETTER[run[0]]}–${LETTER[run[run.length - 1]]}`
        : run.map(i => LETTER[i]).join(', ')
    )
    .join(', ')

export const airStart = (slot: AirSlot): string =>
  `${weekdays(slot.days)} ${slot.start.slice(0, 5)}`

export const airTime = (slot: AirSlot): string => `${airStart(slot)}–${slot.end.slice(0, 5)}`

export const LINK =
  'inline-flex items-center gap-1.5 rounded-container border border-control-border px-3 py-1.5 text-sm font-medium whitespace-nowrap shadow-control transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep'

export const recordingTime = (keep: KeptSpan[], seconds: number): number => {
  const last = keep[keep.length - 1]?.end ?? seconds
  let left = seconds
  for (const k of keep) {
    if (left < k.end - k.start) return k.start + left
    left -= k.end - k.start
  }
  return last
}

const two = (value: number) => String(value).padStart(2, '0')

export const position = (seconds: number): string => {
  const whole = Math.max(0, Math.floor(seconds))
  const hours = Math.floor(whole / 3600)
  const rest = `${two(Math.floor((whole % 3600) / 60))}:${two(whole % 60)}`
  return hours ? `${hours}:${rest}` : rest.replace(/^0/, '')
}

export const length = (seconds: number): string => {
  const whole = Math.round(seconds)
  if (whole < 60) return `${whole} s`
  const hours = Math.floor(whole / 3600)
  const mins = Math.floor((whole % 3600) / 60)
  if (hours) return `${hours} h ${mins} min`
  return whole % 60 ? `${mins} min ${whole % 60} s` : `${mins} min`
}

export const minutes = (seconds: number): string => `${Math.max(1, Math.round(seconds / 60))} min`

export const megabytes = (bytes: number): string => `${Math.round(bytes / 1_000_000)} MB`

const formatter = (options: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat('et-EE', { timeZone: ZONE, ...options })

const TIME = formatter({ hour: '2-digit', minute: '2-digit' })
const TIME_SECONDS = formatter({ hour: '2-digit', minute: '2-digit', second: '2-digit' })
const DATE = formatter({ day: '2-digit', month: '2-digit', year: 'numeric' })
const WEEKDAY = formatter({ weekday: 'narrow' })
const WEEKDAY_LONG = formatter({ weekday: 'long' })

const instant = (startedAt: number, seconds = 0) => new Date((startedAt + seconds) * 1000)

export const clock = (startedAt: number, seconds: number, withSeconds = false): string =>
  (withSeconds ? TIME_SECONDS : TIME).format(instant(startedAt, seconds))

export const range = (startedAt: number, start: number, end: number, withSeconds = false): string =>
  `${clock(startedAt, start, withSeconds)}–${clock(startedAt, end, withSeconds)}`

export const day = (startedAt: number): string =>
  `${WEEKDAY.format(instant(startedAt))} ${DATE.format(instant(startedAt)).slice(0, 5)}`

export const fullDay = (startedAt: number): string => {
  const weekday = WEEKDAY_LONG.format(instant(startedAt))
  return `${weekday[0].toUpperCase()}${weekday.slice(1)}, ${DATE.format(instant(startedAt))}`
}

export const STATES: Record<RecordingState, { label: string; tone: BadgeTone }> = {
  [RecordingState.RECORDING]: { label: 'Salvestab', tone: 'live' },
  [RecordingState.CAPTURED]: { label: 'Salvestatud', tone: 'plain' },
  [RecordingState.PARTIAL]: { label: 'Osaline', tone: 'warn' },
  [RecordingState.FAILED]: { label: 'Ebaõnnestus', tone: 'error' },
}

const TEXT_STATES: Record<TranscriptState, string> = {
  [TranscriptState.NONE]: '—',
  [TranscriptState.RECORDING]: 'Salvestamisel',
  [TranscriptState.UNCHECKED]: 'Kontrollimata',
  [TranscriptState.CHECKED]: 'Kontrollitud',
}

export const textState = (recording: RecordingSummary): string =>
  TEXT_STATES[recording.transcriptState]

const CUT_STATES: Record<CutState, (recording: RecordingSummary) => string> = {
  [CutState.NONE]: () => '—',
  [CutState.FAILED]: () => 'Ebaõnnestus',
  [CutState.SUGGESTED]: recording => `${recording.cuts.length} soovitust`,
  [CutState.REVIEWED]: () => 'Kinnitatud',
}

export const cutState = (recording: RecordingSummary): string =>
  CUT_STATES[recording.cutState](recording)

export const audioFormat = ({ format }: RecordingSummary): string =>
  format ? `${format.codec.toUpperCase()} ${Math.round(format.bitRate / 1000)} kb/s` : '—'

export const dropNote = ({ drops, status }: RecordingSummary): string => {
  const times = drops === 1 ? 'korra' : `${drops} korda`
  const lost = status.lostSeconds
    ? `${length(status.lostSeconds)} jäi salvestamata`
    : 'midagi ei kadunud'
  return `Voog katkes ${times}, ${lost}`
}

export const span = (recording: RecordingSummary): string =>
  range(recording.status.startedAt, 0, recording.status.seconds)
