import { describe, expect, it } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import RadioDayBar from './radio-day-bar.vue'
import { radioDay } from './radio-fixture'

const bar = (overrides = {}) =>
  mount(RadioDayBar, {
    props: { radioDay: radioDay(overrides) },
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
    expect(link.attributes('aria-label')).toBe('Keskpäevatund, 11:55–13:05')
  })

  it('should tell a screen reader that a show is being recorded now', () => {
    const links = bar({ recorded: [FINISHED, RUNNING] }).findAll('[data-testid="radio-day-link"]')
    expect(links[1].attributes('aria-label')).toBe('Huvitaja, 13:10–, recording now')
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
    ]).toEqual([1, 0])
  })

  it('should count the recordings and their time', () => {
    expect(bar().text()).toContain('1 recording · 1 h 10 min')
  })

  it('should say nothing was recorded on an empty day', () => {
    expect(bar({ recorded: [], seconds: 0 }).text()).toContain('Nothing recorded')
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
    const ticks = bar().findAll('[data-testid="radio-day-tick"]')
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
})
