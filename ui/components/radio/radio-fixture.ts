import type {
  EditList,
  RecordingSummary,
  TranscriptLine,
} from '../../models/generated/domain-models'

export const STARTED = Date.UTC(2026, 9, 17, 8, 55, 0) / 1000

export const editList = (overrides: Partial<EditList> = {}): EditList => ({
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

export const recording = (overrides: Partial<RecordingSummary> = {}): RecordingSummary => ({
  name: '2026-10-17',
  status: {
    state: 'captured',
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
  ...overrides,
})

const line = (start: number, end: number, text: string): TranscriptLine => ({
  start,
  end,
  text,
  words: text.split(' ').map((word, i, all) => ({
    start: start + ((end - start) * i) / all.length,
    end: start + ((end - start) * (i + 1)) / all.length,
    word: ` ${word}`,
  })),
})

export const transcript = (): TranscriptLine[] => [
  line(700, 760, 'Tere päevast, kell on kaksteist.'),
  line(762, 780, 'Tere, Keskpäevatund on eetris!'),
  line(2100, 2119, 'Ja siinkohal väike paus.'),
  line(2256, 2270, 'Ja Keskpäevatund jätkab.'),
  line(3000, 3024, 'Teeme siin väikese pausi.'),
  line(3138, 3160, 'Jutt jätkab, Kuku Raadio otsestuudios.'),
  line(3800, 3836, 'Ja siinkohal lõpetame oma tänase saate.'),
  line(3900, 3920, 'Tere päevast, kell on üks.'),
]
