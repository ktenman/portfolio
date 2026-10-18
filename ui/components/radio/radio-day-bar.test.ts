import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioDayBar from './radio-day-bar.vue'
import { radioDay } from './radio-fixture'

const bar = (overrides = {}) => mount(RadioDayBar, { props: { radioDay: radioDay(overrides) } })

describe('RadioDayBar', () => {
  it('should place a recorded span by the hour of the day it began', () => {
    const span = bar().find('[data-testid="radio-day-span"]').attributes('style')
    expect(span).toContain(`left: ${((11 * 60 + 55) / 1440) * 100}%`)
  })

  it('should list the recorded spans of the day', () => {
    expect(bar().text()).toContain('Salvestatud 11:55–13:05')
  })

  it('should give the recorded time of the day', () => {
    expect(bar().text()).toContain('1 h 10 min ööpäevast')
  })

  it('should show the current time on today', () => {
    expect(bar().text()).toContain('Praegu 13:20')
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
    expect(bar({ recorded: [], seconds: 0 }).text()).toContain('Midagi pole salvestatud')
  })
})
