import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioDayBar from './radio-day-bar.vue'
import { radioDay } from './radio-fixture'

const bar = (overrides = {}) => mount(RadioDayBar, { props: { radioDay: radioDay(overrides) } })

const DAY = radioDay()
const FINISHED = DAY.recorded[0]
const RUNNING = {
  name: '2026-10-17-1310-huvitaja',
  programme: 'Huvitaja',
  start: DAY.now - 600,
  end: DAY.now,
  recording: true,
}

const legend = (overrides = {}) =>
  bar(overrides)
    .findAll('[data-testid="radio-day-legend"] > span')
    .map(item => item.text())

describe('RadioDayBar', () => {
  it('should place a recorded span by the hour of the day it began', () => {
    const span = bar().find('[data-testid="radio-day-span"]').attributes('style')
    expect(span).toContain(`left: ${((11 * 60 + 55) / 1440) * 100}%`)
  })

  it('should list the recorded spans of the day', () => {
    expect(bar().text()).toContain('Recorded 11:55–13:05')
  })

  it('should give the recorded time of the day', () => {
    expect(bar().text()).toContain('1 h 10 min of 24 h')
  })

  it('should show the current time on today', () => {
    expect(bar().text()).toContain('Now 13:20')
  })

  it('should not show the current time on another day', () => {
    const day = radioDay()
    expect(
      bar({ now: day.until + 60 })
        .find('[data-testid="radio-day-now"]')
        .exists()
    ).toBe(false)
  })

  it('should say nothing was recorded on an empty day', () => {
    expect(bar({ recorded: [], seconds: 0 }).text()).toContain('Nothing recorded')
  })

  it('should name the recording in progress apart from the finished ones', () => {
    expect(legend({ recorded: [FINISHED, RUNNING] })).toEqual([
      'Recorded 11:55–13:05',
      'Recording 13:10–',
      'Now 13:20',
    ])
  })

  it('should name only the recording in progress on a day without finished ones', () => {
    expect(legend({ recorded: [RUNNING] })).toEqual(['Recording 13:10–', 'Now 13:20'])
  })

  it('should stripe only the span still being recorded', () => {
    const spans = bar({ recorded: [FINISHED, RUNNING] }).findAll('[data-testid="radio-day-span"]')
    expect(spans.map(span => span.classes('bg-umber'))).toEqual([true, false])
  })

  it('should describe both the finished and the running recording to a screen reader', () => {
    const label = bar({ recorded: [FINISHED, RUNNING] })
      .find('[role="img"]')
      .attributes('aria-label')
    expect(label).toBe('Recorded 11:55–13:05, Recording 13:10–')
  })

  it('should mark six, twelve and eighteen inside the bar', () => {
    const guides = bar().findAll('[data-testid="radio-day-guide"]')
    expect(guides.map(guide => guide.attributes('style'))).toEqual([
      'left: 25%;',
      'left: 50%;',
      'left: 75%;',
    ])
  })
})
