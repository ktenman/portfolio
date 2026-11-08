import { afterEach, describe, expect, it } from 'vitest'
import { enableAutoUnmount, mount, type VueWrapper } from '@vue/test-utils'
import type { ChartFrame } from '../../services/chart-frame'
import type { ChartPeriod } from '../../services/chart-period'
import ChartPeriodLayer from './chart-period-layer.vue'

enableAutoUnmount(afterEach)

const TODAY = '2026-11-08'
const SUMMER: ChartPeriod = { from: '2026-06-24', to: '2026-09-10' }

const FRAME: ChartFrame = {
  width: 460,
  left: 40,
  right: 405,
  top: 20,
  bottom: 220,
  axisY: 254.6,
  legendY: 276,
  legendLeft: 50,
  legendRight: 196,
  xs: [40, 405],
  dates: ['2025-11-08', TODAY],
}

const WIDE: ChartFrame = { ...FRAME, width: 1500, right: 1500, xs: [40, 1500] }

const PHONE: ChartFrame = { ...FRAME, width: 342, right: 300, legendRight: 300, xs: [40, 300] }

type LayerProps = InstanceType<typeof ChartPeriodLayer>['$props']

const mountLayer = (props: Partial<LayerProps> = {}) =>
  mount(ChartPeriodLayer, {
    props: {
      frame: FRAME,
      period: null,
      drag: null,
      ring: null,
      today: TODAY,
      backTo: null,
      compact: false,
      ...props,
    },
    attachTo: document.body,
  })

const mountZoomable = () => {
  const wrapper: VueWrapper = mountLayer({
    period: SUMMER,
    onZoom: () => wrapper.setProps({ period: null, backTo: '1Y' }),
  } as Partial<LayerProps>)
  return wrapper
}

const lastLegend = (wrapper: VueWrapper) => {
  const calls = wrapper.emitted('legend') ?? []
  return calls[calls.length - 1]
}

const textOf = (wrapper: VueWrapper) => wrapper.text().replace(/\s+/g, ' ')

describe('ChartPeriodLayer on a wide screen', () => {
  it('should invite a drag while no period is drawn', () => {
    expect(textOf(mountLayer())).toContain('Drag across the chart to pick a period')
  })

  it('should tell how to finish while a period is being drawn', () => {
    const wrapper = mountLayer({ period: SUMMER, drag: 'new' })

    expect(textOf(wrapper)).toContain('Release, then drag the edges to fine-tune')
  })

  it('should count the days of a drawn period on its chip', () => {
    const wrapper = mountLayer({ period: SUMMER })

    expect(wrapper.get('[data-testid="period-days"]').text()).toBe('78 days')
  })

  it('should carry the clear button on the chip of a drawn period', () => {
    const chip = mountLayer({ period: SUMMER }).get('[data-testid="period-days"]')

    expect(chip.find('[data-testid="period-clear"]').exists()).toBe(true)
  })

  it('should name the clear button by what it clears', () => {
    const wrapper = mountLayer({ period: SUMMER })

    expect(wrapper.get('[data-testid="period-clear"]').attributes('aria-label')).toBe(
      'Clear period'
    )
  })

  it('should offer to zoom in on the drawn period', () => {
    const wrapper = mountLayer({ period: SUMMER })

    expect(wrapper.get('[data-testid="period-zoom"]').text()).toBe('Zoom in')
  })

  it('should offer the way back from a zoom', () => {
    const wrapper = mountLayer({ backTo: '1Y' })

    expect(wrapper.get('[data-testid="period-back"]').text()).toBe('Back to 1Y')
  })

  it('should not invite a drag while zoomed', () => {
    expect(textOf(mountLayer({ backTo: '1Y' }))).not.toContain('Drag across')
  })

  it('should date both edges under the plot', () => {
    const chips = mountLayer({ period: SUMMER }).findAll('[data-testid="period-chip"]')

    expect(chips.map(chip => chip.text())).toEqual(['24 Jun', '10 Sep'])
  })

  it('should add the year to an edge outside the current year', () => {
    const wrapper = mountLayer({ period: { from: '2025-12-01', to: '2026-01-10' } })

    expect(wrapper.find('[data-testid="period-chip"]').text()).toBe('1 Dec 2025')
  })

  it('should split chips whose edges are closer than 96 px', () => {
    const chips = mountLayer({ period: SUMMER }).findAll('[data-testid="period-chip"]')

    expect(chips.map(chip => chip.attributes('data-side'))).toEqual(['left', 'right'])
  })

  it('should centre chips on edges that are far apart', () => {
    const chips = mountLayer({ frame: WIDE, period: SUMMER }).findAll('[data-testid="period-chip"]')

    expect(chips.map(chip => chip.attributes('data-side'))).toEqual(['centre', 'centre'])
  })

  it('should hold back the grips while a period is being drawn', () => {
    const wrapper = mountLayer({ period: SUMMER, drag: 'new' })

    expect(wrapper.findAll('[role="slider"]')).toHaveLength(0)
  })

  it('should name the grips as the start and the end of the period', () => {
    const grips = mountLayer({ period: SUMMER }).findAll('[role="slider"]')

    expect(grips.map(grip => grip.attributes('aria-label'))).toEqual([
      'Start of period',
      'End of period',
    ])
  })

  it('should read each grip out as its date', () => {
    const grip = mountLayer({ period: SUMMER }).get('[role="slider"]')

    expect(grip.attributes('aria-valuetext')).toBe('24 June 2026')
  })

  it('should keep the start grip at least two days before the end', () => {
    const grip = mountLayer({ period: SUMMER }).get('[role="slider"]')

    expect(grip.attributes('aria-valuemax')).toBe('304')
  })

  it('should move the start a day later on the right arrow', async () => {
    const wrapper = mountLayer({ period: SUMMER })
    await wrapper.get('[role="slider"]').trigger('keydown', { key: 'ArrowRight' })

    expect(wrapper.emitted('update:period')?.[0]).toEqual([
      { from: '2026-06-25', to: '2026-09-10' },
    ])
  })

  it('should move the end 30 days on page down', async () => {
    const wrapper = mountLayer({ period: SUMMER })
    await wrapper.findAll('[role="slider"]')[1].trigger('keydown', { key: 'PageDown' })

    expect(wrapper.emitted('update:period')?.[0]).toEqual([
      { from: '2026-06-24', to: '2026-08-11' },
    ])
  })

  it('should zoom on enter from a grip', async () => {
    const wrapper = mountLayer({ period: SUMMER })
    await wrapper.get('[role="slider"]').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('zoom')).toHaveLength(1)
  })

  it('should move focus to the way back after zooming from a grip', async () => {
    const wrapper = mountZoomable()
    await wrapper.get('[role="slider"]').trigger('keydown', { key: 'Enter' })

    expect(document.activeElement).toBe(wrapper.get('[data-testid="period-back"]').element)
  })

  it('should move focus to the way back after zooming from the keyboard', async () => {
    const wrapper = mountZoomable()
    await wrapper.get('[data-testid="period-zoom"]').trigger('click', { detail: 0 })

    expect(document.activeElement).toBe(wrapper.get('[data-testid="period-back"]').element)
  })

  it('should clear the period', async () => {
    const wrapper = mountLayer({ period: SUMMER })
    await wrapper.get('[data-testid="period-clear"]').trigger('click')

    expect(wrapper.emitted('update:period')?.[0]).toEqual([null])
  })

  it('should zoom from its button', async () => {
    const wrapper = mountLayer({ period: SUMMER })
    await wrapper.get('[data-testid="period-zoom"]').trigger('click')

    expect(wrapper.emitted('zoom')).toHaveLength(1)
  })

  it('should go back from its button', async () => {
    const wrapper = mountLayer({ backTo: '1Y' })
    await wrapper.get('[data-testid="period-back"]').trigger('click')

    expect(wrapper.emitted('back')).toHaveLength(1)
  })

  it('should keep the legend while the actions fit beside it', () => {
    expect(lastLegend(mountLayer({ period: SUMMER }))).toEqual([false])
  })

  it('should line the legend-row actions up with the right edge of the plot', () => {
    expect(mountLayer({ period: SUMMER }).get('.aside').attributes('style')).toContain(
      'right: 55px'
    )
  })
})

