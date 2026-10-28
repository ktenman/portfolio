import {
  AiringState,
  type Boundary,
  BoundaryMethod,
  CutKind,
  CutState,
  ProgrammeCutState,
  type ProgrammeCuts,
  type CutContext,
  type DayAiring,
  type EditList,
  type RadioDay,
  type RadioSearch,
  type RecordingStatus,
  type RecordingSummary,
  RecordingState,
  type TextPart,
  type TranscriptLine,
  type TranscriptSection,
  TranscriptState,
} from '../../models/generated/domain-models'

const DIGITAL = BoundaryMethod.DIGITAL_SILENCE
const STARTED = Date.UTC(2026, 9, 17, 8, 55, 0) / 1000

const boundary = (name: string, seconds: number, method: string): Boundary => ({
  boundary: name,
  seconds,
  method,
  review: method === 'transcript only',
  jingle: null,
  programme: 'Keskpäevatund',
})

const editList = (overrides: Partial<EditList> = {}): EditList => ({
  keep: [
    { start: 761.5, end: 2120 },
    { start: 2255, end: 3025 },
    { start: 3137, end: 3837 },
  ],
  durationSeconds: 2512.5,
  parts: [{ file: 'raw.mp3', offsetSeconds: 0, sha256: 'ab12', durationSeconds: 4200 }],
  boundaries: [
    boundary('show starts', 761.5, 'digital silence'),
    boundary('break 1 starts', 2120, 'level dip'),
    {
      ...boundary('break 1 ends', 2255, 'station jingle, r=0.93'),
      jingle: { start: 2255.4, r: 0.93, saved: null },
    },
    boundary('break 2 starts', 3025, 'digital silence'),
    boundary('break 2 ends', 3137, 'transcript only'),
    boundary('show ends', 3837, 'digital silence'),
  ],
  hosts: ['Mari', 'Jüri'],
  outline: { model: 'deepseek/deepseek-v4-flash', reads: 3, matched: 3, voters: 3 },
  chapters: [],
  reviewed: false,
  renderedAt: '2026-10-17T10:20:00Z',
  topics: [
    { start: 760, title: 'Rahvusooperi tüli', label: null, summary: null },
    { start: 2700, title: 'Mis toimub poliitikas?', label: null, summary: null },
    { start: 3400, title: 'President Ülle Madise', label: null, summary: null },
  ],
  music: [],
  manual: [],
  songs: [],
  ...overrides,
})

const cut = (
  kind: CutKind,
  title: string,
  start: number,
  end: number,
  methods: BoundaryMethod[],
  review = false
) => ({ kind, title, start, end, methods, review, removed: true, jingle: null })

export const programme = (
  name: string,
  state: ProgrammeCutState,
  details: Partial<ProgrammeCuts> = {}
): ProgrammeCuts => ({ name, state, progress: null, transcript: null, after: null, ...details })

const captured = (): RecordingStatus => ({
  state: RecordingState.CAPTURED,
  programmes: ['Keskpäevatund'],
  startedAt: STARTED,
  seconds: 4200,
  parts: [{ file: 'raw.mp3', offsetSeconds: 0, sha256: 'ab12', durationSeconds: 4200 }],
  lostSeconds: 0,
  slot: null,
})

const spanned = ({ startedAt, seconds }: RecordingStatus) => ({
  start: startedAt,
  end: startedAt + seconds,
})

