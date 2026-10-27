import {
  type AirSlot,
  AiringState,
  type AiringRecording,
  type DayAiring,
  type AudioFormat,
  BoundaryMethod,
  type CutProgress,
  CutStep,
  DayOfWeek,
  type KeptSpan,
  ProgrammeCutState,
  type RadioDay,
  RecorderState,
  type RecordingSummary,
  RecordingState,
  type TimelineEntry,
  type TranscriptSection,
  TranscriptState,
} from '../models/generated/domain-models'
import type { BadgeTone } from '../components/radio/radio-badge.vue'
import { WEEKDAYS } from './iso-dates'

const ZONE = 'Europe/Tallinn'

export const METHODS: Record<BoundaryMethod, string> = {
  [BoundaryMethod.DIGITAL_SILENCE]: 'digital silence',
  [BoundaryMethod.STATION_JINGLE]: 'station jingle',
  [BoundaryMethod.LEVEL_DIP]: 'level dip',
  [BoundaryMethod.TRANSCRIPT_ONLY]: 'text only',
  [BoundaryMethod.MANUAL]: 'manual',
  [BoundaryMethod.SOUND]: 'sound analysis',
}

const DAYS = Object.values(DayOfWeek)

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
        ? `${WEEKDAYS[run[0]]}–${WEEKDAYS[run[run.length - 1]]}`
        : run.map(i => WEEKDAYS[i]).join(', ')
    )
    .join(', ')

export const airTime = (slot: AirSlot): string =>
  `${weekdays(slot.days)} ${slot.start.slice(0, 5)}–${slot.end.slice(0, 5)}`

export const LINK =
  'inline-flex items-center gap-1.5 rounded-container border border-control-border px-3 py-1.5 text-sm font-medium whitespace-nowrap shadow-control transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep'

export const ROW_LINK =
  'font-semibold after:absolute after:inset-0 after:rounded-container focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-brass-deep'

export const PLAY =
  'inline-flex shrink-0 cursor-pointer items-center justify-center rounded-full bg-brass text-white shadow-control transition-colors hover:bg-brass-deep focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brass-deep'

export const recordingTime = (keep: KeptSpan[], seconds: number): number => {
  const last = keep[keep.length - 1]?.end ?? seconds
  let left = seconds
  for (const k of keep) {
    if (left < k.end - k.start) return k.start + left
    left -= k.end - k.start
  }
  return last
}

export const episodeTime = (keep: KeptSpan[], seconds: number): number =>
  keep.reduce((sum, k) => sum + Math.max(0, Math.min(seconds, k.end) - k.start), 0)

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

export const recordedSummary = ({ recorded, seconds }: RadioDay): string | null =>
  recorded.length
    ? `${recorded.length} ${recorded.length === 1 ? 'recording' : 'recordings'} · ${length(seconds)}`
    : null

export const minutes = (seconds: number): string => `${Math.max(1, Math.round(seconds / 60))} min`

export const megabytes = (bytes: number): string => `${Math.round(bytes / 1_000_000)} MB`

const formatter = (options: Intl.DateTimeFormatOptions, locale = 'et-EE') =>
  new Intl.DateTimeFormat(locale, { timeZone: ZONE, ...options })

const TIME = formatter({ hour: '2-digit', minute: '2-digit' })
const TIME_SECONDS = formatter({ hour: '2-digit', minute: '2-digit', second: '2-digit' })
const DATE = formatter({ day: '2-digit', month: '2-digit', year: 'numeric' })
const WEEKDAY = formatter({ weekday: 'short' }, 'en-GB')
const WEEKDAY_LONG = formatter({ weekday: 'long' }, 'en-GB')

const instant = (startedAt: number, seconds = 0) => new Date((startedAt + seconds) * 1000)

export const clock = (startedAt: number, seconds: number, withSeconds = false): string =>
  (withSeconds ? TIME_SECONDS : TIME).format(instant(startedAt, seconds))

export const left = (seconds: number, now = new Date()): string => {
  const text =
    seconds >= 60
      ? `~${Math.round(seconds / 60)} min left`
      : `~${Math.max(5, Math.round(seconds / 5) * 5)} s left`
  if (seconds <= 600) return text
  return `${text} · ready at ~${TIME.format(new Date(now.getTime() + seconds * 1000))}`
}

