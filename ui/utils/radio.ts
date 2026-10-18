import {
  type KeptSpan,
  type RecordingSummary,
  RecordingState,
} from '../models/generated/domain-models'

const ZONE = 'Europe/Tallinn'

const METHODS: [string, string][] = [
  ['digital silence', 'digivaikus'],
  ['station jingle', 'jaama džingel'],
  ['level dip', 'helitaseme langus'],
  ['transcript only', 'ainult tekst'],
]

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

export const day = (startedAt: number): string =>
  `${WEEKDAY.format(instant(startedAt))} ${DATE.format(instant(startedAt)).slice(0, 5)}`

export const fullDay = (startedAt: number): string => {
  const weekday = WEEKDAY_LONG.format(instant(startedAt))
  return `${weekday[0].toUpperCase()}${weekday.slice(1)}, ${DATE.format(instant(startedAt))}`
}

export const method = (name: string): string =>
  METHODS.find(([key]) => name.startsWith(key))?.[1] ?? name

export const STATES: Record<
  RecordingState,
  { label: string; tone: 'plain' | 'live' | 'warn' | 'error' }
> = {
  [RecordingState.RECORDING]: { label: 'Salvestab', tone: 'live' },
  [RecordingState.CAPTURED]: { label: 'Salvestatud', tone: 'plain' },
  [RecordingState.PARTIAL]: { label: 'Osaline', tone: 'warn' },
  [RecordingState.FAILED]: { label: 'Ebaõnnestus', tone: 'error' },
}

export const textState = (recording: RecordingSummary): string => {
  if (!recording.transcribed)
    return recording.status.state === RecordingState.RECORDING ? 'Salvestamisel' : '—'
  return recording.transcriptCheckedAt ? 'Kontrollitud' : 'Kontrollimata'
}

export const cutState = (recording: RecordingSummary): string => {
  if (recording.failure) return 'Ebaõnnestus'
  if (!recording.editList) return '—'
  if (recording.editList.reviewed) return 'Kinnitatud'
  return `${recording.cuts.length} soovitust`
}

export const span = (recording: RecordingSummary): string => {
  const { startedAt, seconds } = recording.status
  return `${clock(startedAt, 0)}–${clock(startedAt, seconds)}`
}
