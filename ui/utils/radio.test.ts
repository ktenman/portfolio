import { describe, expect, it } from 'vitest'
import { ProgrammeCutState, type KeptSpan } from '../models/generated/domain-models'
import {
  audioFormat,
  clock,
  day,
  dropNote,
  fullDay,
  left,
  length,
  megabytes,
  minutes,
  offset,
  poll,
  position,
  recordingTime,
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
})

describe('stream', () => {
  const dropped = (drops: number, lostSeconds: number) =>
    recording({ drops, status: { ...recording().status, lostSeconds } })

  it('should give the format of the recording', () => {
    expect(audioFormat(recording().format)).toEqual('MP3 256 kb/s')
  })

  it('should say nothing was lost when the stream dropped once', () => {
    expect(dropNote(dropped(1, 0))).toEqual('Voog katkes korra, midagi ei kadunud')
  })

  it('should give the length lost when the stream dropped', () => {
    expect(dropNote(dropped(20, 4))).toEqual('Voog katkes 20 korda, 4 s jäi salvestamata')
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

  it('should ask every second while a cut search runs', () => {
    expect(poll(recordings(ProgrammeCutState.QUEUED, ProgrammeCutState.FINDING))).toBe(1000)
  })

  it('should ask every two seconds while a cut search waits its turn', () => {
    expect(poll(recordings(ProgrammeCutState.WAITING, ProgrammeCutState.QUEUED))).toBe(2000)
  })

  it('should ask every half minute while a cut search waits for the text', () => {
    expect(poll(recordings(ProgrammeCutState.WAITING))).toBe(30_000)
  })

  it('should stop asking once no cut search is coming', () => {
    expect(poll(recordings(ProgrammeCutState.FAILED, ProgrammeCutState.NONE))).toBe(false)
  })
})
