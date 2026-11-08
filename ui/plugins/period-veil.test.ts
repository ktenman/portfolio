import { describe, expect, it } from 'vitest'
import type { ChartFrame } from '../services/chart-frame'
import { fakeChart } from '../tests/fixtures'
import { createPeriodVeil, type PeriodDrawing } from './period-veil'

interface Recording {
  rects: number[][]
  arcs: number[][]
  lines: number[][]
  cleared: number
}

const DATES = ['2026-01-01', '2026-01-11', '2026-01-31']
const PERIOD = { from: '2026-01-06', to: '2026-01-21' }
const LINE_Y = 120

const record = (): Recording => ({ rects: [], arcs: [], lines: [], cleared: 0 })

const fakeContext = (recording: Recording) => ({
  fillStyle: '',
  strokeStyle: '',
  lineWidth: 1,
  save: () => undefined,
  restore: () => undefined,
  beginPath: () => undefined,
  fill: () => undefined,
  stroke: () => undefined,
  lineTo: () => undefined,
  moveTo: (x: number, y: number) => {
    recording.lines.push([x, y])
  },
  fillRect: (x: number, y: number, width: number, height: number) => {
    recording.rects.push([x, y, width, height])
  },
  arc: (x: number, y: number, radius: number) => {
    recording.arcs.push([x, y, radius])
  },
})

const recordingChart = (recording: Recording) =>
  fakeChart({
    ctx: fakeContext(recording),
    data: { datasets: [{}, {}] },
    isDatasetVisible: (index: number) => index === 1,
    getDatasetMeta: (index: number) => ({
      dataset: { interpolate: ({ x }: { x: number }) => ({ x, y: LINE_Y + index }) },
    }),
    setActiveElements: () => {
      recording.cleared += 1
    },
    tooltip: { setActiveElements: () => undefined },
  })

const veil = (drawing: Partial<PeriodDrawing>, frames: ChartFrame[] = []) =>
  createPeriodVeil({
    drawing: () => ({ period: PERIOD, dragging: false, inside: false, ...drawing }),
    dates: () => DATES,
    onFrame: frame => frames.push(frame),
  })

const draw = (drawing: Partial<PeriodDrawing>): Recording => {
  const recording = record()
  const chart = recordingChart(recording)
  veil(drawing).afterDatasetsDraw?.(chart, {}, {}, false)
  return recording
}

describe('createPeriodVeil', () => {
  it('should veil the plot on both sides of the period', () => {
    expect(draw({}).rects).toEqual([
      [40, 20, 80, 200],
      [280, 20, 80, 200],
    ])
  })

  it('should draw nothing without a period', () => {
    expect(draw({ period: null })).toEqual(record())
  })

  it('should tint the inside of the period while the pointer is over it', () => {
    expect(draw({ inside: true }).rects).toContainEqual([120, 20, 160, 200])
  })

  it('should draw each edge as a line down the plot', () => {
    expect(draw({}).lines).toEqual([
      [120.5, 20],
      [280.5, 20],
    ])
  })

  it('should ring a dot where each edge meets the first visible line', () => {
    expect(draw({}).arcs).toEqual([
      [120, LINE_Y + 1, 3.5],
      [280, LINE_Y + 1, 3.5],
    ])
  })

  it('should discard chart events while a period is being dragged', () => {
    const chart = recordingChart(record())

    expect(veil({ dragging: true }).beforeEvent?.(chart, {} as never, {})).toBe(false)
  })

  it('should let chart events through while nothing is dragged', () => {
    const chart = recordingChart(record())

    expect(veil({}).beforeEvent?.(chart, {} as never, {})).toBeUndefined()
  })

  it('should drop the hovered points once a drag starts', () => {
    const recording = record()
    veil({ dragging: true }).beforeDraw?.(recordingChart(recording), { cancelable: true }, {})

    expect(recording.cleared).toBe(1)
  })

  it('should report the frame only when it changes', () => {
    const frames: ChartFrame[] = []
    const plugin = veil({}, frames)
    const chart = recordingChart(record())
    plugin.afterRender?.(chart, {}, {})
    plugin.afterRender?.(chart, {}, {})

    expect(frames).toHaveLength(1)
  })
})
