import { describe, expect, it } from 'vitest'
import type { Boundary, EditList, KeptSpan } from '../models/generated/domain-models'
import {
  clock,
  cuts,
  day,
  fullDay,
  keepWithout,
  length,
  megabytes,
  method,
  minutes,
  position,
  recordingTime,
  removed,
  segments,
} from './radio'

const STARTED = Date.UTC(2026, 9, 17, 8, 55, 0) / 1000
const TOTAL = 4200

const boundary = (name: string, seconds: number, method = 'digital silence'): Boundary => ({
  boundary: name,
  seconds,
  method,
  review: method === 'transcript only',
})

const MACHINE: KeptSpan[] = [
  { start: 761.5, end: 2120 },
  { start: 2255, end: 3025 },
  { start: 3137, end: 3837 },
]

const edit = (keep: KeptSpan[] = MACHINE): EditList => ({
  keep,
  durationSeconds: 2512.5,
  parts: [{ file: 'raw.mp3', offsetSeconds: 0, sha256: null }],
  boundaries: [
    boundary('show starts', 761.5),
    boundary('break 1 starts', 2120, 'level dip'),
    boundary('break 1 ends', 2255, 'station jingle, r=0.93'),
    boundary('break 2 starts', 3025),
    boundary('break 2 ends', 3137, 'transcript only'),
    boundary('show ends', 3837),
  ],
  hosts: [],
  outline: { model: null, reads: 3, matched: 3, voters: 3 },
  chapters: [],
  reviewed: false,
  renderedAt: '2026-10-17T10:20:00Z',
  topics: [
    { start: 760, title: 'Rahvusooperi tüli' },
    { start: 2700, title: 'Mis toimub poliitikas?' },
    { start: 3400, title: 'President Ülle Madise' },
  ],
})

describe('cuts', () => {
  it('should find the cut before the show, each ad break and the cut after the show', () => {
    expect(cuts(edit(), TOTAL).map(cut => [cut.kind, cut.start, cut.end])).toEqual([
      ['before', 0, 761.5],
      ['break', 2120, 2255],
      ['break', 3025, 3137],
      ['after', 3837, 4200],
    ])
  })

  it('should give the method that placed each boundary of a cut', () => {
    expect(cuts(edit(), TOTAL)[1].methods).toEqual(['level dip', 'station jingle, r=0.93'])
  })

  it('should flag a cut with a boundary placed by the transcript alone for review', () => {
    expect(cuts(edit(), TOTAL).map(cut => cut.review)).toEqual([false, false, true, false])
  })

  it('should find no cuts in a recording without an edit list', () => {
    expect(cuts(null, TOTAL)).toEqual([])
  })

  it('should drop a cut of no length', () => {
    const list = edit()
    list.boundaries[5] = boundary('show ends', TOTAL)
    expect(cuts(list, TOTAL).map(cut => cut.kind)).toEqual(['before', 'break', 'break'])
  })
})

describe('removed', () => {
  it('should mark every machine suggestion removed under the machine keep list', () => {
    expect(cuts(edit(), TOTAL).map(cut => removed(cut, MACHINE))).toEqual([true, true, true, true])
  })

  it('should mark a break kept when a reviewed keep list covers it', () => {
    const keep = [
      { start: 761.5, end: 3025 },
      { start: 3137, end: 3837 },
    ]
    expect(cuts(edit(keep), TOTAL).map(cut => removed(cut, keep))).toEqual([
      true,
      false,
      true,
      true,
    ])
  })
})

describe('keepWithout', () => {
  it('should give back the machine keep list when every suggestion is removed', () => {
    expect(keepWithout(cuts(edit(), TOTAL), TOTAL)).toEqual(MACHINE)
  })

  it('should merge the neighbours of a kept break into one span', () => {
    const [before, , second, after] = cuts(edit(), TOTAL)
    expect(keepWithout([before, second, after], TOTAL)).toEqual([
      { start: 761.5, end: 3025 },
      { start: 3137, end: 3837 },
    ])
  })

  it('should keep the whole recording when nothing is removed', () => {
    expect(keepWithout([], TOTAL)).toEqual([{ start: 0, end: TOTAL }])
  })
})

describe('segments', () => {
  it('should list the cuts and the topics in recording order', () => {
    expect(
      segments(edit(), TOTAL).map(segment => [segment.start, segment.title, segment.cut])
    ).toEqual([
      [0, 'Enne saadet', true],
      [761.5, 'Rahvusooperi tüli', false],
      [2120, 'Reklaamipaus', true],
      [2255, 'Rahvusooperi tüli', false],
      [2700, 'Mis toimub poliitikas?', false],
      [3025, 'Reklaamipaus', true],
      [3137, 'Mis toimub poliitikas?', false],
      [3400, 'President Ülle Madise', false],
      [3837, 'Pärast saadet', true],
    ])
  })

  it('should show a recording without an edit list as one part', () => {
    expect(segments(null, 60)).toEqual([{ start: 0, end: 60, title: 'Keskpäevatund', cut: false }])
  })
})

describe('recordingTime', () => {
  it('should map a moment of the episode onto the recording', () => {
    expect(recordingTime(MACHINE, 1400)).toEqual(2296.5)
  })

  it('should stop at the end of the last kept span', () => {
    expect(recordingTime(MACHINE, 9999)).toEqual(3837)
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

  it('should name a boundary method in Estonian', () => {
    expect(
      ['digital silence', 'station jingle, r=0.93', 'level dip', 'transcript only'].map(method)
    ).toEqual(['digivaikus', 'jaama džingel', 'helitaseme langus', 'ainult tekst'])
  })
})
