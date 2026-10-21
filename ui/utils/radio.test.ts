import { describe, expect, it } from 'vitest'
import {
  ProgrammeCutState,
  TranscriptState,
  type KeptSpan,
} from '../models/generated/domain-models'
import {
  advance,
  audioFormat,
  clock,
  day,
  episodeParts,
  episodeTime,
  fullDay,
  left,
  length,
  megabytes,
  minutes,
  offset,
  poll,
  position,
  rawParts,
  recordingTime,
  textState,
} from './radio'
import { programme, recording } from '../components/radio/radio-fixture'

const STARTED = Date.UTC(2026, 9, 17, 8, 55, 0) / 1000

const MACHINE: KeptSpan[] = [
  { start: 761.5, end: 2120 },
  { start: 2255, end: 3025 },
  { start: 3137, end: 3837 },
]

describe('recordingTime', () => {
  it('should map a moment of the episode onto the recording', () => {
    expect(recordingTime(MACHINE, 1400)).toEqual(2296.5)
  })

  it('should stop at the end of the last kept span', () => {
    expect(recordingTime(MACHINE, 9999)).toEqual(3837)
  })
})

describe('episodeTime', () => {
  it('should map a moment of the recording onto the episode', () => {
    expect(episodeTime(MACHINE, 2296.5)).toEqual(1400)
  })

  it('should land a moment inside a cut where the cut ends', () => {
    expect(episodeTime(MACHINE, 2200)).toEqual(1358.5)
  })

  it('should start the episode for a moment before the show', () => {
    expect(episodeTime(MACHINE, 100)).toEqual(0)
  })
})

describe('offset', () => {
  it('should find a clock time in the recording', () => {
    expect(offset(STARTED, '12:31:05')).toEqual(2165)
  })

  it('should find no time in text that is not a clock time', () => {
    expect(offset(STARTED, '12.31')).toBeNull()
  })
})

describe('labels', () => {
  it('should give the clock time in Tallinn', () => {
    expect(clock(STARTED, 761.5, true)).toEqual('12:07:41')
  })

  it('should give the clock time without seconds', () => {
    expect(clock(STARTED, 0)).toEqual('11:55')
  })

  it('should give a position under an hour as minutes and seconds', () => {
    expect(position(1421)).toEqual('23:41')
  })

  it('should give a position of an hour or more with hours', () => {
    expect(position(4200)).toEqual('1:10:00')
  })

  it('should give a length in minutes and seconds', () => {
    expect([length(761), length(60), length(4200)]).toEqual(['12 min 41 s', '1 min', '1 h 10 min'])
  })

  it('should give a short length in seconds', () => {
    expect(length(58.5)).toEqual('59 s')
  })

  it('should round a length to minutes', () => {
    expect(minutes(1358)).toEqual('23 min')
  })

  it('should give a size in megabytes', () => {
    expect(megabytes(134_400_000)).toEqual('134 MB')
  })

  it('should give the short day of a recording', () => {
    expect(day(STARTED)).toEqual('L 17.10')
  })

  it('should give the full day of a recording', () => {
    expect(fullDay(STARTED)).toEqual('Laupäev, 17.10.2026')
  })

  it('should call a finished transcript Olemas', () => {
    expect(textState(recording({ transcriptState: TranscriptState.TRANSCRIBED }))).toEqual('Olemas')
  })
})

describe('stream', () => {
  it('should give the format of the recording', () => {
    expect(audioFormat(recording().format)).toEqual('MP3 256 kb/s')
  })
})

describe('left', () => {
  it.each([
    [45, '~45 s jäänud'],
    [42, '~40 s jäänud'],
    [3, '~5 s jäänud'],
    [90, '~2 min jäänud'],
  ])('should round %i seconds left to %s', (seconds, text) => {
    expect(left(seconds)).toBe(text)
  })

  it('should add the clock time once more than ten minutes are left', () => {
    expect(left(1920, new Date('2026-10-19T09:30:00Z'))).toBe('~32 min jäänud · valmis ~13:02')
  })
})

describe('poll', () => {
  const recordings = (...states: ProgrammeCutState[]) => [
    recording(),
    recording({ programmeCuts: states.map(state => programme('Digitund', state)) }),
  ]

  it('should ask once a minute while a cut search runs', () => {
    expect(poll(recordings(ProgrammeCutState.QUEUED, ProgrammeCutState.FINDING))).toBe(60_000)
  })

  it('should ask once a minute while a cut search waits its turn', () => {
    expect(poll(recordings(ProgrammeCutState.NONE, ProgrammeCutState.QUEUED))).toBe(60_000)
  })

  it('should ask once a minute while a cut search waits for the text', () => {
    expect(poll(recordings(ProgrammeCutState.WAITING))).toBe(60_000)
  })

  it('should ask once a minute while an episode renders', () => {
    expect(poll([recording({ render: { percent: 30, secondsLeft: 70 } })])).toBe(60_000)
  })

  it('should stop asking once no cut search is coming', () => {
    expect(poll(recordings(ProgrammeCutState.FAILED, ProgrammeCutState.NONE))).toBe(false)
  })
})