export const recording = (overrides: Partial<RecordingSummary> = {}): RecordingSummary => ({
  name: '2026-10-17',
  programme: 'Keskpäevatund',
  status: captured(),
  span: spanned(overrides.status ?? captured()),
  sizeBytes: 134_400_000,
  transcribed: true,
  speech: {
    model: 'TalTechNLP/streaming-zipformer-large.et-en',
    revision: '2812262bd4fd5d4327dc09ca0e6c19e8a32a03d6',
    label: 'Zipformer · 2812262',
  },
  sounds: { model: 'ced-tiny int8', revision: '2024-04-19', label: 'CED-tiny · 2024-04-19' },
  editList: editList(),
  failure: null,
  render: null,
  renderFailure: null,
  episodeBytes: 34_000_000,
  cuts: [
    cut(CutKind.BEFORE, 'Before the show', 0, 761.5, [DIGITAL]),
    {
      ...cut(CutKind.BREAK, 'Ad break', 2120, 2255, [
        BoundaryMethod.LEVEL_DIP,
        BoundaryMethod.STATION_JINGLE,
      ]),
      jingle: { r: 0.93, saved: false },
    },
    cut(CutKind.BREAK, 'Ad break', 3025, 3137, [DIGITAL, BoundaryMethod.TRANSCRIPT_ONLY], true),
    cut(CutKind.AFTER, 'After the show', 3837, 4200, [DIGITAL]),
  ],
  timeline: [
    { start: 0, end: 761.5, title: 'Before the show', cut: true, label: null, summary: null },
    {
      start: 761.5,
      end: 2120,
      title: 'Rahvusooperi tüli',
      cut: false,
      label: 'Ooperitüli',
      summary: 'Rahvusooper Estonia juhtkond ja nõukogu ei leia ühist keelt.',
    },
    { start: 2120, end: 2255, title: 'Ad break', cut: true, label: null, summary: null },
    {
      start: 2255,
      end: 2700,
      title: 'Rahvusooperi tüli',
      cut: false,
      label: 'Ooperitüli',
      summary: 'Rahvusooper Estonia juhtkond ja nõukogu ei leia ühist keelt.',
    },
    {
      start: 2700,
      end: 3025,
      title: 'Mis toimub poliitikas?',
      cut: false,
      label: 'Poliitika',
      summary: 'Nädala poliitilised uudised ja eelarvevaidlus.',
    },
    { start: 3025, end: 3137, title: 'Ad break', cut: true, label: null, summary: null },
    {
      start: 3137,
      end: 3400,
      title: 'Mis toimub poliitikas?',
      cut: false,
      label: 'Poliitika',
      summary: 'Nädala poliitilised uudised ja eelarvevaidlus.',
    },
    {
      start: 3400,
      end: 3837,
      title: 'President Ülle Madise',
      cut: false,
      label: 'Ülle Madise',
      summary: 'Õiguskantsler räägib oma presidendikandidatuurist.',
    },
    { start: 3837, end: 4200, title: 'After the show', cut: true, label: null, summary: null },
  ],
  audible: [
    { start: 761.5, end: 2120 },
    { start: 2255, end: 3025 },
    { start: 3137, end: 3837 },
  ],
  topics: [
    {
      start: 0,
      end: 1803.5,
      title: 'Rahvusooperi tüli',
      cut: false,
      label: 'Ooperitüli',
      summary: 'Rahvusooper Estonia juhtkond ja nõukogu ei leia ühist keelt.',
    },
    {
      start: 1803.5,
      end: 2391.5,
      title: 'Mis toimub poliitikas?',
      cut: false,
      label: 'Poliitika',
      summary: 'Nädala poliitilised uudised ja eelarvevaidlus.',
    },
    {
      start: 2391.5,
      end: 2828.5,
      title: 'President Ülle Madise',
      cut: false,
      label: 'Ülle Madise',
      summary: 'Õiguskantsler räägib oma presidendikandidatuurist.',
    },
  ],
  format: { codec: 'mp3', bitRate: 256_000 },
  programmeCuts: [programme('Keskpäevatund', ProgrammeCutState.FOUND)],
  drops: 0,
  transcriptState: TranscriptState.TRANSCRIBED,
  cutState: CutState.SUGGESTED,
  ...overrides,
})

const line = (start: number, end: number, text: string): TranscriptLine => ({
  start,
  end,
  text,
  words: [],
  split: null,
  music: false,
})

export const contexts = (): CutContext[] => [
  { before: null, after: line(762, 770, ' Tere päevast, kell on kaksteist.') },
  {
    before: line(2110, 2119.5, ' Ja siinkohal väike paus.'),
    after: line(2256, 2262, ' Ja Keskpäevatund jätkab.'),
  },
  {
    before: line(3018, 3024.8, ' Kohe tuleme tagasi.'),
    after: line(3138, 3145, ' Kuku Raadio otsestuudios.'),
  },
  { before: line(3830, 3836.5, ' Ja siinkohal lõpetame.'), after: null },
]

export const song = (): TranscriptLine => ({ ...line(795, 886, ''), music: true })

export const transcript = (): TranscriptLine[] => [
  {
    ...line(762, 765, ' Tere päevast, kell on kaksteist.'),
    words: [
      { start: 762, end: 763, word: ' Tere' },
      { start: 763.2, end: 764, word: ' päevast,' },
      { start: 764.1, end: 765, word: ' kell on kaksteist.' },
    ],
  },
  { ...line(2110, 2125, ' Ja nüüd väike paus, ostke'), split: recording().cuts[1] },
  line(2256, 2262, ' Ja Keskpäevatund jätkab.'),
]

const index = (item: RecordingSummary, entry: { start: number; end: number }) => {
  const found = item.cuts.findIndex(c => c.start === entry.start && c.end === entry.end)
  return found < 0 ? null : found
}

const EPISODE: Record<number, number> = {
  761.5: 0,
  2255: 1358.5,
  2700: 1803.5,
  3137: 2128.5,
  3400: 2391.5,
}

export const sections = (
  lines: TranscriptLine[] = transcript(),
  item: RecordingSummary = recording()
): TranscriptSection[] =>
  item.timeline.map(entry => ({
    start: entry.start,
    end: entry.end,
    title: entry.title ?? item.programme,
    cut: entry.cut ? index(item, entry) : null,
    episodeStart: entry.cut ? null : (EPISODE[entry.start] ?? null),
    lines: lines.filter(l => entry.start <= l.start && l.start < entry.end),
  }))

const at = (hour: number, minute = 0) => Date.UTC(2026, 9, 17, hour - 3, minute) / 1000

const take = (from: number, to: number) => ({
  name: '2026-10-17',
  programme: 'Keskpäevatund',
  start: STARTED,
  end: STARTED + 4200,
  from,
  to,
})