describe('ChartPeriodLayer on a phone', () => {
  it('should invite a hold while no period is drawn', () => {
    expect(textOf(mountLayer({ compact: true }))).toContain('Hold, then slide')
  })

  it('should ask for a slide while the held period is shorter than two days', () => {
    const wrapper = mountLayer({
      compact: true,
      period: { from: '2026-06-24', to: '2026-06-25' },
      drag: 'new',
    })

    expect(textOf(wrapper)).toContain('Slide to the other end of the period')
  })

  it('should ask for a lift once the held period is long enough', () => {
    const wrapper = mountLayer({ compact: true, period: SUMMER, drag: 'new' })

    expect(textOf(wrapper)).toContain('Lift, then drag the edges')
  })

  it('should start the drawn-period actions where the legend starts', () => {
    const wrapper = mountLayer({ compact: true, period: SUMMER })

    expect(wrapper.get('.aside').attributes('style')).toContain('left: 50px')
  })

  it('should let the legend give way to a drawn period', () => {
    const wrapper = mountLayer({ compact: true, period: SUMMER })

    expect(lastLegend(wrapper)).toEqual([true])
  })

  it('should hold the legend-row actions 8 px inside the right edge of the chart', () => {
    const wrapper = mountLayer({ compact: true, period: SUMMER })

    expect(wrapper.get('.aside').attributes('style')).toContain('right: 8px')
  })

  it('should keep the hold hint where it fits short of the right edge of the chart', () => {
    expect(mountLayer({ compact: true, frame: PHONE }).get('.aside').classes()).not.toContain(
      'unfit'
    )
  })

  it('should keep the legend beside the way back from a zoom', () => {
    expect(lastLegend(mountLayer({ compact: true, frame: PHONE, backTo: '1Y' }))).toEqual([false])
  })

  it('should show the hold ring where the finger presses', () => {
    const wrapper = mountLayer({ compact: true, ring: { x: 120, y: 80 } })

    expect(wrapper.get('[data-testid="period-ring"]').attributes('style')).toContain('left: 120px')
  })
})