const STEPS: Record<CutStep, string> = {
  [CutStep.DECODE]: 'Decoding the audio',
  [CutStep.SILENCE]: 'Finding silences',
  [CutStep.READ]: 'Reading the show with AI',
  [CutStep.BOUNDARIES]: 'Placing the cuts',
  [CutStep.RENDER]: 'Making the clean version',
}

export const stepLabel = ({ step, read }: CutProgress): string =>
  step === CutStep.READ ? `${STEPS[step]} · ${read}/3` : STEPS[step]

const COMING = new Set([
  ProgrammeCutState.FINDING,
  ProgrammeCutState.QUEUED,
  ProgrammeCutState.WAITING,
])

export const poll = (recordings: RecordingSummary[]): number | false =>
  recordings.some(it => it.render != null || it.programmeCuts.some(cuts => COMING.has(cuts.state)))
    ? 60_000
    : false

export const advance = (
  progress: { percent: number; secondsLeft: number },
  elapsed: number
): { percent: number; secondsLeft: number } => {
  if (elapsed <= 0) return progress
  const done = (100 - progress.percent) * Math.min(1, elapsed / progress.secondsLeft)
  return {
    percent: Math.min(99, Math.round(progress.percent + done)),
    secondsLeft: Math.max(1, Math.ceil(progress.secondsLeft - elapsed)),
  }
}

const daySeconds = (time: string): number | null => {
  const match = /^(\d{1,2}):(\d{2}):(\d{2})$/.exec(time.trim())
  return match ? Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3]) : null
}

export const offset = (startedAt: number, time: string): number | null => {
  const at = daySeconds(time)
  const start = daySeconds(clock(startedAt, 0, true))
  if (at === null || start === null) return null
  return (at - start + 86400) % 86400
}

export const sectionTime = (section: TranscriptSection, raw: boolean, startedAt: number): string =>
  raw ? clock(startedAt, section.start) : position(section.episodeStart ?? 0)

export const range = (startedAt: number, start: number, end: number, withSeconds = false): string =>
  `${clock(startedAt, start, withSeconds)}–${clock(startedAt, end, withSeconds)}`

export const day = (startedAt: number): string =>
  `${WEEKDAY.format(instant(startedAt))} ${DATE.format(instant(startedAt)).slice(0, 5)}`

export const fullDay = (startedAt: number): string =>
  `${WEEKDAY_LONG.format(instant(startedAt))}, ${DATE.format(instant(startedAt)).slice(0, 5)}`

export const STATES: Record<RecordingState, { label: string; tone: BadgeTone }> = {
  [RecordingState.RECORDING]: { label: 'Recording', tone: 'live' },
  [RecordingState.CAPTURED]: { label: 'Recorded', tone: 'plain' },
  [RecordingState.PARTIAL]: { label: 'Partial', tone: 'warn' },
  [RecordingState.FAILED]: { label: 'Failed', tone: 'error' },
}

export const AIRING_STATES: Partial<Record<AiringState, { label: string; tone: BadgeTone }>> = {
  [AiringState.RECORDED]: { label: 'Recorded', tone: 'plain' },
  [AiringState.PARTIAL]: { label: 'Partial', tone: 'warn' },
  [AiringState.RECORDING]: { label: 'Recording', tone: 'live' },
  [AiringState.ON_AIR]: { label: 'On air', tone: 'live' },
  [AiringState.PLANNED]: { label: 'Planned', tone: 'plain' },
}

export const airLength = (seconds: number): string => {
  const hours = Math.round(seconds / 3600)
  return hours && Math.abs(seconds - hours * 3600) <= 300 ? `${hours} h` : length(seconds)
}

export const coversEnd = (airing: DayAiring): boolean =>
  !!airing.recording && airing.recording.to >= airing.end - 60

const covered = (airing: DayAiring, recording: AiringRecording): string =>
  `${coversEnd(airing) ? 'Last ' : ''}${minutes(recording.to - recording.from)} recorded in “${recording.programme}”`

const repeatNote = (airing: DayAiring): string | null => {
  const original = airing.original
  if (original?.state === AiringState.PARTIAL) return 'can be recorded in full'
  if (original?.state === AiringState.MISSED)
    return `${day(original.start)} ${clock(original.start, 0)} not recorded`
  return airing.episode ?? (original ? day(original.start) : null)
}