export const airing = (overrides: Partial<DayAiring> = {}): DayAiring => ({
  id: 1,
  name: 'Ilmaparandaja',
  episode: null,
  start: at(14),
  end: at(15),
  state: AiringState.OPEN,
  once: false,
  repeat: false,
  original: null,
  recording: null,
  ...overrides,
})

const hour = (id: number, start: number, name: string, overrides: Partial<DayAiring> = {}) =>
  airing({ id, name, start: at(start), end: at(start + 1), ...overrides })

const repeat = (
  id: number,
  start: number,
  name: string,
  original: DayAiring['original'],
  overrides: Partial<DayAiring> = {}
) => hour(id, start, name, { repeat: true, original, ...overrides })

const MISSED = AiringState.MISSED
const ARCHIVED = { start: at(12), state: AiringState.RECORDED, recording: take(at(12), at(13)) }

export const radioDay = (overrides: Partial<RadioDay> = {}): RadioDay => ({
  date: '2026-10-17',
  today: '2026-10-17',
  days: [
    '2026-10-12',
    '2026-10-13',
    '2026-10-14',
    '2026-10-15',
    '2026-10-16',
    '2026-10-17',
    '2026-10-18',
  ],
  from: at(5),
  until: at(29),
  ticks: [
    { at: at(6), midnight: false },
    { at: at(12), midnight: false },
    { at: at(18), midnight: false },
    { at: at(24), midnight: true },
  ],
  now: at(13, 20),
  recorded: [
    {
      name: '2026-10-17',
      programme: 'Keskpäevatund',
      start: STARTED,
      end: STARTED + 4200,
      recording: false,
    },
  ],
  seconds: 4200,
  day: [
    {
      missed: true,
      airings: [
        repeat(1, 5, 'Kukkuv Õun', null, { state: MISSED }),
        hour(2, 6, 'Laupäevane hommik', { end: at(9), state: MISSED }),
        hour(3, 9, 'Meretund', { state: MISSED }),
      ],
    },
    {
      missed: false,
      airings: [
        hour(4, 10, 'Neeme Raud. Siin', {
          end: at(12),
          state: AiringState.PARTIAL,
          recording: take(at(11, 55), at(12)),
        }),
        hour(5, 12, 'Keskpäevatund', {
          state: AiringState.RECORDED,
          recording: take(at(12), at(13)),
        }),
        hour(6, 13, 'Tarkade klubi', { state: AiringState.ON_AIR }),
        hour(7, 14, 'Ilmaparandaja'),
        hour(8, 15, 'ENNtsüklopeedia', { episode: 'Chuck Berry' }),
        hour(9, 17, 'Naistejutud Kukus', { episode: '474 · Robin Juhkental' }),
        repeat(10, 18, 'Keskpäevatund', ARCHIVED, { state: AiringState.ARCHIVED }),
        hour(11, 19, 'Ringhääling 75', { episode: 'VII saade' }),
        repeat(12, 20, 'Filmikägu', { start: at(-3), state: MISSED, recording: null }),
        repeat(13, 21, 'Meretund', { start: at(9), state: MISSED, recording: null }),
        repeat(14, 22, 'EMALAEV', null, {
          end: at(24),
          episode: 'Kristjan Port: süsiniku ja räni, bioloogia ja tehnoloogia kohtumine',
        }),
      ],
    },
  ],
  night: [
    {
      missed: false,
      airings: [
        repeat(
          15,
          24,
          'Neeme Raud. Siin',
          { start: at(10), state: AiringState.PARTIAL, recording: take(at(11, 55), at(12)) },
          { end: at(26) }
        ),
        repeat(16, 26, 'Keskpäevatund', ARCHIVED, { state: AiringState.ARCHIVED }),
        repeat(17, 27, 'Tarkade klubi', null, { state: AiringState.PLANNED, once: true }),
      ],
    },
  ],
  ...overrides,
})

const said = (...parts: string[]): TextPart[] =>
  parts.map((text, index) => ({ text, mark: index % 2 === 1 }))

export const radioSearch = (overrides: Partial<RadioSearch> = {}): RadioSearch => ({
  topics: [
    {
      name: '2026-10-17',
      programme: 'Keskpäevatund',
      startedAt: STARTED,
      start: 2700,
      title: said('Kes ', 'valimistel', ' võidab?'),
      summary: said('Saatejuhid arutavad ', 'valimiste', ' järel.'),
    },
  ],
  moreTopics: 0,
  groups: [
    {
      name: '2026-10-17',
      programme: 'Keskpäevatund',
      startedAt: STARTED,
      lines: [
        { start: 2150, kept: false, text: said('Uudistes: ', 'valimistest', ' veel.') },
        { start: 2300, kept: true, text: said('Kevadel tulevad ', 'valimised', '.') },
      ],
      more: 0,
    },
  ],
  lines: 2,
  recordings: 1,
  partial: false,
  suggestions: [],
  next: null,
  searchable: true,
  ...overrides,
})

export const nothingFound = (close: string[] = []): RadioSearch =>
  radioSearch({
    topics: [],
    groups: [],
    lines: 0,
    recordings: 0,
    suggestions: close,
  })
