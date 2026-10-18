import { describe, expect, it } from 'vitest'
import type { KeptSpan } from '../models/generated/domain-models'
import { clock, day, fullDay, length, megabytes, minutes, position, recordingTime } from './radio'

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
