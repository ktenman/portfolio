import { describe, expect, it } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import RadioDayBar from './radio-day-bar.vue'
import { radioDay } from './radio-fixture'
import { Station } from '../../models/generated/domain-models'

const bar = (overrides = {}, station: Station | null = null) =>
  mount(RadioDayBar, {
    props: { radioDay: radioDay(overrides), station },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })

const DAY = radioDay()
const FINISHED = DAY.recorded[0]
const RUNNING = {
  name: '2026-10-17-1310-huvitaja',
  programme: 'Huvitaja',
  start: DAY.now - 600,
  end: DAY.now,
  recording: true,
  station: Station.KUKU,
}

const hours = (overrides = {}) =>
  bar(overrides)
    .findAll('[data-testid="radio-day-hour"]')
    .map(hour => hour.text())

describe('RadioDayBar', () => {
  it('should place a recorded span by the time it aired since five in the morning', () => {
    const span = bar().find('[data-testid="radio-day-span"]').attributes('style')
    expect(span).toContain(`left: ${((11 * 60 + 55 - 300) / 1440) * 100}%`)
  })

  it('should link a recorded span to its recording', () => {
    expect(bar().findComponent(RouterLinkStub).props('to')).toBe('/radio/2026-10-17')
  })

  it('should size the link of a recording to the time it recorded', () => {
    const link = bar({ recorded: [{ ...FINISHED, end: FINISHED.start + 3600 }] }).find(
      '[data-testid="radio-day-link"]'
    )
    expect(link.attributes('style')).toContain(`width: ${(1 / 24) * 100}%`)
  })

  it('should label a recorded span with its start and show', () => {
    const text = bar().find('[data-testid="radio-day-link"]').text()
    expect([text.includes('11:55'), text.includes('Keskpäevatund')]).toEqual([true, true])
  })

  it('should underline the name of a recording while it has keyboard focus', () => {
    const name = bar()
      .findAll('[data-testid="radio-day-link"] span')
      .find(span => span.text() === 'Keskpäevatund')!
    expect(name.classes('group-focus-visible:underline')).toBe(true)
  })

  it('should name the show and its times to a screen reader', () => {
    const link = bar().find('[data-testid="radio-day-link"]')
    expect(link.attributes('aria-label')).toBe('Keskpäevatund, 11:55–13:05 · Kuku')
  })

  it('should name the station of a show to a screen reader', () => {
    const recorded = [{ ...FINISHED, station: Station.ARIPAEV }]
    const link = bar({ recorded }).find('[data-testid="radio-day-link"]')
    expect(link.attributes('aria-label')).toBe('Keskpäevatund, 11:55–13:05 · Äripäev')
  })

  it('should tell a screen reader that a show is being recorded now', () => {
    const links = bar({ recorded: [FINISHED, RUNNING] }).findAll('[data-testid="radio-day-link"]')
    expect(links[1].attributes('aria-label')).toBe('Huvitaja, 13:10–, recording now · Kuku')
  })

  it('should title a planned airing with its times', () => {
    const plan = bar().find('[data-testid="radio-day-planned"]')
    expect(plan.attributes('title')).toBe('Tarkade klubi · 03:00–04:00 · planned')
  })

  it('should outline a planned airing without filling it', () => {
    const block = bar().find('[data-testid="radio-day-planned-span"]')
    expect([block.classes('border'), block.classes('bg-ink')]).toEqual([true, false])
  })

  it('should leave a planned airing to the schedule for a screen reader', () => {
    const plan = bar().find('[data-testid="radio-day-planned"]')
    expect(plan.attributes('aria-hidden')).toBe('true')
  })

  it('should not link a planned airing', () => {
    const day = bar({ recorded: [] })
    expect([
      day.findAll('[data-testid="radio-day-planned"]').length,
      day.findAllComponents(RouterLinkStub).length,
    ]).toEqual([2, 0])
  })

  it('should count the recordings and their time', () => {
    expect(bar().text()).toContain('3 recordings · 2 h 30 min')
  })

  it('should count the planned airings and their time on a day with nothing recorded', () => {
    const planned = [
      { programme: 'Sihik', start: DAY.from + 7 * 3600, end: DAY.from + 8 * 3600 },
      { programme: 'Indrek Vaheoja', start: DAY.from + 10 * 3600, end: DAY.from + 13 * 3600 },
    ]
    const day = bar({ recorded: [], seconds: 0, planned, plannedSeconds: 14400 })
    expect(day.find('[data-testid="radio-day-summary"]').text()).toBe('2 planned · 4 h 0 min')
  })

  it('should leave the corner empty on a day with nothing recorded or planned', () => {
    const day = bar({ recorded: [], seconds: 0, planned: [], plannedSeconds: 0 })
    expect(day.find('[data-testid="radio-day-summary"]').text()).toBe('')
  })

  it('should show the current time on today', () => {
    expect(bar().find('[data-testid="radio-day-clock"]').text()).toBe('Now 13:20')
  })

  it('should not show the current time on another day', () => {
    expect(
      bar({ now: DAY.until + 60 })
        .find('[data-testid="radio-day-now"]')
        .exists()
    ).toBe(false)
  })

  it('should draw finished recordings in ink and stripe the one still being recorded', () => {
    const spans = bar({ recorded: [FINISHED, RUNNING] }).findAll('[data-testid="radio-day-span"]')
    expect(spans.map(span => span.classes('bg-ink'))).toEqual([true, false])
  })

  it('should place the noon tick seven hours into the broadcast day', () => {
    const ticks = bar().findAll('[data-testid="radio-day-tick"]')
    expect(ticks[1].attributes('style')).toContain(`left: ${(7 / 24) * 100}%`)
  })

  it('should mark midnight stronger than the other ticks', () => {
    const ticks = bar({}, Station.KUKU).findAll('[data-testid="radio-day-tick"]')
    expect(ticks.map(tick => tick.classes('bg-ink-faint'))).toEqual([false, false, false, true])
  })

  it('should label the hours of the broadcast day', () => {
    expect(hours({ now: DAY.until + 60 })).toEqual(['06', '12', '18', '00'])
  })

  it('should hide the hour label next to the current time', () => {
    expect(hours({ now: DAY.now + 4 * 3600 + 600 })).toEqual(['06', '12', '00'])
  })

  it('should keep the current time inside the card early in the morning', () => {
    const clock = bar({ now: DAY.from + 600 }).find('[data-testid="radio-day-clock"]')
    expect(clock.classes('translate-x-0')).toBe(true)
  })

  it('should keep the current time inside the card just before five in the morning', () => {
    const clock = bar({ now: DAY.until - 600 }).find('[data-testid="radio-day-clock"]')
    expect(clock.classes('-translate-x-full')).toBe(true)
  })

  const tracks = (station?: Station | null, overrides = {}) =>
    bar(overrides, station).findAll('[data-testid="radio-day-track"]')

  it('should draw one track per station on All', () => {
    expect(tracks().map(track => track.get('[data-testid="radio-day-station"]').text())).toEqual([
      'Kuku',
      'Äripäev',
    ])
  })

  it('should draw one track on a chip', () => {
    expect(
      tracks(Station.ARIPAEV).map(track => track.get('[data-testid="radio-day-station"]').text())
    ).toEqual(['Äripäev'])
  })

  it('should keep the name of a station beside its track', () => {
    expect(tracks()[0].classes()).toContain('grid-cols-[72px_minmax(0,1fr)]')
  })

  it('should put each recording on the track of its station', () => {
    const recorded = [FINISHED, { ...RUNNING, station: Station.ARIPAEV }]
    const links = tracks(null, { recorded }).map(
      track => track.findAll('[data-testid="radio-day-link"]').length
    )
    expect(links).toEqual([1, 1])
  })

  it('should put each planned airing on the track of its station', () => {
    const planned = [{ ...DAY.planned[0], station: Station.ARIPAEV }]
    const plans = tracks(null, { planned }).map(
      track => track.findAll('[data-testid="radio-day-planned"]').length
    )
    expect(plans).toEqual([0, 1])
  })

  it('should share one row of hours between the tracks', () => {
    expect(hours({ now: DAY.until + 60 })).toEqual(['06', '12', '18', '00'])
  })

  it('should line the hours up with the tracks', () => {
    const row = bar().get('[data-testid="radio-day-hours"]')
    expect(row.classes()).toContain('grid-cols-[72px_minmax(0,1fr)]')
  })

  const room = (recorded: (typeof FINISHED)[], planned: typeof DAY.planned, at: number) => {
    const { room, tracks } = bar({ recorded, planned }).vm as unknown as {
      room: (span: { start: number; end: number }, starts: number[]) => string
      tracks: { station: Station; starts: number[] }[]
    }
    return tracks.map(track => room({ start: at, end: at + 600 }, track.starts))
  }

  it('should not stop a label at a start on the other track', () => {
    const later = { ...FINISHED, start: FINISHED.start + 900, station: Station.ARIPAEV }
    const whole = `min(${((DAY.until - FINISHED.start) / (DAY.until - DAY.from)) * 100}cqw`
    expect(room([FINISHED, later], [], FINISHED.start)[0]).toContain(whole)
  })

  it('should stop a label at the next start on its own track', () => {
    const later = { ...FINISHED, start: FINISHED.start + 900 }
    const short = `min(${(900 / (DAY.until - DAY.from)) * 100}cqw`
    expect(room([FINISHED, later], [], FINISHED.start)[0]).toContain(short)
  })

  it('should stop a label at a planned start on its own track', () => {
    const planned = [{ ...DAY.planned[0], start: FINISHED.start + 900 }]
    const short = `min(${(900 / (DAY.until - DAY.from)) * 100}cqw`
    expect(room([FINISHED], planned, FINISHED.start)[0]).toContain(short)
  })
})