describe('advance', () => {
  it('should move the percent on by the share of the time left that passed', () => {
    expect(advance({ percent: 36, secondsLeft: 70 }, 35)).toEqual({ percent: 68, secondsLeft: 35 })
  })

  it('should stop short of done however long it waits', () => {
    expect(advance({ percent: 90, secondsLeft: 10 }, 600)).toEqual({ percent: 99, secondsLeft: 1 })
  })

  it('should leave the progress alone when no time passed', () => {
    expect(advance({ percent: 36, secondsLeft: 70 }, 0)).toEqual({ percent: 36, secondsLeft: 70 })
  })
})

describe('rawParts', () => {
  it('should name each part by its title, clock span and fate', () => {
    expect(
      rawParts(recording(), [1])
        .slice(0, 3)
        .map(part => part.label)
    ).toEqual([
      'Enne saadet 11:55–12:07 · Jääb alles',
      'Rahvusooperi tüli 12:07–12:30 · Jääb alles',
      'Reklaamipaus 12:30–12:32 · Eemaldatakse',
    ])
  })
})

describe('rawParts names', () => {
  it('should leave a cut unnamed on the raw strip', () => {
    expect(rawParts(recording(), [])[0]!.name).toBeNull()
  })

  it("should print a raw part's start on the clock", () => {
    expect(rawParts(recording(), [])[1]!.time).toBe('12:07')
  })

  it('should name a kept raw part by its label', () => {
    expect(rawParts(recording(), [])[1]!.name).toBe('Ooperitüli')
  })

  it('should label a part no topic covers by the programme', () => {
    const untitled = { start: 0, end: 4200, title: null, cut: false, label: null, summary: null }
    expect(rawParts(recording({ timeline: [untitled] }), [])[0]!.label).toBe(
      'Keskpäevatund 11:55–13:05 · Jääb alles'
    )
  })

  it('should name a raw topic resumed after a break once', () => {
    expect(rawParts(recording(), []).map(part => part.name)).toEqual([
      null,
      'Ooperitüli',
      null,
      null,
      'Poliitika',
      null,
      null,
      'Ülle Madise',
      null,
    ])
  })
})

describe('episodeParts', () => {
  it('should build the cleaned strip from the topics', () => {
    expect(episodeParts(recording()).map(({ start, end, name }) => [start, end, name])).toEqual([
      [0, 1803.5, 'Ooperitüli'],
      [1803.5, 2391.5, 'Poliitika'],
      [2391.5, 2828.5, 'Ülle Madise'],
    ])
  })

  it('should name each topic by its number, title and start', () => {
    expect(episodeParts(recording()).map(part => part.label)).toEqual([
      'Teema 1: Rahvusooperi tüli · 0:00',
      'Teema 2: Mis toimub poliitikas? · 30:03',
      'Teema 3: President Ülle Madise · 39:51',
    ])
  })

  it("should carry each topic's summary onto the strip", () => {
    expect(episodeParts(recording())[2]!.summary).toBe(
      'Õiguskantsler räägib oma presidendikandidatuurist.'
    )
  })

  it("should print a topic's start in episode time", () => {
    expect(episodeParts(recording())[1]!.time).toBe('30:03')
  })

  it('should count a topic resumed after a restored cut once', () => {
    const entry = (start: number, end: number, title: string, cut = false) => ({
      start,
      end,
      title,
      cut,
      label: null,
      summary: null,
    })
    const topics = [
      entry(0, 100, 'Ooper'),
      entry(100, 160, 'Reklaamipaus', true),
      entry(160, 300, 'Ooper'),
      entry(300, 400, 'Valimised'),
    ]
    expect(episodeParts(recording({ topics })).map(part => [part.name, part.label])).toEqual([
      ['Ooper', 'Teema 1: Ooper · 0:00'],
      [null, 'Reklaamipaus · 1:40'],
      [null, 'Teema 1: Ooper · 2:40'],
      ['Valimised', 'Teema 2: Valimised · 5:00'],
    ])
  })

  it('should not number a part no topic covers', () => {
    const topics = [
      { start: 0, end: 120, title: null, cut: false, label: null, summary: null },
      { start: 120, end: 300, title: 'Šokolaadi hind', cut: false, label: null, summary: null },
    ]
    expect(episodeParts(recording({ topics })).map(part => [part.name, part.label])).toEqual([
      [null, 'Keskpäevatund · 0:00'],
      ['Šokolaadi hind', 'Teema 1: Šokolaadi hind · 2:00'],
    ])
  })

  it('should name a topic by its title when it has no label', () => {
    const topic = {
      start: 0,
      end: 60,
      title: 'Tesla uus roadster',
      cut: false,
      label: null,
      summary: null,
    }
    expect(episodeParts(recording({ topics: [topic] }))[0]!.name).toBe('Tesla uus roadster')
  })
})
