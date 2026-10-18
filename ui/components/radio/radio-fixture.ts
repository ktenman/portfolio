import {
  CutKind,
  type EditList,
  type RecordingSummary,
  RecordingState,
} from '../../models/generated/domain-models'

const STARTED = Date.UTC(2026, 9, 17, 8, 55, 0) / 1000

const editList = (overrides: Partial<EditList> = {}): EditList => ({
  keep: [
    { start: 761.5, end: 2120 },
    { start: 2255, end: 3025 },
    { start: 3137, end: 3837 },
  ],
  durationSeconds: 2512.5,
  parts: [{ file: 'raw.mp3', offsetSeconds: 0, sha256: 'ab12' }],
  boundaries: [
    { boundary: 'show starts', seconds: 761.5, method: 'digital silence', review: false },
    { boundary: 'break 1 starts', seconds: 2120, method: 'level dip', review: false },
    { boundary: 'break 1 ends', seconds: 2255, method: 'station jingle, r=0.93', review: false },
    { boundary: 'break 2 starts', seconds: 3025, method: 'digital silence', review: false },
    { boundary: 'break 2 ends', seconds: 3137, method: 'transcript only', review: true },
    { boundary: 'show ends', seconds: 3837, method: 'digital silence', review: false },
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
  methods: string[],
  review = false
) => ({ kind, title, start, end, methods, review, removed: true })

export const recording = (overrides: Partial<RecordingSummary> = {}): RecordingSummary => ({
  name: '2026-10-17',
  programme: 'Keskpäevatund',
  status: {
    state: RecordingState.CAPTURED,
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
    cut(CutKind.BEFORE, 'Enne saadet', 0, 761.5, ['digital silence']),
    cut(CutKind.BREAK, 'Reklaamipaus', 2120, 2255, ['level dip', 'station jingle, r=0.93']),
    cut(CutKind.BREAK, 'Reklaamipaus', 3025, 3137, ['digital silence', 'transcript only'], true),
    cut(CutKind.AFTER, 'Pärast saadet', 3837, 4200, ['digital silence']),
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
  ...overrides,
})