export const airingNote = (airing: DayAiring): string | null => {
  const recording = airing.recording
  if (airing.state === AiringState.RECORDED && recording)
    return `Recorded ${range(recording.start, 0, recording.end - recording.start)}`
  if (airing.state === AiringState.PARTIAL && recording) return covered(airing, recording)
  if (airing.state === AiringState.RECORDING) return 'Being recorded'
  if (airing.repeat) return ['Repeat', repeatNote(airing)].filter(Boolean).join(' · ')
  if (airing.state === AiringState.ON_AIR) return 'Not being recorded'
  return airing.episode
}

export const missedRun = (airings: DayAiring[]): string => {
  const first = airings[0]
  const last = airings[airings.length - 1]
  const count = `${airings.length} ${airings.length === 1 ? 'show' : 'shows'}`
  return `${range(first.start, 0, last.end - first.start)} · ${count}, not recorded`
}

export const dateDay = (date: string): string => day(Date.parse(`${date}T12:00:00Z`) / 1000)

export const nightOf = (until: number): string => {
  const weekday = WEEKDAY_LONG.format(instant(until))
  const date = DATE.format(instant(until)).slice(0, 5)
  return `Overnight into ${weekday}, ${date}`
}

export const RECORDER_STATES: Record<RecorderState, { label: string; tone: BadgeTone }> = {
  [RecorderState.WAITING]: { label: 'Waiting', tone: 'plain' },
  [RecorderState.RECORDING]: { label: 'Recording', tone: 'live' },
  [RecorderState.OFF]: { label: 'Off', tone: 'error' },
}

const TEXT_STATES: Record<TranscriptState, string> = {
  [TranscriptState.NONE]: '—',
  [TranscriptState.RECORDING]: 'In progress',
  [TranscriptState.TRANSCRIBED]: 'Ready',
}

export const textState = (recording: RecordingSummary): string =>
  TEXT_STATES[recording.transcriptState]

export const audioFormat = (format: AudioFormat | null): string =>
  format ? `${format.codec.toUpperCase()} ${Math.round(format.bitRate / 1000)} kb/s` : '—'

export const span = ({ span: { start, end } }: RecordingSummary): string =>
  range(start, 0, end - start)

export const HATCH =
  'bg-[repeating-linear-gradient(135deg,var(--color-hairline-strong)_0_1px,transparent_1px_6px)]'

export interface StripPart {
  start: number
  end: number
  removed: boolean
  label: string
  title: string | null
  name: string | null
  summary: string | null
  time: string
}

const named = ({ title, label, summary, cut }: TimelineEntry) => ({
  title,
  name: cut ? null : (label ?? title),
  summary: summary ?? null,
})

export const rawParts = (
  { timeline, cuts, status, programme }: RecordingSummary,
  removed: number[]
): StripPart[] =>
  timeline.map((entry, i) => {
    const { start, end, title, cut } = entry
    const out = cut && removed.includes(cuts.findIndex(c => c.start === start))
    const fate = out ? 'Removed' : 'Kept'
    const before = timeline.slice(0, i).filter(e => !e.cut)
    const resumed = !cut && before[before.length - 1]?.title === title
    return {
      ...named(entry),
      ...(resumed && { name: null }),
      start,
      end,
      removed: out,
      label: `${title ?? programme} ${range(status.startedAt, start, end)} · ${fate}`,
      time: clock(status.startedAt, start),
    }
  })

export const episodeParts = ({ topics, programme }: RecordingSummary): StripPart[] =>
  topics.reduce<StripPart[]>((parts, entry) => {
    const heard = parts.filter(part => part.name !== null)
    const resumed = !entry.cut && heard[heard.length - 1]?.title === entry.title
    const number = heard.length + (resumed ? 0 : 1)
    const lead =
      entry.cut || !entry.title ? (entry.title ?? programme) : `Topic ${number}: ${entry.title}`
    const part = {
      ...named(entry),
      start: entry.start,
      end: entry.end,
      removed: false,
      label: `${lead} · ${position(entry.start)}`,
      time: position(entry.start),
    }
    return [...parts, resumed ? { ...part, name: null } : part]
  }, [])

export const topicAt = (topics: StripPart[], at: number): number =>
  Math.max(0, topics.filter(topic => topic.start <= at).length - 1)
