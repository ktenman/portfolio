import {
  CutState,
  type DigestBuild,
  type DigestFailure,
  type DigestMention,
  DigestStep,
  type DigestStory,
  type RadioDigest,
  Station,
  type TranscriptSection,
} from '../../models/generated/domain-models'
import { recording } from './radio-fixture'

const told = (programme: string, day: string, station = Station.KUKU): DigestMention => ({
  programme,
  station,
  day,
})

const KESKPAEVATUND = told('Keskpäevatund', '2026-11-14')
const SAMOST = told('Samost ja Aaspõllu', '2026-11-15')
const DIGITUND = told('Digitund', '2026-11-09')
const KUUM_TOOL_TOLD = told('Kuum tool', '2026-11-09')

const STORIES: [string, string, DigestMention, string, DigestMention[]][] = [
  [
    'Rünnakud Ust-Lugas ja rahuläbirääkimised',
    'Droonid tabasid Ust-Luga sadamat ja rahuläbirääkimised venivad.',
    KESKPAEVATUND,
    '2026-11-14-1200-keskpaevatund',
    [SAMOST, KUUM_TOOL_TOLD],
  ],
  [
    'Valimised: reitingud ja uued tulijad',
    'Erakondade reitingud enne valimisi ja uued tulijad poliitikas.',
    SAMOST,
    '2026-11-15-1200-samost-ja-aaspollu',
    [KESKPAEVATUND, KUUM_TOOL_TOLD],
  ],
  [
    'Telia koondamised ja tehisaru',
    'Telia koondab sadu töötajaid ja põhjendab seda tehisaruga.',
    DIGITUND,
    '2026-11-09-1000-digitund',
    [told('Hommikuprogramm', '2026-11-13', Station.ARIPAEV)],
  ],
  [
    'Kahjunõue ebaseaduslike sideandmete pärast',
    'Riik peab maksma kahjutasu ebaseaduslikult kogutud sideandmete eest.',
    KESKPAEVATUND,
    '2026-11-14-1200-keskpaevatund',
    [SAMOST],
  ],
  [
    'Nvidia juhi Jensen Huangi tagasiastumine',
    'Nvidia juht astub tagasi ja turud reageerivad.',
    DIGITUND,
    '2026-11-09-1000-digitund',
    [],
  ],
  [
    'Reformierakonna maksuplaan ja sündimus',
    'Reformierakond tahab maksudega sündimust toetada.',
    SAMOST,
    '2026-11-15-1200-samost-ja-aaspollu',
    [],
  ],
]

const STORY_SECONDS = 200

const digestStories = (count = STORIES.length): DigestStory[] =>
  STORIES.slice(0, count).map(([title, summary, source, name, also], index) => ({
    title,
    summary,
    start: index * STORY_SECONDS,
    end: (index + 1) * STORY_SECONDS,
    importance: 5,
    interest: 4,
    score: 14,
    kind: 'news',
    reason: 'Julgeolek',
    source: { ...source, recording: name, at: 845 + index * 100, also },
  }))

export const digestFailure = (overrides: Partial<DigestFailure> = {}): DigestFailure => ({
  reason: 'The model gave no judgement',
  tries: 1,
  attempts: 3,
  nextTry: '2026-11-16T23:00:00Z',
  ...overrides,
})

export const digestBuild = (overrides: Partial<DigestBuild> = {}): DigestBuild => ({
  date: '2026-11-16',
  step: DigestStep.CUTTING,
  titles: digestStories().map(story => story.title),
  done: 1,
  percent: 58,
  secondsLeft: 300,
  failure: null,
  title: 'Week in Radio 09.11–15.11.2026',
  ...overrides,
})

export const digest = (count?: number): RadioDigest => {
  const stories = digestStories(count)
  const seconds = stories.length * STORY_SECONDS
  const timeline = stories.map(({ start, end, title, summary }) => ({
    start,
    end,
    title,
    cut: false,
    label: null,
    summary,
  }))
  const status = {
    ...recording().status,
    parts: [],
    programmes: ['Week in Radio'],
    startedAt: Date.UTC(2026, 10, 16, 10) / 1000,
    seconds,
  }
  const whole = [{ start: 0, end: seconds }]
  const chapters = stories.map(({ start, title, summary }) => ({
    start,
    title,
    label: null,
    summary,
  }))
  return {
    recording: recording({
      name: 'week-in-radio-2026-11-16',
      programme: 'Week in Radio 09.11–15.11.2026',
      status,
      span: { start: status.startedAt, end: status.startedAt + seconds },
      sizeBytes: 0,
      speech: null,
      sounds: null,
      editList: {
        ...recording().editList!,
        keep: whole,
        durationSeconds: seconds,
        parts: [],
        boundaries: [],
        hosts: [],
        outline: { model: 'google/gemini-4-carbon', reads: 1, matched: 1, voters: 1 },
        chapters,
        reviewed: true,
        topics: chapters,
      },
      episodeBytes: 9_600_000,
      cuts: [],
      timeline,
      topics: timeline,
      audible: whole,
      format: null,
      programmeCuts: [],
      stations: [...new Set(stories.map(({ source }) => source.station))],
      shows: [...new Set(stories.map(({ source }) => source.programme))],
      cutState: CutState.REVIEWED,
    }),
    stories,
  }
}

export const digestSections = (): TranscriptSection[] =>
  digestStories().map(story => ({
    start: story.start,
    end: story.end,
    title: story.title,
    cut: null,
    episodeStart: story.start,
    lines: [
      {
        start: story.start,
        end: story.start + 18,
        text: ` ${story.summary}`,
        words: [],
        split: null,
        music: false,
      },
    ],
    source: story.source,
  }))
