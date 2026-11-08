import { describe, expect, it } from 'vitest'
import { dateAtX, frameBounds, readFrame, xOfDate, type ChartFrame } from './chart-frame'
import { fakeChart } from '../tests/fixtures'

const DATES = ['2026-01-01', '2026-01-11', '2026-01-31']

const FRAME: ChartFrame = {
  width: 400,
  left: 40,
  right: 360,
  top: 20,
  bottom: 220,
  axisY: 254.6,
  legendY: 276,
  legendLeft: 50,
  legendRight: 196,
  xs: [40, 200, 360],
  dates: DATES,
}

describe('frameBounds', () => {
  it('should bound a frame by its first and last date', () => {
    expect(frameBounds(FRAME)).toEqual({ from: '2026-01-01', to: '2026-01-31' })
  })
})

describe('dateAtX', () => {
  it('should read the date of the sample under the pointer', () => {
    expect(dateAtX(FRAME, 200)).toBe('2026-01-11')
  })

  it('should count the days between two samples in proportion to the distance', () => {
    expect(dateAtX(FRAME, 120)).toBe('2026-01-06')
  })

  it('should hold the first date left of the plot', () => {
    expect(dateAtX(FRAME, 0)).toBe('2026-01-01')
  })

  it('should hold the last date right of the plot', () => {
    expect(dateAtX(FRAME, 999)).toBe('2026-01-31')
  })
})

describe('xOfDate', () => {
  it('should place a day between two samples in proportion to its distance', () => {
    expect(xOfDate(FRAME, '2026-01-21')).toBe(280)
  })

  it('should place a day before the chart on its left edge', () => {
    expect(xOfDate(FRAME, '2025-12-01')).toBe(40)
  })

  it('should place a day after the chart on its right edge', () => {
    expect(xOfDate(FRAME, '2026-03-01')).toBe(360)
  })
})

describe('readFrame', () => {
  it('should read the plot, the axis labels and the legend row', () => {
    expect(readFrame(fakeChart(), DATES)).toEqual(FRAME)
  })

  it('should read the legend row from its first line when the legend wraps', () => {
    const legend = {
      legendHitBoxes: [
        { left: 50, top: 270, width: 60, height: 12 },
        { left: 126, top: 270, width: 200, height: 12 },
        { left: 50, top: 292, width: 80, height: 12 },
      ],
    }

    expect(readFrame(fakeChart({ legend }), DATES)?.legendRight).toBe(326)
  })

  it('should read no frame before the chart has a plot area', () => {
    expect(readFrame(fakeChart({ chartArea: undefined }), DATES)).toBeNull()
  })
})
