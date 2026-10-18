import {
  type Boundary,
  BoundaryMethod,
  CutKind,
  CutState,
  type CutContext,
  type EditList,
  type RecordingSummary,
  RecordingState,
  type TranscriptLine,
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
})

const editList = (overrides: Partial<EditList> = {}): EditList => ({
  keep: [
    { start: 761.5, end: 2120 },
    { start: 2255, end: 3025 },
    { start: 3137, end: 3837 },
  ],
  durationSeconds: 2512.5,
  parts: [{ file: 'raw.mp3', offsetSeconds: 0, sha256: 'ab12' }],
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
    { start: 760, title: 'Rahvusooperi tüli' },
    { start: 2700, title: 'Mis toimub poliitikas?' },
    { start: 3400, title: 'President Ülle Madise' },
  ],
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

export const recording = (overrides: Partial<RecordingSummary> = {}): RecordingSummary => ({
  name: '2026-10-17',
  programme: 'Keskpäevatund',
  status: {
    state: RecordingState.CAPTURED,
    programmes: ['Keskpäevatund'],
    startedAt: STARTED,
    seconds: 4200,
    parts: [{ file: 'raw.mp3', offsetSeconds: 0, sha256: 'ab12' }],
  },
  sizeBytes: 134_400_000,
  transcribed: true,
  transcriptCheckedAt: null,
  editList: editList(),
  failure: null,
  episodeBytes: 34_000_000,
  cuts: [
    cut(CutKind.BEFORE, 'Enne saadet', 0, 761.5, [DIGITAL]),
    {
      ...cut(CutKind.BREAK, 'Reklaamipaus', 2120, 2255, [
        BoundaryMethod.LEVEL_DIP,
        BoundaryMethod.STATION_JINGLE,
      ]),
      jingle: { r: 0.93, saved: false },
    },
    cut(CutKind.BREAK, 'Reklaamipaus', 3025, 3137, [DIGITAL, BoundaryMethod.TRANSCRIPT_ONLY], true),
    cut(CutKind.AFTER, 'Pärast saadet', 3837, 4200, [DIGITAL]),
  ],
  timeline: [
    { start: 0, end: 761.5, title: 'Enne saadet', cut: true },
    { start: 761.5, end: 2120, title: 'Rahvusooperi tüli', cut: false },
    { start: 2120, end: 2255, title: 'Reklaamipaus', cut: true },
    { start: 2255, end: 2700, title: 'Rahvusooperi tüli', cut: false },
    { start: 2700, end: 3025, title: 'Mis toimub poliitikas?', cut: false },
    { start: 3025, end: 3137, title: 'Reklaamipaus', cut: true },
    { start: 3137, end: 3400, title: 'Mis toimub poliitikas?', cut: false },
    { start: 3400, end: 3837, title: 'President Ülle Madise', cut: false },
    { start: 3837, end: 4200, title: 'Pärast saadet', cut: true },
  ],
  audible: [
    { start: 761.5, end: 2120 },
    { start: 2255, end: 3025 },
    { start: 3137, end: 3837 },
  ],
  transcriptState: TranscriptState.UNCHECKED,
  cutState: CutState.SUGGESTED,
  ...overrides,
})

const line = (start: number, end: number, text: string): TranscriptLine => ({
  start,
  end,
  text,
  words: [],
  split: null,
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
