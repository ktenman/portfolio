import type {
  Boundary,
  EditList,
  KeptSpan,
  RecordingSummary,
} from '../models/generated/domain-models'

export type CutKind = 'before' | 'break' | 'after'

export interface Cut {
  kind: CutKind
  start: number
  end: number
  methods: string[]
  review: boolean
}

export interface Segment {
  start: number
  end: number
  title: string
  cut: boolean
}

export const PROGRAMME = 'Keskpäevatund'
const ZONE = 'Europe/Tallinn'
const TOPIC_LEAD = 30

export const CUT_TITLES: Record<CutKind, string> = {
  before: 'Enne saadet',
  break: 'Reklaamipaus',
  after: 'Pärast saadet',
}

const METHODS: [string, string][] = [
  ['digital silence', 'digivaikus'],
  ['station jingle', 'jaama džingel'],
  ['level dip', 'helitaseme langus'],
  ['transcript only', 'ainult tekst'],
]

const cutSpan = (kind: CutKind, total: number, from?: Boundary, to?: Boundary): Cut => {
  const ends = [from, to].filter((b): b is Boundary => b !== undefined)
  return {
    kind,
    start: from?.seconds ?? 0,
    end: to?.seconds ?? total,
    methods: ends.map(b => b.method),
    review: ends.some(b => b.review),
  }
}

export const cuts = (edit: EditList | null, total: number): Cut[] => {
  if (!edit) return []
  const at = new Map(edit.boundaries.map(b => [b.boundary, b]))
  const breaks = edit.boundaries.filter(b => /^break \d+ starts$/.test(b.boundary)).length
  const pauses = Array.from({ length: breaks }, (_, i) =>
    cutSpan('break', total, at.get(`break ${i + 1} starts`), at.get(`break ${i + 1} ends`))
  )
  return [
    cutSpan('before', total, undefined, at.get('show starts')),
    ...pauses,
    cutSpan('after', total, at.get('show ends')),
  ].filter(cut => cut.end - cut.start > 0.01)
}

export const removed = (cut: Cut, keep: KeptSpan[]): boolean => {
  const middle = (cut.start + cut.end) / 2
  return !keep.some(k => k.start <= middle && middle < k.end)
}

export const keepWithout = (gone: Cut[], total: number): KeptSpan[] => {
  const ordered = [...gone].sort((a, b) => a.start - b.start)
  const kept: KeptSpan[] = []
  const cursor = ordered.reduce((from, cut) => {
    if (cut.start > from) kept.push({ start: from, end: cut.start })
    return Math.max(from, cut.end)
  }, 0)
  if (total > cursor) kept.push({ start: cursor, end: total })
  return kept
}

const topicAt = (topics: EditList['topics'], seconds: number): string =>
  topics.filter(t => t.start <= seconds + TOPIC_LEAD).slice(-1)[0]?.title ?? PROGRAMME

const split = (kept: KeptSpan, topics: EditList['topics']): Segment[] => {
  const marks = topics.filter(t => t.start > kept.start + TOPIC_LEAD && t.start < kept.end)
  const starts = [{ start: kept.start, title: topicAt(topics, kept.start) }, ...marks]
  return starts.map((mark, i) => ({
    start: mark.start,
    end: starts[i + 1]?.start ?? kept.end,
    title: mark.title,
    cut: false,
  }))
}

export const segments = (edit: EditList | null, total: number): Segment[] => {
  const found = cuts(edit, total)
  const topics = [...(edit?.topics ?? [])].sort((a, b) => a.start - b.start)
  const parts = [
    ...found.map(cut => ({
      start: cut.start,
      end: cut.end,
      title: CUT_TITLES[cut.kind],
      cut: true,
    })),
    ...keepWithout(found, total).flatMap(kept => split(kept, topics)),
  ]
  return parts.sort((a, b) => a.start - b.start)
}

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

export const STATES: Record<string, { label: string; tone: 'plain' | 'live' | 'warn' | 'error' }> =
  {
    recording: { label: 'Salvestab', tone: 'live' },
    captured: { label: 'Salvestatud', tone: 'plain' },
    partial: { label: 'Osaline', tone: 'warn' },
    failed: { label: 'Ebaõnnestus', tone: 'error' },
  }

export const textState = (recording: RecordingSummary): string => {
  if (!recording.transcribed) return recording.status.state === 'recording' ? 'Salvestamisel' : '—'
  return recording.transcriptCheckedAt ? 'Kontrollitud' : 'Kontrollimata'
}

export const cutState = (recording: RecordingSummary): string => {
  if (recording.failure) return 'Ebaõnnestus'
  if (!recording.editList) return '—'
  if (recording.editList.reviewed) return 'Kinnitatud'
  return `${cuts(recording.editList, recording.status.seconds).length} soovitust`
}

export const span = (recording: RecordingSummary): string => {
  const { startedAt, seconds } = recording.status
  return `${clock(startedAt, 0)}–${clock(startedAt, seconds)}`
}
